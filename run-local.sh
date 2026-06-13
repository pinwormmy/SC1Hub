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

LOCAL_BASE_CONFIG="$ROOT_DIR/.local-data/application-local-base.properties"
grep -v '^spring\.profiles\.include=' "$ROOT_DIR/src/main/resources/application.properties" > "$LOCAL_BASE_CONFIG"

./gradlew bootRun --args="--spring.config.location=file:$LOCAL_BASE_CONFIG,file:$ROOT_DIR/src/main/resources/application-local.properties"
