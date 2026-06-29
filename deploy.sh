#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -f ".deploy.env" ]]; then
  # shellcheck disable=SC1091
  source ".deploy.env"
fi

DEPLOY_HOST="${DEPLOY_HOST:-sc1hub-prod}"
DEPLOY_USER="${DEPLOY_USER:-}"
REMOTE_TOMCAT_DIR="${REMOTE_TOMCAT_DIR:-/home/hosting_users/sc1hub/tomcat}"
REMOTE_SCRIPT_DIR="${REMOTE_SCRIPT_DIR:-$(dirname "$REMOTE_TOMCAT_DIR")/scripts}"
REMOTE_CONFIG_DIR="${REMOTE_CONFIG_DIR:-$(dirname "$REMOTE_TOMCAT_DIR")/config}"
REMOTE_WAR_NAME="${REMOTE_WAR_NAME:-ROOT.war}"
REMOTE_STOP_CMD="${REMOTE_STOP_CMD:-\$REMOTE_TOMCAT_DIR/bin/shutdown.sh}"
REMOTE_START_CMD="${REMOTE_START_CMD:-\$REMOTE_TOMCAT_DIR/bin/startup.sh}"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"
export JAVA_HOME GRADLE_USER_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [[ -n "$DEPLOY_USER" ]]; then
  REMOTE="$DEPLOY_USER@$DEPLOY_HOST"
else
  REMOTE="$DEPLOY_HOST"
fi
REMOTE_WEBAPPS_DIR="$REMOTE_TOMCAT_DIR/webapps"
REMOTE_WAR_PATH="$REMOTE_WEBAPPS_DIR/$REMOTE_WAR_NAME"
REMOTE_UPLOAD_PATH="$REMOTE_WAR_PATH.uploading"
REMOTE_EXPLODED_DIR="$REMOTE_WEBAPPS_DIR/${REMOTE_WAR_NAME%.war}"
REMOTE_CLEANUP_SCRIPT="$REMOTE_SCRIPT_DIR/cleanup-hosting-storage.sh"
REMOTE_ONE_LINE_STRATEGY_SQL="$REMOTE_SCRIPT_DIR/20260616_create_one_line_strategy.sql"
REMOTE_ONLINE_PROPS="$REMOTE_CONFIG_DIR/application-online.properties"
REMOTE_HTTP_PORT="${REMOTE_HTTP_PORT:-8645}"

echo "Building bootWar..."
./gradlew clean bootWar </dev/null

WAR_FILE="$(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name '*.war' ! -name '*-plain.war' | sort | tail -n 1)"
if [[ -z "$WAR_FILE" ]]; then
  echo "No bootWar artifact found in build/libs." >&2
  exit 1
fi

echo "Artifact: $WAR_FILE"
echo "Target:   $REMOTE:$REMOTE_WAR_PATH"
echo
read -r -p "Deploy to Cafe24 now? [y/N] " answer
case "$answer" in
  y|Y|yes|YES) ;;
  *) echo "Canceled."; exit 0 ;;
esac

echo "Uploading WAR..."
scp "$WAR_FILE" "$REMOTE:$REMOTE_UPLOAD_PATH"

echo "Uploading maintenance scripts..."
ssh "$REMOTE" "mkdir -p '$REMOTE_SCRIPT_DIR'"
scp "$ROOT_DIR/scripts/cleanup-hosting-storage.sh" "$REMOTE:$REMOTE_CLEANUP_SCRIPT"
scp "$ROOT_DIR/src/main/resources/sql/20260616_create_one_line_strategy.sql" "$REMOTE:$REMOTE_ONE_LINE_STRATEGY_SQL"

