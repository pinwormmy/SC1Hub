#!/home/bin/bash2
set -u

TOMCAT_DIR="${TOMCAT_DIR:-/home/hosting_users/sc1hub/tomcat}"
HTTP_PORT="${HTTP_PORT:-8645}"
JPS_BIN="${JPS_BIN:-/usr/local/jdk8/bin/jps}"
CURL_BIN="${CURL_BIN:-/usr/bin/curl}"
PERL_BIN="${PERL_BIN:-/usr/bin/perl}"
PROC_ROOT="${PROC_ROOT:-/proc}"
STATE_DIR="${STATE_DIR:-$TOMCAT_DIR/temp/sc1hub-oom-recovery}"
LOG_FILE="${LOG_FILE:-$TOMCAT_DIR/logs/oom-recovery.log}"
LOCK_DIR="$STATE_DIR/lock"
LAST_RUN_FILE="$STATE_DIR/last-run-epoch"
RESTART_COOLDOWN_SECONDS="${RESTART_COOLDOWN_SECONDS:-300}"
DRY_RUN="${DRY_RUN:-false}"

mkdir -p "$STATE_DIR" "$(dirname "$LOG_FILE")"
exec >> "$LOG_FILE" 2>&1

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*"
}

pause_one_second() {
    "$PERL_BIN" -e 'select undef, undef, undef, 1' 2>/dev/null || true
}

is_healthy() {
    "$CURL_BIN" -fsS -I --max-time 3 "http://127.0.0.1:$HTTP_PORT/" >/dev/null 2>&1
}

find_catalina_pids() {
    "$JPS_BIN" -lv 2>/dev/null |
        awk '/org\.apache\.catalina\.startup\.Bootstrap/{print $1}'
}

verify_catalina_pid() {
    local pid="$1"
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    [[ -r "$PROC_ROOT/$pid/cmdline" ]] || return 1
    tr '\000' ' ' < "$PROC_ROOT/$pid/cmdline" |
        grep -q 'org.apache.catalina.startup.Bootstrap'
}

if ! mkdir "$LOCK_DIR" 2>/dev/null; then
    log "Another OOM recovery is already running; skipping duplicate invocation."
    exit 0
fi
trap 'rmdir "$LOCK_DIR" 2>/dev/null || true' EXIT

now_epoch="$(date '+%s')"
if [[ -f "$LAST_RUN_FILE" ]]; then
    last_run_epoch="$(sed -n '1p' "$LAST_RUN_FILE" 2>/dev/null || true)"
    if [[ "$last_run_epoch" =~ ^[0-9]+$ ]] &&
            (( now_epoch - last_run_epoch < RESTART_COOLDOWN_SECONDS )); then
        log "OOM recovery is inside the ${RESTART_COOLDOWN_SECONDS}s cooldown; refusing a restart loop."
        exit 0
    fi
fi
printf '%s\n' "$now_epoch" > "$LAST_RUN_FILE"

catalina_pids="$(find_catalina_pids)"
pid_count="$(printf '%s\n' "$catalina_pids" | awk 'NF { count++ } END { print count + 0 }')"
if (( pid_count > 1 )); then
    log "Found $pid_count Catalina JVMs; refusing to terminate an ambiguous target."
    exit 1
fi

if (( pid_count == 1 )); then
    app_pid="$(printf '%s\n' "$catalina_pids" | awk 'NF { print; exit }')"
    if ! verify_catalina_pid "$app_pid"; then
        log "PID $app_pid did not verify as Catalina; refusing termination."
        exit 1
    fi
    if [[ "$DRY_RUN" == "true" ]]; then
        log "Dry run verified Catalina PID $app_pid; no restart performed."
        exit 0
    fi

    log "OOM detected; terminating verified Catalina PID $app_pid."
    kill "$app_pid" 2>/dev/null || true
    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
        [[ ! -d "$PROC_ROOT/$app_pid" ]] && break
        pause_one_second
    done

    if [[ -d "$PROC_ROOT/$app_pid" ]]; then
        if ! verify_catalina_pid "$app_pid"; then
            log "PID identity changed after SIGTERM; refusing SIGKILL."
            exit 1
        fi
        log "Catalina PID $app_pid did not stop; sending SIGKILL."
        kill -9 "$app_pid" 2>/dev/null || true
        for _ in 1 2 3 4 5; do
            [[ ! -d "$PROC_ROOT/$app_pid" ]] && break
            pause_one_second
        done
    fi

    if [[ -d "$PROC_ROOT/$app_pid" ]]; then
        log "Catalina PID $app_pid is still present; recovery aborted."
        exit 1
    fi
elif is_healthy; then
    log "No Catalina PID was reported but internal health is already OK; no action taken."
    exit 0
else
    log "No Catalina PID is running; starting Tomcat."
fi

if ! "$TOMCAT_DIR/bin/startup.sh"; then
    log "Tomcat startup command failed."
    exit 1
fi

for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
    if is_healthy; then
        new_pid="$(find_catalina_pids | awk 'NF { print; exit }')"
        log "Tomcat recovered successfully. pid=$new_pid attempt=$attempt"
        exit 0
    fi
    pause_one_second
done

log "Tomcat did not become healthy after OOM recovery."
exit 1
