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
REMOTE_WAR_BACKUP_PATH="$REMOTE_WAR_PATH.rollback"
REMOTE_CLEANUP_SCRIPT="$REMOTE_SCRIPT_DIR/cleanup-hosting-storage.sh"
REMOTE_OOM_RECOVERY_SCRIPT="$REMOTE_SCRIPT_DIR/restart-tomcat-after-oom.sh"
REMOTE_ONE_LINE_STRATEGY_SQL="$REMOTE_SCRIPT_DIR/20260616_create_one_line_strategy.sql"
REMOTE_STRATEGY_RECOMMENDATION_SQL="$REMOTE_SCRIPT_DIR/20260824_create_one_line_strategy_recommendation.sql"
REMOTE_VISITOR_COUNT_SQL="$REMOTE_SCRIPT_DIR/20260711_create_visitor_daily_identity.sql"
REMOTE_ONLINE_PROPS="$REMOTE_CONFIG_DIR/application-online.properties"
REMOTE_HTTP_PORT="${REMOTE_HTTP_PORT:-8645}"
ROLLBACK_REQUIRES_LEGACY_RUNTIME="${ROLLBACK_REQUIRES_LEGACY_RUNTIME:-true}"

echo "Building and verifying release WAR..."
./gradlew clean build </dev/null

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
scp "$ROOT_DIR/scripts/restart-tomcat-after-oom.sh" "$REMOTE:$REMOTE_OOM_RECOVERY_SCRIPT"
scp "$ROOT_DIR/src/main/resources/sql/20260616_create_one_line_strategy.sql" "$REMOTE:$REMOTE_ONE_LINE_STRATEGY_SQL"
scp "$ROOT_DIR/src/main/resources/sql/20260824_create_one_line_strategy_recommendation.sql" "$REMOTE:$REMOTE_STRATEGY_RECOMMENDATION_SQL"
scp "$ROOT_DIR/src/main/resources/sql/20260711_create_visitor_daily_identity.sql" "$REMOTE:$REMOTE_VISITOR_COUNT_SQL"

