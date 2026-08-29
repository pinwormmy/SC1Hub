#!/home/bin/bash2
set -u

TOMCAT_DIR="${TOMCAT_DIR:-/home/hosting_users/sc1hub/tomcat}"
HTTP_PORT="${HTTP_PORT:-8645}"
SHUTDOWN_PORT="${SHUTDOWN_PORT:-8646}"
CURL_BIN="${CURL_BIN:-/usr/bin/curl}"
PERL_BIN="${PERL_BIN:-/usr/bin/perl}"
PROC_ROOT="${PROC_ROOT:-/proc}"
STATE_DIR="${STATE_DIR:-$TOMCAT_DIR/temp/sc1hub-oom-recovery}"
LOG_FILE="${LOG_FILE:-$TOMCAT_DIR/logs/oom-recovery.log}"
LOCK_DIR="$STATE_DIR/lock"
LOCK_OWNER_FILE="$LOCK_DIR/owner-pid"
LAST_RUN_FILE="$STATE_DIR/last-run-epoch"
RESTART_COOLDOWN_SECONDS="${RESTART_COOLDOWN_SECONDS:-300}"
DRY_RUN="${DRY_RUN:-false}"
EXPECTED_PID="${1:-}"
JAVA_HOME="${JAVA_HOME:-/usr/local/jdk8}"
JRE_HOME="${JRE_HOME:-$JAVA_HOME}"
CLOSED_SOCKET_FDS=""
export JAVA_HOME JRE_HOME

# HotSpot launches OnOutOfMemoryError through /bin/sh. The shell inherits Tomcat's
# listening sockets, so close only socket descriptors before running any child
# process. The configured hook uses `exec` so no intermediate shell keeps a copy.
close_inherited_socket_descriptors() {
    local fd_path fd
    for fd_path in "$PROC_ROOT/$$/fd/"*; do
        [[ -S "$fd_path" ]] || continue
        fd="${fd_path##*/}"
        case "$fd" in
            0|1|2|''|*[!0-9]*) continue ;;
        esac
        if eval "exec ${fd}>&-" 2>/dev/null; then
            CLOSED_SOCKET_FDS="${CLOSED_SOCKET_FDS}${CLOSED_SOCKET_FDS:+,}${fd}"
        fi
    done
}

close_inherited_socket_descriptors
mkdir -p "$STATE_DIR" "$(dirname "$LOG_FILE")"
exec >> "$LOG_FILE" 2>&1

log() {
    printf '%s %s\n' "$(date '+%Y-%m-%d %H:%M:%S %Z')" "$*"
}

pause_one_second() {
    "$PERL_BIN" -e 'select undef, undef, undef, 1' 2>/dev/null || true
}

is_healthy() {
    "$CURL_BIN" -fsS -I --connect-timeout 1 --max-time 2 \
        "http://127.0.0.1:$HTTP_PORT/" >/dev/null 2>&1
}

cmdline_matches_catalina() {
    local cmdline_path="$1"
    [[ -r "$cmdline_path" ]] || return 1
    tr '\000' '\n' < "$cmdline_path" |
        grep -Fxq 'org.apache.catalina.startup.Bootstrap' || return 1
    tr '\000' '\n' < "$cmdline_path" |
        grep -Fxq -- "-Dcatalina.home=$TOMCAT_DIR"
}

verify_catalina_pid() {
    local pid="$1"
    [[ "$pid" =~ ^[0-9]+$ ]] || return 1
    cmdline_matches_catalina "$PROC_ROOT/$pid/cmdline"
}

find_catalina_pids() {
    local cmdline_path pid
    for cmdline_path in "$PROC_ROOT"/[0-9]*/cmdline; do
        [[ -r "$cmdline_path" ]] || continue
        if cmdline_matches_catalina "$cmdline_path"; then
            pid="${cmdline_path%/cmdline}"
            printf '%s\n' "${pid##*/}"
        fi
    done
}

count_pids() {
    printf '%s\n' "$1" | awk 'NF { count++ } END { print count + 0 }'
}

port_is_listening() {
    local port_hex net_file
    port_hex="$(printf '%04X' "$1")"
    for net_file in "$PROC_ROOT/net/tcp" "$PROC_ROOT/net/tcp6"; do
        [[ -r "$net_file" ]] || continue
        if awk -v target="$port_hex" '
                $4 == "0A" {
                    split($2, address, ":")
                    if (address[2] == target) found = 1
                }
                END { exit found ? 0 : 1 }
            ' "$net_file"; then
            return 0
        fi
    done
    return 1
}

wait_for_ports_to_close() {
    local attempt
    for attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
        if ! port_is_listening "$HTTP_PORT" && ! port_is_listening "$SHUTDOWN_PORT"; then
            return 0
        fi
        pause_one_second
    done
    return 1
}

recovery_lock_owner_is_active() {
    local owner_pid
    owner_pid="$(sed -n '1p' "$LOCK_OWNER_FILE" 2>/dev/null || true)"
    [[ "$owner_pid" =~ ^[0-9]+$ ]] || return 1
    [[ -r "$PROC_ROOT/$owner_pid/cmdline" ]] || return 1
    tr '\000' ' ' < "$PROC_ROOT/$owner_pid/cmdline" | grep -Fq "$0"
}

