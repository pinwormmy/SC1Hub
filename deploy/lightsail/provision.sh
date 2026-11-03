#!/usr/bin/env bash
# Prepares a fresh Lightsail Ubuntu 24.04 host for SC1Hub. Safe to re-run.
#
#   scp -r deploy/lightsail ubuntu@HOST:/tmp/sc1hub-provision
#   ssh ubuntu@HOST 'sudo bash /tmp/sc1hub-provision/provision.sh'
#
# Optional environment:
#   TOMCAT_VERSION            Apache Tomcat 10.1.x release to install (default below)
#   CERTBOT_EMAIL             Let's Encrypt account e-mail
#   CERTBOT_AGREE_TOS=yes     The operator's explicit agreement to the Let's Encrypt terms
# A certificate is requested only when both are set and /etc/letsencrypt/cloudflare.ini
# (a Cloudflare API token limited to Zone:DNS:Edit on sc1hub.com) exists. Until then nginx
# serves the self-signed snakeoil certificate so the rest of the host can be tested.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOMCAT_VERSION="${TOMCAT_VERSION:-10.1.61}"
APP_USER=sc1hub
APP_HOME=/home/hosting_users/sc1hub
BASE="$APP_HOME/tomcat"
TOMCAT_ROOT=/opt/tomcat
PROPS="$APP_HOME/config/application-online.properties"
LE_LIVE=/etc/letsencrypt/live/sc1hub.com
CF_CREDENTIALS=/etc/letsencrypt/cloudflare.ini

step() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run with sudo"
# shellcheck disable=SC1091
. /etc/os-release
[[ "$ID" == "ubuntu" && "$VERSION_ID" == "24.04" ]] || die "expected Ubuntu 24.04, found $PRETTY_NAME"

step "Time zone Asia/Seoul (bot slots, visitor rollups and MariaDB NOW() depend on it)"
timedatectl set-timezone Asia/Seoul

step "1 GB swap file"
if ! swapon --show=NAME --noheadings | grep -qx /swapfile; then
  [[ -f /swapfile ]] || fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile >/dev/null
  swapon /swapfile
fi
grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
echo 'vm.swappiness=10' > /etc/sysctl.d/90-sc1hub.conf
sysctl -q -p /etc/sysctl.d/90-sc1hub.conf

step "Packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-17-jdk-headless mariadb-server nginx certbot \
  python3-certbot-dns-cloudflare rclone unattended-upgrades needrestart ssl-cert curl
JAVA_HOME="/usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture)"
[[ -x "$JAVA_HOME/bin/java" ]] || die "JDK 17 not found at $JAVA_HOME"

step "Automatic security updates (JDK, nginx, MariaDB, OS); services restart, the host does not reboot"
cat > /etc/apt/apt.conf.d/20auto-upgrades <<'EOF'
APT::Periodic::Update-Package-Lists "1";
APT::Periodic::Unattended-Upgrade "1";
EOF
cat > /etc/apt/apt.conf.d/52sc1hub-unattended <<'EOF'
Unattended-Upgrade::Automatic-Reboot "false";
EOF
mkdir -p /etc/needrestart/conf.d
cat > /etc/needrestart/conf.d/90-sc1hub.conf <<'EOF'
# Restart services (including sc1hub-tomcat after a JDK update) without prompting.
$nrconf{restart} = 'a';
EOF

step "Journal size cap"
mkdir -p /etc/systemd/journald.conf.d
printf '[Journal]\nSystemMaxUse=300M\n' > /etc/systemd/journald.conf.d/90-sc1hub.conf
systemctl restart systemd-journald

step "Service account and the Cafe24 directory layout"
mkdir -p /home/hosting_users
if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --user-group --home-dir "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER"
fi
install -d -o "$APP_USER" -g "$APP_USER" -m 750 "$APP_HOME"
install -d -o "$APP_USER" -g "$APP_USER" -m 700 "$APP_HOME/config"
install -d -o root -g root -m 700 "$APP_HOME/backup"
for dir in logs temp work webapps webapps/img webapps/upload data data/assistant; do
  install -d -o "$APP_USER" -g "$APP_USER" -m 750 "$BASE/$dir"
