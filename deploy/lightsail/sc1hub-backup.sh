#!/usr/bin/env bash
# Nightly SC1Hub backup on the Lightsail host (sc1hub-backup.timer, runs as root).
#
# Local copies under /home/hosting_users/sc1hub/backup/daily, kept for KEEP_LOCAL_DAYS:
#   sc1hub-<stamp>-db.sql.gz      full database dump
#   sc1hub-<stamp>-files.tar.gz   uploads and the RAG index
#   sc1hub-<stamp>-config.tar.gz  application-online.properties (secrets; local or encrypted only)
# Optional off-site copy with rclone to SC1HUB_BACKUP_REMOTE (set in /etc/sc1hub/backup.env).
set -euo pipefail

APP_HOME="${SC1HUB_APP_HOME:-/home/hosting_users/sc1hub}"
BACKUP_DIR="${SC1HUB_BACKUP_DIR:-$APP_HOME/backup/daily}"
KEEP_LOCAL_DAYS="${SC1HUB_BACKUP_KEEP_LOCAL_DAYS:-14}"
REMOTE="${SC1HUB_BACKUP_REMOTE:-}"
KEEP_REMOTE_DAYS="${SC1HUB_BACKUP_KEEP_REMOTE_DAYS:-30}"
ALLOW_PLAIN_REMOTE="${SC1HUB_BACKUP_ALLOW_PLAIN_REMOTE:-0}"
PROPS="$APP_HOME/config/application-online.properties"
BASE="$APP_HOME/tomcat"
MIN_FREE_KB=$((1024 * 1024))

umask 077
mkdir -p "$BACKUP_DIR"

free_kb="$(df -Pk "$BACKUP_DIR" | awk 'NR == 2 { print $4 }')"
if (( free_kb < MIN_FREE_KB )); then
  echo "Less than 1 GB free under $BACKUP_DIR; skipping the backup." >&2
  exit 1
fi

db_url="$(grep '^spring.datasource.url=' "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r')"
db_name="$(printf '%s' "$db_url" | sed -E 's#^jdbc:(mysql|mariadb)://[^/]+/([^?]+).*#\2#')"
[[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || { echo "Unexpected database name in $PROPS" >&2; exit 1; }

stamp="$(date +%Y%m%d-%H%M)"
prefix="$BACKUP_DIR/sc1hub-$stamp"

# root reaches MariaDB through the unix socket, so no password is stored for backups.
mariadb-dump --single-transaction --quick --routines --triggers --hex-blob \
  --default-character-set=utf8mb4 "$db_name" | gzip -6 > "$prefix-db.sql.gz.part"
gzip -t "$prefix-db.sql.gz.part"
mv "$prefix-db.sql.gz.part" "$prefix-db.sql.gz"

tar -C "$BASE" -czf "$prefix-files.tar.gz.part" webapps/img webapps/upload data/assistant
mv "$prefix-files.tar.gz.part" "$prefix-files.tar.gz"

tar -C "$APP_HOME" -czf "$prefix-config.tar.gz" config

find "$BACKUP_DIR" -maxdepth 1 -type f -name 'sc1hub-*' -mtime "+$KEEP_LOCAL_DAYS" -delete
echo "Local backup: $(du -ch "$prefix"-* | tail -1 | cut -f1) in $BACKUP_DIR"

if [[ -z "$REMOTE" ]]; then
  echo "No SC1HUB_BACKUP_REMOTE configured; local backup only."
  exit 0
fi

remote_name="${REMOTE%%:*}"
remote_type="$(rclone config show "$remote_name" 2>/dev/null | awk -F' = ' '$1 == "type" { print $2 }')"
if [[ -z "$remote_type" ]]; then
  echo "rclone remote '$remote_name' is not configured for root." >&2
  exit 1
fi
if [[ "$remote_type" != "crypt" && "$ALLOW_PLAIN_REMOTE" != "1" ]]; then
  echo "Refusing to upload member data to unencrypted remote '$remote_name' (type $remote_type)." >&2
  echo "Use an rclone crypt remote, or set SC1HUB_BACKUP_ALLOW_PLAIN_REMOTE=1 deliberately." >&2
  exit 1
fi

target="${REMOTE%/}"
[[ "$target" == *: ]] || target="$target/"
rclone copy "$prefix-db.sql.gz" "${target}db/"
# Uploads only ever grow, so an incremental copy keeps the remote cheap.
rclone copy "$BASE/webapps/img" "${target}files/img/"
rclone copy "$BASE/webapps/upload" "${target}files/upload/"
rclone copy "$BASE/data/assistant" "${target}files/assistant/"
if [[ "$remote_type" == "crypt" ]]; then
  rclone copy "$prefix-config.tar.gz" "${target}config/"
fi
rclone delete --min-age "${KEEP_REMOTE_DAYS}d" "${target}db/"
rclone delete --min-age "${KEEP_REMOTE_DAYS}d" "${target}config/" 2>/dev/null || true
echo "Off-site copy to $REMOTE finished."
