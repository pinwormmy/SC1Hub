#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOCAL_PROPS="$ROOT_DIR/src/main/resources/application-local.properties"
REMOTE_PROPS="/home/hosting_users/sc1hub/tomcat/webapps/ROOT/WEB-INF/classes/application-online.properties"
REMOTE="${REMOTE:-sc1hub-prod}"

mkdir -p "$ROOT_DIR/.local-data"

local_prop() {
  grep "^$1=" "$LOCAL_PROPS" | cut -d= -f2- | tr -d '\r'
}

LOCAL_DB_URL="$(local_prop spring.datasource.url)"
LOCAL_DB_USER="$(local_prop spring.datasource.username)"
LOCAL_DB_PASS="$(local_prop spring.datasource.password)"
LOCAL_DB_NAME="$(printf "%s" "$LOCAL_DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')"

echo "Fetching production schema only..."
ssh "$REMOTE" "PROP='$REMOTE_PROPS';
  DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#');
  MYSQL_PWD=\"\$DB_PASS\" mysqldump -u \"\$DB_USER\" --no-data --routines --triggers --events --single-transaction \"\$DB_NAME\"" \
  > "$ROOT_DIR/.local-data/prod-schema.sql"

echo "Fetching non-personal board_list seed..."
ssh "$REMOTE" "PROP='$REMOTE_PROPS';
  DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#');
  MYSQL_PWD=\"\$DB_PASS\" mysqldump -u \"\$DB_USER\" --no-create-info --skip-triggers \"\$DB_NAME\" board_list" \
  > "$ROOT_DIR/.local-data/board-list-seed.sql"

echo "Applying local schema..."
MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" < "$ROOT_DIR/.local-data/prod-schema.sql"
MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" < "$ROOT_DIR/.local-data/board-list-seed.sql"
MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
  -e "INSERT INTO total_visitor_count (total_count) SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM total_visitor_count);"

ONE_LINE_STRATEGY_TABLES=$(
  MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" -N -s -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '$LOCAL_DB_NAME' AND table_name IN ('one_line_strategy', 'one_line_strategy_category');"
)
if [[ "$ONE_LINE_STRATEGY_TABLES" != "2" ]]; then
  echo "Applying one-line strategy schema..."
  MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" < "$ROOT_DIR/src/main/resources/sql/20260616_create_one_line_strategy.sql"
fi

echo "Local DB schema is ready."