done
# The app can read but not rewrite its own container configuration.
install -d -o root -g "$APP_USER" -m 750 "$BASE" "$BASE/bin" "$BASE/conf" "$BASE/lib"
# HostConfig's per-host config base (Engine "sc1hub", Host "localhost") stays writable for Tomcat.
install -d -o "$APP_USER" -g "$APP_USER" -m 750 "$BASE/conf/sc1hub" "$BASE/conf/sc1hub/localhost"

step "Apache Tomcat $TOMCAT_VERSION"
TOMCAT_DIR="$TOMCAT_ROOT/apache-tomcat-$TOMCAT_VERSION"
if [[ ! -d "$TOMCAT_DIR" ]]; then
  work="$(mktemp -d)"
  trap 'rm -rf "$work"' EXIT
  name="apache-tomcat-$TOMCAT_VERSION.tar.gz"
  path="tomcat/tomcat-10/v$TOMCAT_VERSION/bin/$name"
  curl -fsSLo "$work/$name" "https://dlcdn.apache.org/$path" \
    || curl -fsSLo "$work/$name" "https://archive.apache.org/dist/$path"
  curl -fsSLo "$work/$name.sha512" "https://downloads.apache.org/$path.sha512" \
    || curl -fsSLo "$work/$name.sha512" "https://archive.apache.org/dist/$path.sha512"
  expected="$(awk '{print $1}' "$work/$name.sha512")"
  actual="$(sha512sum "$work/$name" | awk '{print $1}')"
  [[ -n "$expected" && "$expected" == "$actual" ]] || die "SHA-512 mismatch for $name"
  mkdir -p "$TOMCAT_ROOT"
  tar -xzf "$work/$name" -C "$TOMCAT_ROOT"
fi
# The tarball ships bin/ and lib/ as owner-only; the service account runs Tomcat but must not rewrite it.
chown -R root:"$APP_USER" "$TOMCAT_DIR"
chmod -R u=rwX,g=rX,o= "$TOMCAT_DIR"
ln -sfn "$TOMCAT_DIR" "$TOMCAT_ROOT/current"

for file in catalina.properties context.xml logging.properties web.xml; do
  [[ -f "$BASE/conf/$file" ]] || install -o root -g "$APP_USER" -m 640 "$TOMCAT_ROOT/current/conf/$file" "$BASE/conf/$file"
done
install -o root -g "$APP_USER" -m 640 "$HERE/server.xml" "$BASE/conf/server.xml"
cat > "$BASE/bin/setenv.sh" <<EOF
# Generated by deploy/lightsail/provision.sh
export JAVA_HOME="$JAVA_HOME"
export SPRING_PROFILES_ACTIVE=online
export SPRING_CONFIG_ADDITIONAL_LOCATION=file:$APP_HOME/config/
export CATALINA_OPTS="-Xms128m -Xmx256m -XX:MaxMetaspaceSize=192m -XX:+ExitOnOutOfMemoryError -Duser.timezone=Asia/Seoul -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Dsun.reflect.inflationThreshold=2147483647"
EOF
chown root:"$APP_USER" "$BASE/bin/setenv.sh"
chmod 750 "$BASE/bin/setenv.sh"