echo "Installing WAR and restarting Tomcat..."
ssh "$REMOTE" \
  ". ~/.bash_profile
   set -e
   REMOTE_TOMCAT_DIR='$REMOTE_TOMCAT_DIR'
   REMOTE_CONFIG_DIR='$REMOTE_CONFIG_DIR'
   REMOTE_ONLINE_PROPS='$REMOTE_ONLINE_PROPS'
   REMOTE_WAR_NAME='$REMOTE_WAR_NAME'
   REMOTE_WAR_PATH='$REMOTE_WAR_PATH'
   REMOTE_UPLOAD_PATH='$REMOTE_UPLOAD_PATH'
   REMOTE_EXPLODED_DIR='$REMOTE_EXPLODED_DIR'
   REMOTE_WAR_BACKUP_PATH='$REMOTE_WAR_BACKUP_PATH'
   REMOTE_HTTP_PORT='$REMOTE_HTTP_PORT'
   REMOTE_OOM_RECOVERY_SCRIPT='$REMOTE_OOM_RECOVERY_SCRIPT'
   REMOTE_ONE_LINE_STRATEGY_SQL='$REMOTE_ONE_LINE_STRATEGY_SQL'
   REMOTE_STRATEGY_RECOMMENDATION_SQL='$REMOTE_STRATEGY_RECOMMENDATION_SQL'
   REMOTE_VISITOR_COUNT_SQL='$REMOTE_VISITOR_COUNT_SQL'
   ROLLBACK_REQUIRES_LEGACY_RUNTIME='$ROLLBACK_REQUIRES_LEGACY_RUNTIME'
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
   if grep -q -- '-Dsun.reflect.inflationThreshold=' \"\$SETENV_SH\"; then
     sed -i -E 's/-Dsun\.reflect\.inflationThreshold=[0-9]+/-Dsun.reflect.inflationThreshold=2147483647/g' \"\$SETENV_SH\"
   fi
   if ! grep -q 'SC1Hub reflection accessor limit' \"\$SETENV_SH\"; then
     {
       echo ''
       echo '# SC1Hub reflection accessor limit'
       echo '# Keep reflective calls native so generated accessor classes cannot consume the Metaspace cap.'
       echo 'export CATALINA_OPTS=\"\${CATALINA_OPTS:-} -Dsun.reflect.inflationThreshold=2147483647\"'
     } >> \"\$SETENV_SH\"
   fi
   if ! grep -q -- '-Dsun.reflect.inflationThreshold=2147483647' \"\$SETENV_SH\"; then
     echo 'Failed to configure the SC1Hub reflection accessor limit.' >&2
     exit 1
   fi
   sed -i '\|^# SC1Hub OOM recovery hook$|d' \"\$SETENV_SH\"
   sed -i '\|-XX:OnOutOfMemoryError=|d' \"\$SETENV_SH\"
   {
     echo ''
     echo '# SC1Hub OOM recovery hook'
     echo \"export CATALINA_OPTS=\\\"\\\${CATALINA_OPTS:-} '-XX:OnOutOfMemoryError=exec $REMOTE_OOM_RECOVERY_SCRIPT %p'\\\"\"
   } >> \"\$SETENV_SH\"
   if ! grep -Fq -- \"'-XX:OnOutOfMemoryError=exec $REMOTE_OOM_RECOVERY_SCRIPT %p'\" \"\$SETENV_SH\"; then
     echo 'Failed to configure the SC1Hub OOM recovery hook.' >&2
     exit 1
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
   chmod 600 \"\$REMOTE_ONLINE_PROPS\"
   chmod +x '$REMOTE_CLEANUP_SCRIPT' \"\$REMOTE_OOM_RECOVERY_SCRIPT\"
   RUNTIME_DETAILS=\$(\"\$REMOTE_TOMCAT_DIR/bin/version.sh\" 2>&1 || true)
   if ! printf '%s' \"\$RUNTIME_DETAILS\" | grep -Eq 'Server version: Apache Tomcat/10\\.0\\.' \\
      || ! printf '%s' \"\$RUNTIME_DETAILS\" | grep -Eq 'JVM Version: +17\\.'; then
     echo 'Cafe24 must be running Tomcat 10.0.x and Java 17 before this WAR is installed.' >&2
     echo \"Detected instead: \$RUNTIME_DETAILS\" >&2
     exit 1
   fi
   PROP=\"\$REMOTE_ONLINE_PROPS\"
   DB_URL=\$(grep '^spring.datasource.url=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_USER=\$(grep '^spring.datasource.username=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_PASS=\$(grep '^spring.datasource.password=' \"\$PROP\" | cut -d= -f2- | tr -d '\r')
   DB_NAME=\$(printf '%s' \"\$DB_URL\" | sed -E 's#^jdbc:(mysql|mariadb)://[^/]+/([^?]+).*#\\2#')
   if [ -z \"\$DB_USER\" ] || [ -z \"\$DB_PASS\" ] || ! printf '%s' \"\$DB_NAME\" | grep -Eq '^[A-Za-z0-9_]+$'; then
     echo 'Online database configuration is missing or invalid.' >&2
     exit 1
   fi
   echo 'Applying visitor count schema...'
   MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" \"\$DB_NAME\" < \"\$REMOTE_VISITOR_COUNT_SQL\"
   ONE_LINE_STRATEGY_TABLES=\$(MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" -N -s -e \"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name IN ('one_line_strategy', 'one_line_strategy_category');\" \"\$DB_NAME\")
   if [ \"\$ONE_LINE_STRATEGY_TABLES\" != \"2\" ]; then
     echo 'Applying one-line strategy schema...'
     MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" \"\$DB_NAME\" < \"\$REMOTE_ONE_LINE_STRATEGY_SQL\"
   fi
   echo 'Applying one-line strategy recommendation schema...'
   MYSQL_PWD=\"\$DB_PASS\" mysql -u \"\$DB_USER\" \"\$DB_NAME\" < \"\$REMOTE_STRATEGY_RECOMMENDATION_SQL\"
   if [ ! -s \"\$REMOTE_UPLOAD_PATH\" ]; then
     echo \"Uploaded WAR is missing or empty: \$REMOTE_UPLOAD_PATH\" >&2
     exit 1
   fi

   wait_for_local_health() {
     for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
       if curl -fsS -I --max-time 2 \"http://127.0.0.1:\$REMOTE_HTTP_PORT/\" >/dev/null 2>&1; then
         return 0
       fi
       perl -e 'select undef, undef, undef, 1' 2>/dev/null || true
     done
     return 1
   }
   wait_for_stable_local_health() {
     if ! wait_for_local_health; then
       return 1
     fi
     for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
       perl -e 'select undef, undef, undef, 1' 2>/dev/null || true
       if ! curl -fsS -I --max-time 2 "http://127.0.0.1:\$REMOTE_HTTP_PORT/" >/dev/null 2>&1; then
         return 1
       fi
     done
     return 0
   }
   warm_up_representative_routes() {
     for route in / /strategy-tips /boards/pvstboard /boards/zvszboard /boards/funboard \
       '/boards/pvstboard/readPost?postNum=2' '/api/chat/messages?afterSeq=0' /sitemap.xml; do
       if ! curl -fsS --max-time 5 -o /dev/null \"http://127.0.0.1:\$REMOTE_HTTP_PORT\$route\"; then
         echo \"Representative warm-up failed: \$route\" >&2
         return 1
       fi
     done
     return 0
   }
   verify_metaspace_headroom() {
     APP_PIDS=\$(jps -lv 2>/dev/null | awk '/org\\.apache\\.catalina\\.startup\\.Bootstrap/{print \$1}')
     APP_PID_COUNT=\$(printf '%s\\n' \"\$APP_PIDS\" | awk 'NF { count++ } END { print count + 0 }')
     if [ \"\$APP_PID_COUNT\" != \"1\" ]; then
       echo \"Expected exactly one Catalina JVM, found \$APP_PID_COUNT.\" >&2
       return 1
     fi
     APP_PID=\$(printf '%s\\n' \"\$APP_PIDS\" | awk 'NF { print; exit }')
     APP_CMDLINE=\"/proc/\$APP_PID/cmdline\"
     if [ ! -r \"\$APP_CMDLINE\" ]; then
       echo \"Could not read production JVM command line for PID \$APP_PID.\" >&2
       return 1
     fi
     REFLECTION_ARG_COUNT=\$(tr '\\000' '\\n' < \"\$APP_CMDLINE\" | grep -Fxc -- '-Dsun.reflect.inflationThreshold=2147483647' || true)
     OOM_ARG_COUNT=\$(tr '\\000' '\\n' < \"\$APP_CMDLINE\" | grep -Fxc -- \"-XX:OnOutOfMemoryError=exec \$REMOTE_OOM_RECOVERY_SCRIPT %p\" || true)
     if [ \"\$REFLECTION_ARG_COUNT\" != \"1\" ] || [ \"\$OOM_ARG_COUNT\" != \"1\" ]; then
       echo \"Production JVM safety arguments are missing or duplicated. reflection=\$REFLECTION_ARG_COUNT oom=\$OOM_ARG_COUNT\" >&2
       return 1
     fi
     METASPACE_MAX_BYTES=\$(jinfo -flag MaxMetaspaceSize \"\$APP_PID\" 2>/dev/null | sed -n 's/.*=\\([0-9][0-9]*\\).*/\\1/p')
     if ! printf '%s' \"\$METASPACE_MAX_BYTES\" | grep -Eq '^[0-9]+$' || [ \"\$METASPACE_MAX_BYTES\" -le 0 ]; then
       echo 'Could not read the production MaxMetaspaceSize flag.' >&2
       return 1
     fi
     METASPACE_MAX_KB=\$((METASPACE_MAX_BYTES / 1024))
     METASPACE_USED_KB=\$(jstat -gc \"\$APP_PID\" | awk 'NR == 2 { printf \"%d\\n\", \$10 }')
     if ! printf '%s' \"\$METASPACE_USED_KB\" | grep -Eq '^[0-9]+$'; then
       echo 'Could not measure production Metaspace usage.' >&2
       return 1
     fi
     METASPACE_PERCENT=\$((METASPACE_USED_KB * 100 / METASPACE_MAX_KB))
     echo \"Production Metaspace after warm-up: \${METASPACE_USED_KB}KB / \${METASPACE_MAX_KB}KB (\${METASPACE_PERCENT}%)\"
     printf '%s used=%sKB max=%sKB pct=%s\\n' \"\$(date '+%Y-%m-%d %H:%M:%S')\" \"\$METASPACE_USED_KB\" \"\$METASPACE_MAX_KB\" \"\$METASPACE_PERCENT\" >> \"\$REMOTE_TOMCAT_DIR/logs/metaspace-history.log\" 2>/dev/null || true
     if [ \"\$METASPACE_PERCENT\" -ge 95 ]; then
       echo \"Production Metaspace is at \${METASPACE_PERCENT}% of the \${METASPACE_MAX_KB}KB cap; refusing the release.\" >&2
       return 1
     fi
     if [ \"\$METASPACE_PERCENT\" -ge 85 ]; then
       echo \"WARNING: Production Metaspace is at \${METASPACE_PERCENT}% of the \${METASPACE_MAX_KB}KB cap. A first-time cold code path can still exhaust the remainder.\" >&2
     fi
     return 0
   }
   wait_for_tomcat_shutdown() {
     attempt=0
     while [ \"\$attempt\" -lt 50 ]; do
       if ! curl -fsS -I --max-time 2 \"http://127.0.0.1:\$REMOTE_HTTP_PORT/\" >/dev/null 2>&1; then
         return 0
       fi
       attempt=\$((attempt + 1))
       perl -e 'select undef, undef, undef, 1' 2>/dev/null || true
     done
     return 1
   }
   rollback_and_restart() {
     echo 'New deployment failed; restoring the previous WAR.' >&2
     $REMOTE_STOP_CMD || true
     wait_for_tomcat_shutdown || true
     if [ \"\$HAD_EXISTING_WAR\" = \"1\" ] && [ -f \"\$REMOTE_WAR_BACKUP_PATH\" ]; then
       cp -p \"\$REMOTE_WAR_BACKUP_PATH\" \"\$REMOTE_WAR_PATH\"
     else
       rm -f \"\$REMOTE_WAR_PATH\"
     fi
     rm -rf \"\$REMOTE_EXPLODED_DIR\"
     if [ \"\$ROLLBACK_REQUIRES_LEGACY_RUNTIME\" = \"true\" ]; then
       echo 'Legacy WAR and config restored. Change Cafe24 back to Tomcat 8.5 / JDK 8 before starting it.' >&2
       return 1
     fi
     if ! $REMOTE_START_CMD; then
       echo 'Rollback WAR was restored, but Tomcat restart failed.' >&2
       return 1
     fi
     if ! wait_for_local_health; then
       echo \"Rollback restart did not become healthy on port \$REMOTE_HTTP_PORT.\" >&2
       return 1
     fi
     echo 'Previous WAR restored and restarted.' >&2
     return 0
   }

   HAD_EXISTING_WAR=0
   if [ -f \"\$REMOTE_WAR_PATH\" ]; then
     cp -p \"\$REMOTE_WAR_PATH\" \"\$REMOTE_WAR_BACKUP_PATH\"
     chmod 600 \"\$REMOTE_WAR_BACKUP_PATH\"
     HAD_EXISTING_WAR=1
   fi
   CATALINA_OUT=\"\$REMOTE_TOMCAT_DIR/logs/catalina.out\"
   if [ -s \"\$CATALINA_OUT\" ]; then
     ROTATED=\"\$CATALINA_OUT.\$(date '+%Y%m%dT%H%M%S')\"
     if cp -p \"\$CATALINA_OUT\" \"\$ROTATED\"; then
       : > \"\$CATALINA_OUT\" || true
       ls -1t \"\$CATALINA_OUT.\"[0-9]* 2>/dev/null | awk 'NR > 10' | while read -r stale; do
         rm -f \"\$stale\"
       done
     else
       echo 'Could not preserve catalina.out before the release; keeping it intact.' >&2
     fi
   fi
   $REMOTE_STOP_CMD || true
   if ! wait_for_tomcat_shutdown; then
     echo \"Tomcat is still responding on port \$REMOTE_HTTP_PORT after shutdown.\" >&2
     exit 1
   fi
   if ! mv \"\$REMOTE_UPLOAD_PATH\" \"\$REMOTE_WAR_PATH\"; then
     rollback_and_restart || true
     exit 1
   fi
   rm -rf \"\$REMOTE_EXPLODED_DIR\"
   if ! $REMOTE_START_CMD; then
     rollback_and_restart || true
     exit 1
   fi
   if wait_for_local_health &&
      warm_up_representative_routes &&
      verify_metaspace_headroom &&
      wait_for_stable_local_health &&
      verify_metaspace_headroom; then
     exit 0
   fi
   echo \"Tomcat failed startup, representative warm-up, or Metaspace verification.\" >&2
   echo \"Inspect \$REMOTE_TOMCAT_DIR/logs/catalina.out for startup details.\" >&2
   rollback_and_restart || true
   exit 1"

echo "Deploy complete."