acquire_recovery_lock() {
    if mkdir "$LOCK_DIR" 2>/dev/null; then
        printf '%s\n' "$$" > "$LOCK_OWNER_FILE"
        return 0
    fi
    if recovery_lock_owner_is_active; then
        return 1
    fi
    log "Removing stale OOM recovery lock."
    rm -f "$LOCK_OWNER_FILE"
    rmdir "$LOCK_DIR" 2>/dev/null || return 1
    mkdir "$LOCK_DIR" 2>/dev/null || return 1
    printf '%s\n' "$$" > "$LOCK_OWNER_FILE"
}

release_recovery_lock() {
    rm -f "$LOCK_OWNER_FILE"
    rmdir "$LOCK_DIR" 2>/dev/null || true
}

log "Closed inherited socket descriptor(s): ${CLOSED_SOCKET_FDS:-none}."
if ! acquire_recovery_lock; then
    log "Another OOM recovery is already running; skipping duplicate invocation."
    exit 0
fi
trap release_recovery_lock EXIT

now_epoch="$(date '+%s')"
if [[ -f "$LAST_RUN_FILE" ]]; then
    last_run_epoch="$(sed -n '1p' "$LAST_RUN_FILE" 2>/dev/null || true)"
    if [[ "$last_run_epoch" =~ ^[0-9]+$ ]] &&
            (( now_epoch - last_run_epoch < RESTART_COOLDOWN_SECONDS )); then
        log "OOM recovery is inside the ${RESTART_COOLDOWN_SECONDS}s cooldown; refusing a restart loop."
        exit 0
    fi
fi

catalina_pids="$(find_catalina_pids)"
pid_count="$(count_pids "$catalina_pids")"
if (( pid_count > 1 )); then
    log "Found $pid_count Catalina JVMs; refusing to terminate an ambiguous target."
    exit 1
fi

if [[ -n "$EXPECTED_PID" ]]; then
    if ! verify_catalina_pid "$EXPECTED_PID"; then
        log "Expected PID $EXPECTED_PID did not verify as this Catalina instance; refusing recovery."
        exit 1
    fi
    if (( pid_count != 1 )) || [[ "$catalina_pids" != "$EXPECTED_PID" ]]; then
        log "Expected PID $EXPECTED_PID does not match the single discovered Catalina PID; refusing recovery."
        exit 1
    fi
fi

if (( pid_count == 1 )); then
    app_pid="${EXPECTED_PID:-$catalina_pids}"
    if ! verify_catalina_pid "$app_pid"; then
        log "PID $app_pid did not verify as Catalina; refusing termination."
        exit 1
    fi
    if [[ "$DRY_RUN" == "true" ]]; then
        log "Dry run verified Catalina PID $app_pid; no restart performed."
        exit 0
    fi

    printf '%s\n' "$now_epoch" > "$LAST_RUN_FILE"
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
elif [[ "$DRY_RUN" == "true" ]]; then
    log "Dry run found no Catalina PID to verify."
    exit 1
elif is_healthy; then
    log "No Catalina PID was reported but internal health is already OK; no action taken."
    exit 0
else
    printf '%s\n' "$now_epoch" > "$LAST_RUN_FILE"
    log "No Catalina PID is running; recovery will start Tomcat."
fi

# The OOM child inherits evaluated CATALINA_OPTS. Clear it so setenv.sh rebuilds
# each option exactly once for the replacement JVM.
unset CATALINA_OPTS
for start_attempt in 1 2 3; do
    catalina_pids="$(find_catalina_pids)"
    pid_count="$(count_pids "$catalina_pids")"
    if (( pid_count > 1 )); then
        log "Found $pid_count Catalina JVMs during recovery; refusing a duplicate start."
        exit 1
    fi

    if (( pid_count == 0 )); then
        if ! wait_for_ports_to_close; then
            log "Tomcat ports $HTTP_PORT/$SHUTDOWN_PORT remained occupied; refusing a conflicting start."
            exit 1
        fi
        log "Starting Tomcat. start_attempt=$start_attempt"
        if ! "$TOMCAT_DIR/bin/startup.sh"; then
            log "Tomcat startup command failed. start_attempt=$start_attempt"
        fi
    else
        existing_pid="$(printf '%s\n' "$catalina_pids" | awk 'NF { print; exit }')"
        log "Catalina PID $existing_pid already exists; waiting without starting a duplicate."
    fi

    for health_attempt in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45; do
        if is_healthy; then
            recovered_pids="$(find_catalina_pids)"
            recovered_count="$(count_pids "$recovered_pids")"
            if (( recovered_count == 1 )); then
                new_pid="$(printf '%s\n' "$recovered_pids" | awk 'NF { print; exit }')"
                log "Tomcat recovered successfully. pid=$new_pid start_attempt=$start_attempt health_attempt=$health_attempt"
                exit 0
            fi
            log "Internal health responded but Catalina PID count is $recovered_count; refusing success."
            exit 1
        fi
        pause_one_second
    done
    log "Tomcat was not healthy after startup attempt $start_attempt; rechecking before retry."
done

log "Tomcat did not become healthy after OOM recovery."
exit 1
