#!/bin/sh
# 일별 방문자 수 추이 리포트 (읽기 전용).
# deploy.sh와 동일하게 호스팅 계정의 application-online.properties에서 DB 접속 정보를 읽는다.
set -eu

PROP="/home/hosting_users/sc1hub/config/application-online.properties"
if [ ! -f "$PROP" ]; then
  PROP="/home/hosting_users/sc1hub/tomcat/webapps/ROOT/WEB-INF/classes/application-online.properties"
fi
if [ ! -s "$PROP" ]; then
  echo "Missing online config: $PROP" >&2
  exit 1
fi

DB_URL=$(grep '^spring.datasource.url=' "$PROP" | cut -d= -f2- | tr -d '\r')
DB_USER=$(grep '^spring.datasource.username=' "$PROP" | cut -d= -f2- | tr -d '\r')
DB_PASS=$(grep '^spring.datasource.password=' "$PROP" | cut -d= -f2- | tr -d '\r')
DB_NAME=$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\1#')

SINCE="${1:-2026-02-01}"
MYSQL_PWD="$DB_PASS" mysql -u "$DB_USER" -N -s -e \
  "SELECT date, daily_count FROM visitor_count WHERE date >= '$SINCE' ORDER BY date;" \
  "$DB_NAME"
