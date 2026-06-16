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

echo "Installing WAR and restarting Tomcat..."
ssh "$REMOTE" \
  ". ~/.bash_profile
   set -e
   REMOTE_TOMCAT_DIR='$REMOTE_TOMCAT_DIR'
   mkdir -p '$REMOTE_WEBAPPS_DIR'
   SETENV_SH='$REMOTE_TOMCAT_DIR/bin/setenv.sh'
   touch \"\$SETENV_SH\"
   if ! grep -q 'SC1Hub Spring profile' \"\$SETENV_SH\"; then
     {
       echo ''
       echo '# SC1Hub Spring profile'
       echo 'export SPRING_PROFILES_ACTIVE=\"\${SPRING_PROFILES_ACTIVE:-online}\"'
     } >> \"\$SETENV_SH\"
   fi
   BACKUP_STAMP=\$(date +%Y%m%d%H%M%S)
   if [ -f '$REMOTE_WAR_PATH' ]; then
     cp '$REMOTE_WAR_PATH' '$REMOTE_WAR_PATH.bak.'\"\$BACKUP_STAMP\"
   fi
   chmod +x '$REMOTE_CLEANUP_SCRIPT'
   mv '$REMOTE_UPLOAD_PATH' '$REMOTE_WAR_PATH'
   rm -rf '$REMOTE_EXPLODED_DIR'
   $REMOTE_STOP_CMD || true
   $REMOTE_START_CMD"

echo "Deploy complete."