echo "Installing WAR and restarting Tomcat..."
ssh "$REMOTE" \
  ". ~/.bash_profile
   set -e
   REMOTE_TOMCAT_DIR='$REMOTE_TOMCAT_DIR'
   REMOTE_CONFIG_DIR='$REMOTE_CONFIG_DIR'
   REMOTE_ONLINE_PROPS='$REMOTE_ONLINE_PROPS'
   REMOTE_WAR_NAME='$REMOTE_WAR_NAME'
   REMOTE_HTTP_PORT='$REMOTE_HTTP_PORT'
   REMOTE_ONE_LINE_STRATEGY_SQL='$REMOTE_ONE_LINE_STRATEGY_SQL'
   mkdir -p '$REMOTE_WEBAPPS_DIR'
   mkdir -p \"\$REMOTE_CONFIG_DIR\"
   chmod 700 \"\$REMOTE_CONFIG_DIR\"
   SETENV_SH='$REMOTE_TOMCAT_DIR/bin/setenv.sh'
   touch \"\$SETENV_SH\"
   if ! grep -q 'SC1Hub Spring profile' \"\$SETENV_SH\"; then
     {
       echo ''
       echo '# SC1Hub Spring profile'
       echo 'export SPRING_PROFILES_ACTIVE=\"\${SPRING_PROFILES_ACTIVE:-online}\"'
     } >> \"\$SETENV_SH\"
   fi
   if ! grep -q 'SC1Hub external config' \"\$SETENV_SH\"; then
     {
       echo ''
       echo '# SC1Hub external config'
       echo 'export SPRING_CONFIG_ADDITIONAL_LOCATION=\"\${SPRING_CONFIG_ADDITIONAL_LOCATION:-file:/home/hosting_users/sc1hub/config/}\"'
     } >> \"\$SETENV_SH\"
   fi
   LEGACY_ONLINE_PROPS=\"\$REMOTE_TOMCAT_DIR/webapps/ROOT/WEB-INF/classes/application-online.properties\"
   if [ ! -f \"\$REMOTE_ONLINE_PROPS\" ] && [ -f \"\$LEGACY_ONLINE_PROPS\" ]; then
     cp \"\$LEGACY_ONLINE_PROPS\" \"\$REMOTE_ONLINE_PROPS\"
     chmod 600 \"\$REMOTE_ONLINE_PROPS\"
   fi
   if [ ! -s \"\$REMOTE_ONLINE_PROPS\" ]; then
     echo \"Missing required online config: \$REMOTE_ONLINE_PROPS\" >&2
     exit 1
   fi
   if [ -f \"\$REMOTE_TOMCAT_DIR/logs/catalina.out\" ]; then
     : > \"\$REMOTE_TOMCAT_DIR/logs/catalina.out\" || true
   fi
   $REMOTE_STOP_CMD || true
   for attempt in 1 2 3 4 5 6 7 8 9 10; do
     if ! curl -fsS -I --max-time 2 \"http://127.0.0.1:\$REMOTE_HTTP_PORT/\" >/dev/null 2>&1; then
       break
     fi
     perl -e 'select undef, undef, undef, 1' 2>/dev/null || true
   done
   if curl -fsS -I --max-time 2 \"http://127.0.0.1:\$REMOTE_HTTP_PORT/\" >/dev/null 2>&1; then
     echo \"Tomcat is still responding on port \$REMOTE_HTTP_PORT after shutdown.\" >&2
     exit 1
   fi
   chmod +x '$REMOTE_CLEANUP_SCRIPT'
   PROP=\"\$REMOTE_ONLINE_PROPS\"
   DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*#\\1#')
   ONE_LINE_STRATEGY_TABLES=\$(MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" -N -s -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = '\$DB_NAME' AND table_name IN ('one_line_strategy', 'one_line_strategy_category');\" \"\$DB_NAME\")
   if [ \"\$ONE_LINE_STRATEGY_TABLES\" != \"2\" ]; then
     echo 'Applying one-line strategy schema...'
     MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" \"\$DB_NAME\" < \"\$REMOTE_ONE_LINE_STRATEGY_SQL\"
   fi
   mv '$REMOTE_UPLOAD_PATH' '$REMOTE_WAR_PATH'
   rm -rf '$REMOTE_EXPLODED_DIR'
   $REMOTE_START_CMD
   for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
     if curl -fsS -I --max-time 2 \"http://127.0.0.1:\$REMOTE_HTTP_PORT/\" >/dev/null 2>&1; then
       exit 0
     fi
     perl -e 'select undef, undef, undef, 1' 2>/dev/null || true
   done
   echo \"Tomcat did not respond on port \$REMOTE_HTTP_PORT after startup.\" >&2
   tail -n 120 \"\$REMOTE_TOMCAT_DIR/logs/catalina.out\" >&2 || true
   exit 1"

echo "Deploy complete."
