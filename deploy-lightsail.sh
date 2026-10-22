#!/usr/bin/env bash
# Builds, verifies and deploys SC1Hub to the AWS Lightsail host (deploy/lightsail/).
# deploy.sh keeps serving Cafe24 until the cutover; see docs/lightsail-migration-plan-20261022.md.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -f ".deploy-lightsail.env" ]]; then
  # shellcheck disable=SC1091
  source ".deploy-lightsail.env"
fi

DEPLOY_HOST="${DEPLOY_HOST:-sc1hub-lightsail}"
# Releases go out only from a clean commit that origin/main already contains (AGENTS.md).
# true only for pre-cutover rehearsal builds on the new host.
ALLOW_UNRELEASED_COMMIT="${ALLOW_UNRELEASED_COMMIT:-false}"

JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle}"
export JAVA_HOME GRADLE_USER_HOME
export PATH="$JAVA_HOME/bin:$PATH"

SQL_DIR="$ROOT_DIR/src/main/resources/sql"
# Applied in order while the current release still runs; each script is idempotent.
PRE_START_SQL=(
  20260711_create_visitor_daily_identity.sql
  20260901_drop_member_reset_token.sql
  20261013_add_post_mod_date.sql
)
# Applied only while Tomcat is stopped: the running release may still query these tables.
STOPPED_SQL=(
  20260907_drop_supportboard.sql
  20260907_drop_one_line_strategy.sql
)

git fetch --quiet origin main
COMMIT="$(git rev-parse HEAD)"
gate_problems=()
git diff --quiet HEAD -- || gate_problems+=("tracked files have uncommitted changes")
git merge-base --is-ancestor HEAD origin/main || gate_problems+=("HEAD $COMMIT is not contained in origin/main")
if (( ${#gate_problems[@]} > 0 )); then
  printf 'Release gate: %s\n' "${gate_problems[@]}" >&2
  if [[ "$ALLOW_UNRELEASED_COMMIT" != "true" ]]; then
    echo "Push the verified commit to origin/main first (or set ALLOW_UNRELEASED_COMMIT=true for a rehearsal)." >&2
    exit 1
  fi
  echo "ALLOW_UNRELEASED_COMMIT=true: continuing with an unreleased build for rehearsal." >&2
  COMMIT="$COMMIT-unreleased"
fi

echo "Building and verifying release WAR..."
./gradlew clean build </dev/null

WAR_FILE="$(find "$ROOT_DIR/build/libs" -maxdepth 1 -type f -name '*.war' ! -name '*-plain.war' | sort | tail -n 1)"
[[ -n "$WAR_FILE" ]] || { echo "No bootWar artifact found in build/libs." >&2; exit 1; }

echo "Commit:   $COMMIT"
echo "Artifact: $WAR_FILE"
echo "Target:   $DEPLOY_HOST"
echo
read -r -p "Deploy to Lightsail now? [y/N] " answer
case "$answer" in
  y|Y|yes|YES) ;;
  *) echo "Canceled."; exit 0 ;;
esac

RELEASE_DIR="$(ssh "$DEPLOY_HOST" 'mktemp -d /tmp/sc1hub-release.XXXXXX')"
cleanup_remote() { ssh "$DEPLOY_HOST" "rm -rf '$RELEASE_DIR'" >/dev/null 2>&1 || true; }
trap cleanup_remote EXIT

echo "Uploading release to $DEPLOY_HOST:$RELEASE_DIR..."
ssh "$DEPLOY_HOST" "mkdir -p '$RELEASE_DIR/sql/pre' '$RELEASE_DIR/sql/stopped'"
scp -q "$WAR_FILE" "$DEPLOY_HOST:$RELEASE_DIR/ROOT.war"
scp -q "$ROOT_DIR/deploy/lightsail/remote-install.sh" "$DEPLOY_HOST:$RELEASE_DIR/remote-install.sh"
for i in "${!PRE_START_SQL[@]}"; do
  scp -q "$SQL_DIR/${PRE_START_SQL[$i]}" "$DEPLOY_HOST:$RELEASE_DIR/sql/pre/$(printf '%02d' "$i")-${PRE_START_SQL[$i]}"
done
for i in "${!STOPPED_SQL[@]}"; do
  scp -q "$SQL_DIR/${STOPPED_SQL[$i]}" "$DEPLOY_HOST:$RELEASE_DIR/sql/stopped/$(printf '%02d' "$i")-${STOPPED_SQL[$i]}"
done

echo "Installing on $DEPLOY_HOST..."
ssh "$DEPLOY_HOST" "sudo bash '$RELEASE_DIR/remote-install.sh' '$RELEASE_DIR' '$COMMIT'"

echo "Deploy complete: $COMMIT"
