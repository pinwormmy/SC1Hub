#!/usr/bin/env bash
set -euo pipefail

THRESHOLD_PERCENT="${THRESHOLD_PERCENT:-90}"
TOMCAT_DIR="${TOMCAT_DIR:-/home/hosting_users/sc1hub/tomcat}"
WEBAPPS_DIR="${WEBAPPS_DIR:-$TOMCAT_DIR/webapps}"
LOGS_DIR="${LOGS_DIR:-$TOMCAT_DIR/logs}"
TEMP_DIR="${TEMP_DIR:-$TOMCAT_DIR/temp}"
WORK_DIR="${WORK_DIR:-$TOMCAT_DIR/work}"
BACKUP_KEEP="${BACKUP_KEEP:-2}"
DRY_RUN="${DRY_RUN:-false}"

usage_percent() {
  df -P "$TOMCAT_DIR" | awk 'NR == 2 { gsub("%", "", $5); print $5 }'
}

run_cmd() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run]'
    printf ' %q' "$@"
    printf '\n'
    return 0
  fi

  "$@"
}

CURRENT_USAGE="$(usage_percent)"
echo "Disk usage for $TOMCAT_DIR: ${CURRENT_USAGE}%"

if (( CURRENT_USAGE < THRESHOLD_PERCENT )); then
  echo "Below threshold (${THRESHOLD_PERCENT}%). Nothing to clean."
  exit 0
fi

echo "At or above threshold (${THRESHOLD_PERCENT}%). Cleaning hosting storage..."

if [[ -d "$LOGS_DIR" ]]; then
  find "$LOGS_DIR" -type f \( -name '*.log' -o -name '*.out' -o -name '*.txt' \) -print |
    while IFS= read -r log_file; do
      echo "Truncating log: $log_file"
      run_cmd truncate -s 0 "$log_file"
    done
fi

if [[ -d "$WEBAPPS_DIR" ]]; then
  find "$WEBAPPS_DIR" -maxdepth 1 -type f -name '*.war.bak.*' -print |
    sort -r |
    awk -v keep="$BACKUP_KEEP" 'NR > keep { print }' |
    while IFS= read -r backup_file; do
      echo "Removing old WAR backup: $backup_file"
      run_cmd rm -f "$backup_file"
    done
fi

if [[ -d "$TEMP_DIR" ]]; then
  echo "Removing Tomcat temp files under $TEMP_DIR"
  find "$TEMP_DIR" -mindepth 1 -maxdepth 1 -print |
    while IFS= read -r temp_path; do
      run_cmd rm -rf "$temp_path"
    done
fi

if [[ -d "$WORK_DIR" ]]; then
  echo "Removing Tomcat work files under $WORK_DIR"
  find "$WORK_DIR" -mindepth 1 -maxdepth 1 -print |
    while IFS= read -r work_path; do
      run_cmd rm -rf "$work_path"
    done
fi

echo "Disk usage after cleanup: $(usage_percent)%"