step "MariaDB"
install -m 644 "$HERE/mariadb-sc1hub.cnf" /etc/mysql/mariadb.conf.d/90-sc1hub.cnf
systemctl enable --now mariadb
systemctl restart mariadb
if [[ -s "$PROPS" ]]; then
  prop() { grep "^$1=" "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r'; }
  db_url="$(prop spring.datasource.url)"
  db_user="$(prop spring.datasource.username)"
  db_pass="$(prop spring.datasource.password)"
  db_name="$(printf '%s' "$db_url" | sed -E 's#^jdbc:(mysql|mariadb)://[^/]+/([^?]+).*#\2#')"
  [[ "$db_name" =~ ^[A-Za-z0-9_]+$ && "$db_user" =~ ^[A-Za-z0-9_]+$ && -n "$db_pass" ]] \
    || die "datasource settings in $PROPS are missing or unexpected"
  sql_pass="$(printf '%s' "$db_pass" | sed -e "s/\\\\/\\\\\\\\/g" -e "s/'/''/g")"
  # Through stdin so the password never shows up in the process list.
  mariadb <<SQL
CREATE DATABASE IF NOT EXISTS \`$db_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '$db_user'@'localhost' IDENTIFIED BY '$sql_pass';
CREATE USER IF NOT EXISTS '$db_user'@'127.0.0.1' IDENTIFIED BY '$sql_pass';
ALTER USER '$db_user'@'localhost' IDENTIFIED BY '$sql_pass';
ALTER USER '$db_user'@'127.0.0.1' IDENTIFIED BY '$sql_pass';
GRANT ALL PRIVILEGES ON \`$db_name\`.* TO '$db_user'@'localhost';
GRANT ALL PRIVILEGES ON \`$db_name\`.* TO '$db_user'@'127.0.0.1';
SQL
  echo "Database '$db_name' and user '$db_user' are ready."
else
  echo "NOTE: $PROPS is missing; copy it (mode 600, owner $APP_USER) and re-run to create the database user."
fi

step "TLS certificate"
if [[ ! -s "$LE_LIVE/fullchain.pem" && -f "$CF_CREDENTIALS" && -n "${CERTBOT_EMAIL:-}" && "${CERTBOT_AGREE_TOS:-}" == "yes" ]]; then
  chmod 600 "$CF_CREDENTIALS"
  certbot certonly --non-interactive --agree-tos --no-eff-email -m "$CERTBOT_EMAIL" \
    --dns-cloudflare --dns-cloudflare-credentials "$CF_CREDENTIALS" --dns-cloudflare-propagation-seconds 30 \
    --deploy-hook "systemctl reload nginx" \
    -d sc1hub.com -d www.sc1hub.com
fi
if [[ -s "$LE_LIVE/fullchain.pem" ]]; then
  cert="$LE_LIVE/fullchain.pem"; key="$LE_LIVE/privkey.pem"
  echo "Using the Let's Encrypt certificate (renewed by certbot.timer)."
else
  cert=/etc/ssl/certs/ssl-cert-snakeoil.pem; key=/etc/ssl/private/ssl-cert-snakeoil.key
  echo "NOTE: no Let's Encrypt certificate yet; nginx uses the self-signed snakeoil certificate."
fi
cat > /etc/nginx/snippets/sc1hub-tls.conf <<EOF
ssl_certificate $cert;
ssl_certificate_key $key;
ssl_protocols TLSv1.2 TLSv1.3;
ssl_prefer_server_ciphers off;
ssl_session_cache shared:SC1HUB_TLS:10m;
ssl_session_timeout 1d;
ssl_session_tickets off;
EOF

step "nginx"
install -m 644 "$HERE/nginx-sc1hub.conf" /etc/nginx/sites-available/sc1hub
ln -sfn /etc/nginx/sites-available/sc1hub /etc/nginx/sites-enabled/sc1hub
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable nginx
systemctl reload nginx || systemctl restart nginx

step "systemd units"
install -m 644 "$HERE/sc1hub-tomcat.service" /etc/systemd/system/sc1hub-tomcat.service
install -m 644 "$HERE/sc1hub-backup.service" /etc/systemd/system/sc1hub-backup.service
install -m 644 "$HERE/sc1hub-backup.timer" /etc/systemd/system/sc1hub-backup.timer
install -m 750 "$HERE/sc1hub-backup.sh" /usr/local/sbin/sc1hub-backup.sh
install -m 750 "$HERE/restore-db.sh" /usr/local/sbin/sc1hub-restore-db.sh
install -d -m 700 /etc/sc1hub
if [[ ! -f /etc/sc1hub/backup.env ]]; then
  cat > /etc/sc1hub/backup.env <<'EOF'
# Off-site copy target for sc1hub-backup.sh, e.g. SC1HUB_BACKUP_REMOTE=sc1hub-crypt:
# The remote must be an rclone "crypt" remote (database dumps hold member data) unless
# SC1HUB_BACKUP_ALLOW_PLAIN_REMOTE=1 is set on purpose. Leave empty for local-only backups.
SC1HUB_BACKUP_REMOTE=
EOF
  chmod 600 /etc/sc1hub/backup.env
fi
systemctl daemon-reload
systemctl enable sc1hub-tomcat.service
systemctl enable --now sc1hub-backup.timer

step "Summary"
echo "Tomcat:  $(readlink -f "$TOMCAT_ROOT/current")"
echo "Java:    $("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"
echo "MariaDB: $(mariadb --version)"
echo "nginx:   $(nginx -v 2>&1)"
echo "Next: place $PROPS, restore the database (sc1hub-restore-db.sh), copy uploads and the RAG index,"
echo "then deploy with ./deploy-lightsail.sh from the workstation."
