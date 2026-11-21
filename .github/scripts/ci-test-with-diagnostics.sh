#!/usr/bin/env bash
# Runs a Gradle test command with periodic JVM thread dumps after long runtime (CI hang diagnosis).
# Intended for GitHub Actions ubuntu runners with Temurin JDK on PATH.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ci-diagnostics-lib.sh
source "${ROOT}/ci-diagnostics-lib.sh"

DIAG_DIR="${CI_DIAG_DIR:-build/ci-diagnostics/${CI_DIAG_SUITE:-suite}}"
FIRST_DUMP_SEC="${CI_TEST_FIRST_DUMP_SEC:-600}"
DUMP_INTERVAL_SEC="${CI_TEST_DUMP_INTERVAL_SEC:-300}"
WATCHDOG_POLL_SEC="${CI_TEST_WATCHDOG_POLL_SEC:-30}"

mkdir -p "$DIAG_DIR"

dump_test_jvms() {
  local elapsed_s=$1
  local ts_log ts_file
  ts_log="$(ci_diag_log_timestamp_utc)"
  ts_file="$(ci_diag_file_timestamp_utc)"
  local out="${DIAG_DIR}/thread-dump-${ts_file}-elapsed-${elapsed_s}s.txt"
  if ! ci_diag_artifact_path_is_valid "${out}"; then
    echo "CI diagnostics: internal error: artifact path is not valid: ${out}" >&2
    exit 2
  fi
  {
    echo "::group::CI thread dump suite=${CI_DIAG_SUITE:-unknown} elapsed_s=${elapsed_s} timestamp_utc=${ts_log}"
    echo "CI diagnostics: timestamp_utc=${ts_log} elapsed_s=${elapsed_s} suite=${CI_DIAG_SUITE:-unknown}"
    echo "CI diagnostics: saving full dump to ${out}"
    echo "=== jps -l ($(ci_diag_log_timestamp_utc)) ==="
    jps -l 2>&1 || true
    echo
    while read -r pid rest; do
      [[ -z "${pid:-}" ]] && continue
      case "$rest" in
        *Gradle*|*gradle*|*junit*|*JUnit*|*TestWorker*|*worker.org.gradle*)
          ;;
        *) continue ;;
      esac
      if ! kill -0 "$pid" 2>/dev/null; then
        echo "CI diagnostics: skip pid=${pid} (not running) main_class=${rest}"
        continue
      fi
      echo "=== process pid=${pid} timestamp_utc=${ts_log} main_class=${rest} ==="
      if command -v jcmd >/dev/null 2>&1; then
        jcmd "$pid" Thread.print 2>&1 || true
      elif command -v jstack >/dev/null 2>&1; then
        jstack -l "$pid" 2>&1 || true
      else
        echo "CI diagnostics: jcmd/jstack not available for pid=${pid}"
      fi
      echo
    done < <(jps -l 2>/dev/null || true)
    echo "::endgroup::"
  } | tee "$out"
}

watchdog() {
  local gradle_pid=$1
  local start_s
  start_s="$(date +%s)"
  local last_dump_s=0
  trap 'exit 0' TERM
  while kill -0 "$gradle_pid" 2>/dev/null; do
    sleep "$WATCHDOG_POLL_SEC" &
    local sleep_pid=$!
    wait "$sleep_pid" 2>/dev/null || true
    if ! kill -0 "$gradle_pid" 2>/dev/null; then
      exit 0
    fi
    local now_s elapsed_s
    now_s="$(date +%s)"
    elapsed_s=$((now_s - start_s))
    if (( elapsed_s < FIRST_DUMP_SEC )); then
      continue
    fi
    if (( last_dump_s == 0 || now_s - last_dump_s >= DUMP_INTERVAL_SEC )); then
      dump_test_jvms "$elapsed_s"
      last_dump_s=$now_s
    fi
  done
}

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <gradle-command> [args...]" >&2
  exit 2
fi

"$@" &
gradle_pid=$!

watchdog "$gradle_pid" &
watchdog_pid=$!

set +e
wait "$gradle_pid"
gradle_exit=$?
set -e
echo "CI diagnostics: gradle exit code=${gradle_exit}"

if kill -TERM "$watchdog_pid" 2>/dev/null; then
  wait "$watchdog_pid" 2>/dev/null || true
fi

if (( gradle_exit != 0 )); then
  dump_test_jvms "exit-${gradle_exit}"
fi

exit "$gradle_exit"