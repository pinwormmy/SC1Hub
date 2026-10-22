#!/usr/bin/env bash
# Server half of deploy-lightsail.sh. Runs as root on the Lightsail host:
#   sudo bash remote-install.sh RELEASE_DIR COMMIT
# RELEASE_DIR holds ROOT.war, sql/pre/*.sql (idempotent, applied while the old release runs)
# and sql/stopped/*.sql (applied only while no release is up).
set -euo pipefail

RELEASE_DIR="${1:?release directory}"
COMMIT="${2:-unknown}"
APP_USER=sc1hub
APP_HOME=/home/hosting_users/sc1hub
BASE="$APP_HOME/tomcat"
SERVICE=sc1hub-tomcat
HTTP_PORT=8645
PROPS="$APP_HOME/config/application-online.properties"
WEBAPPS="$BASE/webapps"
WAR="$WEBAPPS/ROOT.war"
STAGED="$WAR.uploading"
ROLLBACK_WAR="$WAR.rollback"
EXPLODED="$WEBAPPS/ROOT"
METASPACE_LOG="$BASE/logs/metaspace-history.log"
# nginx-shaped proxy headers: a TLS request from a documentation-range client address.
PROXY_HEADERS=(-H "Host: sc1hub.com" -H "X-Forwarded-For: 192.0.2.10" -H "X-Forwarded-Proto: https")

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run with sudo"
[[ -s "$RELEASE_DIR/ROOT.war" ]] || die "missing $RELEASE_DIR/ROOT.war"
[[ -s "$PROPS" ]] || die "missing $PROPS"
systemctl cat "$SERVICE" >/dev/null 2>&1 || die "systemd unit $SERVICE is not installed (run provision.sh)"

runtime="$(sudo -u "$APP_USER" env CATALINA_HOME=/opt/tomcat/current CATALINA_BASE="$BASE" \
  /opt/tomcat/current/bin/version.sh 2>&1 || true)"
if ! grep -Eq 'Server version: Apache Tomcat/10\.1\.' <<<"$runtime" \
   || ! grep -Eq 'JVM Version: +17\.' <<<"$runtime"; then
  die "expected Tomcat 10.1.x on Java 17, found: $runtime"
fi

