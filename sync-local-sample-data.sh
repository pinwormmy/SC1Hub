#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

LOCAL_PROPS="${LOCAL_PROPS:-$ROOT_DIR/src/main/resources/application-local.properties}"
REMOTE_PROPS="${REMOTE_PROPS:-/home/hosting_users/sc1hub/tomcat/webapps/ROOT/WEB-INF/classes/application-online.properties}"
REMOTE="${REMOTE:-sc1hub-prod}"
POST_LIMIT="${POST_LIMIT:-20}"
INCLUDE_COMMENTS="${INCLUDE_COMMENTS:-false}"

BOARDS=(
  tvszboard
  tvspboard
  tvstboard
  zvstboard
  zvspboard
  zvszboard
  pvstboard
  pvszboard
  pvspboard
  teamplayguideboard
  tipboard
)

mkdir -p "$ROOT_DIR/.local-data/sample-data"

if [[ ! -f "$LOCAL_PROPS" ]]; then
  echo "Missing local config: $LOCAL_PROPS" >&2
  echo "Create it from src/main/resources/application-local.example.properties first." >&2
  exit 1
fi

local_prop() {
  grep "^$1=" "$LOCAL_PROPS" | cut -d= -f2- | tr -d '\r'
}

LOCAL_DB_URL="$(local_prop spring.datasource.url)"
LOCAL_DB_USER="$(local_prop spring.datasource.username)"
LOCAL_DB_PASS="$(local_prop spring.datasource.password)"
LOCAL_DB_NAME="$(printf "%s" "$LOCAL_DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')"

if [[ ! "$LOCAL_DB_URL" =~ jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0)(:|/) ]]; then
  echo "Refusing to write sample data to a non-local datasource: $LOCAL_DB_URL" >&2
  exit 1
fi

if ! [[ "$POST_LIMIT" =~ ^[0-9]+$ ]] || [[ "$POST_LIMIT" -lt 1 ]]; then
  echo "POST_LIMIT must be a positive integer." >&2
  exit 1
fi

BOARD_ARGS=()
for board in "${BOARDS[@]}"; do
  BOARD_ARGS+=("$board")
  if [[ "$INCLUDE_COMMENTS" == "true" ]]; then
    BOARD_ARGS+=("${board}_comment")
  fi
done

echo "Fetching board_list..."
ssh "$REMOTE" "PROP='$REMOTE_PROPS';
  DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
  DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#');
  MYSQL_PWD=\"\$DB_PASS\" mysqldump -u \"\$DB_USER\" --single-transaction --skip-lock-tables --no-create-info --skip-triggers \"\$DB_NAME\" board_list" \
  > "$ROOT_DIR/.local-data/sample-data/board_list.sql"

echo "Clearing local strategy board sample data..."
{
  echo "SET FOREIGN_KEY_CHECKS=0;"
  for table in "${BOARD_ARGS[@]}"; do
    echo "TRUNCATE TABLE \`$table\`;"
  done
  echo "TRUNCATE TABLE board_list;"
  echo "SET FOREIGN_KEY_CHECKS=1;"
} | MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME"

MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
  < "$ROOT_DIR/.local-data/sample-data/board_list.sql"

for board in "${BOARDS[@]}"; do
  echo "Fetching $board posts, limit=$POST_LIMIT..."
  ssh "$REMOTE" "PROP='$REMOTE_PROPS';
    DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
    DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
    DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
    DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#');
    WHERE=\"notice=1 OR post_num IN (SELECT post_num FROM (SELECT post_num FROM $board WHERE notice=0 ORDER BY reg_date DESC, post_num DESC LIMIT $POST_LIMIT) sampled_posts)\";
    MYSQL_PWD=\"\$DB_PASS\" mysqldump -u \"\$DB_USER\" --single-transaction --skip-lock-tables --no-create-info --skip-triggers --where=\"\$WHERE\" \"\$DB_NAME\" $board" \
    > "$ROOT_DIR/.local-data/sample-data/$board.sql"
  MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
    < "$ROOT_DIR/.local-data/sample-data/$board.sql"

  if [[ "$INCLUDE_COMMENTS" == "true" ]]; then
    echo "Fetching $board comments for sampled posts..."
    ssh "$REMOTE" "PROP='$REMOTE_PROPS';
      DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
      DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
      DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r');
      DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#');
      WHERE=\"post_num IN (SELECT post_num FROM (SELECT post_num FROM $board WHERE notice=1 OR post_num IN (SELECT post_num FROM (SELECT post_num FROM $board WHERE notice=0 ORDER BY reg_date DESC, post_num DESC LIMIT $POST_LIMIT) sampled_posts)) local_posts)\";
      MYSQL_PWD=\"\$DB_PASS\" mysqldump -u \"\$DB_USER\" --single-transaction --skip-lock-tables --no-create-info --skip-triggers --where=\"\$WHERE\" \"\$DB_NAME\" ${board}_comment" \
      > "$ROOT_DIR/.local-data/sample-data/${board}_comment.sql"
    MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
      < "$ROOT_DIR/.local-data/sample-data/${board}_comment.sql"
    MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
      -e "UPDATE \`${board}_comment\` SET id = NULL, nickname = 'local_guest', password = NULL;"
  fi

  MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
    -e "UPDATE \`$board\` SET writer = 'SC1Hub';"
done

MYSQL_PWD="$LOCAL_DB_PASS" mysql -u "$LOCAL_DB_USER" "$LOCAL_DB_NAME" \
  -e "INSERT INTO total_visitor_count (total_count) SELECT 0 WHERE NOT EXISTS (SELECT 1 FROM total_visitor_count);"

echo "Local sample strategy-board data is ready."
