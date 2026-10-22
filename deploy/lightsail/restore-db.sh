#!/usr/bin/env bash
# Restores a mysqldump file (plain or .gz) into the SC1Hub database on the Lightsail host.
# Installed as /usr/local/sbin/sc1hub-restore-db.sh.
#
#   sudo sc1hub-restore-db.sh /tmp/prod-dump.sql.gz --replace
#
# --replace drops and recreates the database first; without it the dump is loaded on top.
# Tomcat must be stopped so no request writes half-way through the restore.
set -euo pipefail

DUMP="${1:-}"
MODE="${2:-}"
APP_HOME=/home/hosting_users/sc1hub
PROPS="$APP_HOME/config/application-online.properties"
SERVICE=sc1hub-tomcat

die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

[[ $EUID -eq 0 ]] || die "run with sudo"
[[ -s "$DUMP" ]] || die "usage: sc1hub-restore-db.sh DUMP.sql[.gz] [--replace]"
[[ -s "$PROPS" ]] || die "missing $PROPS"
if systemctl is-active --quiet "$SERVICE"; then
  die "$SERVICE is running; stop it first (sudo systemctl stop $SERVICE)"
fi

db_url="$(grep '^spring.datasource.url=' "$PROPS" | head -1 | cut -d= -f2- | tr -d '\r')"
db_name="$(printf '%s' "$db_url" | sed -E 's#^jdbc:(mysql|mariadb)://[^/]+/([^?]+).*#\2#')"
[[ "$db_name" =~ ^[A-Za-z0-9_]+$ ]] || die "unexpected database name in $PROPS"

if [[ "$MODE" == "--replace" ]]; then
  echo "Recreating database $db_name..."
  mariadb -e "DROP DATABASE IF EXISTS \`$db_name\`; CREATE DATABASE \`$db_name\` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
fi

echo "Loading $DUMP into $db_name..."
started=$(date +%s)
if [[ "$DUMP" == *.gz ]]; then
  gzip -dc "$DUMP" | mariadb --default-character-set=utf8mb4 "$db_name"
else
  mariadb --default-character-set=utf8mb4 "$db_name" < "$DUMP"
fi
echo "Restored in $(( $(date +%s) - started ))s."

echo "Row counts:"
tables="$(mariadb -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='$db_name' AND table_type='BASE TABLE' ORDER BY table_name")"
for table in $tables; do
  printf '%s\t%s\n' "$table" "$(mariadb -N "$db_name" -e "SELECT COUNT(*) FROM \`$table\`")"
done