prop() { grep "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r'; }
DB_URL="$(prop spring.datasource.url)"
DB_USER="$(prop spring.datasource.username)"
DB_PASS="$(prop spring.datasource.password)"
DB_NAME="$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:(mysql|mariadb)://[^/]+/([^?]+).*#\2#')"
[[ -n "$DB_USER" && -n "$DB_PASS" && "$DB_NAME" =~ ^[A-Za-z0-9_]+$ ]] || die "datasource settings are missing or invalid"
run_sql() { MYSQL_PWD="$DB_PASS" mariadb -u "$DB_USER" "$DB_NAME" < "$1"; }

local_get() { curl -fsS --max-time "${2:-5}" -o /dev/null "${PROXY_HEADERS[@]}" "http://127.0.0.1:$HTTP_PORT$1"; }

wait_for_local_health() {
  for _ in $(seq 1 90); do
    local_get / 2 && return 0
    sleep 1
  done
  return 1
}

wait_for_stable_local_health() {
  for _ in $(seq 1 30); do
    sleep 1
    local_get / 2 || return 1
  done
}

warm_up_representative_routes() {
  local route
  for route in / /boards/pvstboard /boards/zvszboard /boards/funboard \
      '/boards/pvstboard/readPost?postNum=2' '/api/chat/messages?afterSeq=0' /sitemap.xml; do
    local_get "$route" 10 || { echo "Representative warm-up failed: $route" >&2; return 1; }
  done
}

verify_through_nginx() {
  # -k: before the cutover nginx may still serve the self-signed certificate.
  local route
  for route in / '/boards/pvstboard/readPost?postNum=2'; do
    curl -kfsS --max-time 10 -o /dev/null --resolve sc1hub.com:443:127.0.0.1 "https://sc1hub.com$route" \
      || { echo "nginx path failed: $route" >&2; return 1; }
  done
}

verify_metaspace_headroom() {
  local pid max_bytes used_kb percent
  pid="$(systemctl show -p MainPID --value "$SERVICE")"
  [[ "$pid" =~ ^[0-9]+$ && "$pid" != 0 ]] || { echo "No running JVM for $SERVICE" >&2; return 1; }
  max_bytes="$(sudo -u "$APP_USER" jinfo -flag MaxMetaspaceSize "$pid" 2>/dev/null | sed -n 's/.*=\([0-9][0-9]*\).*/\1/p')"
  used_kb="$(sudo -u "$APP_USER" jstat -gc "$pid" | awk 'NR == 2 { printf "%d\n", $10 }')"
  [[ "$max_bytes" =~ ^[0-9]+$ && "$used_kb" =~ ^[0-9]+$ && "$max_bytes" -gt 0 ]] \
    || { echo "Could not measure Metaspace" >&2; return 1; }
  percent=$(( used_kb * 1024 * 100 / max_bytes ))
  echo "Metaspace after warm-up: ${used_kb}KB / $(( max_bytes / 1024 ))KB (${percent}%)"
  printf '%s commit=%s used=%sKB max=%sKB pct=%s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$COMMIT" \
    "$used_kb" "$(( max_bytes / 1024 ))" "$percent" >> "$METASPACE_LOG" 2>/dev/null || true
  if (( percent >= 95 )); then
    echo "Metaspace is at ${percent}% of its cap; refusing the release." >&2
    return 1
  fi
  if (( percent >= 85 )); then
    echo "WARNING: Metaspace is at ${percent}% of its cap." >&2
  fi
}

HAD_EXISTING_WAR=0
rollback_and_restart() {
  echo "New release failed; restoring the previous WAR." >&2
  journalctl -u "$SERVICE" -n 60 --no-pager >&2 || true
  systemctl stop "$SERVICE" || true
  if [[ "$HAD_EXISTING_WAR" == 1 && -f "$ROLLBACK_WAR" ]]; then
    cp -p "$ROLLBACK_WAR" "$WAR"
  else
    rm -f "$WAR"
  fi
  rm -rf "$EXPLODED"
  systemctl start "$SERVICE" || { echo "Rollback restart failed." >&2; return 1; }
  wait_for_local_health || { echo "Rollback release did not become healthy." >&2; return 1; }
  echo "Previous WAR restored and running." >&2
}

shopt -s nullglob
for sql in "$RELEASE_DIR"/sql/pre/*.sql; do
  echo "Applying $(basename "$sql") (idempotent)..."
  run_sql "$sql"
done

install -o "$APP_USER" -g "$APP_USER" -m 640 "$RELEASE_DIR/ROOT.war" "$STAGED"
if [[ -f "$WAR" ]]; then
  cp -p "$WAR" "$ROLLBACK_WAR"
  HAD_EXISTING_WAR=1
fi

systemctl stop "$SERVICE"
# Schema removals run only while no release is up: the old WAR may still query those tables.
for sql in "$RELEASE_DIR"/sql/stopped/*.sql; do
  echo "Applying $(basename "$sql") (idempotent, service stopped)..."
  if ! run_sql "$sql"; then
    echo "Failed on $(basename "$sql"); restarting the previous release untouched." >&2
    rm -f "$STAGED"
    systemctl start "$SERVICE" || true
    exit 1
  fi
done

mv "$STAGED" "$WAR"
rm -rf "$EXPLODED"
if ! systemctl start "$SERVICE"; then
  rollback_and_restart || true
  exit 1
fi

if wait_for_local_health \
   && warm_up_representative_routes \
   && verify_through_nginx \
   && verify_metaspace_headroom \
   && wait_for_stable_local_health \
   && verify_metaspace_headroom; then
  printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$COMMIT" >> "$APP_HOME/releases.log"
  echo "Release $COMMIT is live."
  exit 0
fi

echo "Startup, warm-up, nginx or Metaspace verification failed." >&2
rollback_and_restart || true
exit 1
