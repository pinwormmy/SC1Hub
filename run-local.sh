#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"
export JAVA_HOME GRADLE_USER_HOME
export PATH="$JAVA_HOME/bin:$PATH"

mkdir -p \
  "$ROOT_DIR/.local-data/img" \
  "$ROOT_DIR/.local-data/upload" \
  "$ROOT_DIR/.local-data/assistant"

LOCAL_SAFE_CONFIG="$ROOT_DIR/.local-data/application-local-safe.properties"
LOCAL_CONFIG="${SC1HUB_LOCAL_CONFIG:-$ROOT_DIR/src/main/resources/application-local.properties}"

if [[ ! -f "$LOCAL_CONFIG" ]]; then
  cat >&2 <<EOF
Missing local config: $LOCAL_CONFIG

Create it from the template:
  cp "$ROOT_DIR/src/main/resources/application-local.example.properties" "$ROOT_DIR/src/main/resources/application-local.properties"

Then edit spring.datasource.* for a local MySQL database only.
EOF
  exit 1
fi

DATASOURCE_URL="$(grep -E '^spring\.datasource\.url=' "$LOCAL_CONFIG" | tail -n 1 | cut -d= -f2- || true)"
if [[ -z "$DATASOURCE_URL" ]]; then
  echo "Missing spring.datasource.url in $LOCAL_CONFIG" >&2
  exit 1
fi

if [[ ! "$DATASOURCE_URL" =~ jdbc:mysql://(localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0)(:|/) ]]; then
  cat >&2 <<EOF
Refusing to run against a non-local datasource:
  spring.datasource.url=$DATASOURCE_URL

run-local.sh only accepts localhost/127.0.0.1/[::1]/0.0.0.0 MySQL URLs.
Set SC1HUB_ALLOW_NONLOCAL_DB=true only for an intentional, non-production test database.
EOF
  if [[ "${SC1HUB_ALLOW_NONLOCAL_DB:-false}" != "true" ]]; then
    exit 1
  fi
fi

cat > "$LOCAL_SAFE_CONFIG" <<EOF
sc1hub.gemini.allowLiveCalls=false
sc1hub.assistant.bot.enabled=false
sc1hub.assistant.bot.autoPublishEnabled=false
sc1hub.assistant.bot.autoPublishCatchUpEnabled=false
sc1hub.assistant.rag.enabled=false
sc1hub.assistant.rag.autoUpdate.enabled=false
sc1hub.http-redirect.enabled=false
EOF

echo "Starting SC1Hub locally on http://localhost:8082"
echo "Using local config: $LOCAL_CONFIG"
echo "Active Spring profile: local"
echo "Safety overrides: Gemini live calls off, assistant bot off, auto-publish off, port 80 HTTP redirect off."

./gradlew bootRun --args="--spring.profiles.active=local --spring.config.additional-location=file:$LOCAL_CONFIG,file:$LOCAL_SAFE_CONFIG"
