#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dependency-snapshot-wait-lib.sh
source "${ROOT}/dependency-snapshot-wait-lib.sh"

fail() {
  echo "fail: $*" >&2
  exit 1
}

pass() {
  echo "ok: $*"
}

# --- unit: header parsing ---
pass "header parsing"
assert_ready() {
  dependency_graph_snapshot_ready_from_headers "$1"
}
assert_ready $'HTTP/2.0 200 OK\nX-Github-Dependency-Graph-Snapshot-Warnings:\n' || fail "empty warnings should be ready"
if dependency_graph_snapshot_ready_from_headers $'HTTP/2.0 200 OK\nX-Github-Dependency-Graph-Snapshot-Warnings: ref_sha\n' >/dev/null; then
  fail "non-empty warnings should not be ready"
fi

# --- unit: parent/child title binding ---
pass "parent/child title binding"
child_run_display_title_matches_parent "dependency-submission-pr • source-run-44146864545" 44146864545 || fail "marker match"
child_run_display_title_matches_parent "dependency-submission-pr • source-run-999" 44146864545 && fail "wrong parent id must not match"

# --- unit: job conclusions ---
pass "job conclusions"
jobs_ok='{"jobs":[{"name":"Verify dependency graph artifact","conclusion":"success"},{"name":"Submit dependency graph (PR)","conclusion":"success"}]}'
jobs_skip='{"jobs":[{"name":"Verify dependency graph artifact","conclusion":"skipped"},{"name":"Submit dependency graph (PR)","conclusion":"skipped"}]}'
pr_submission_child_jobs_ok "${jobs_ok}" || fail "both jobs success"
pr_submission_child_jobs_ok "${jobs_skip}" && fail "skipped jobs must fail"

# --- integration mocks via gh_api_mock ---
MOCK_REPO="mcliquid/SCEE"
MOCK_HEAD="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
MOCK_BASE="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
MOCK_PARENT_ID="1001"
MOCK_CHILD_ID="2002"

MOCK_SUBMISSION_STATE="in_progress"
MOCK_CHILD_ROWS=""
MOCK_CHILD_JOBS=""
MOCK_COMPARE_HEADERS=$'HTTP/2.0 200 OK\nX-Github-Dependency-Graph-Snapshot-Warnings:\n'

gh_api_mock() {
  local url="" status conclusion conclusion_json include_headers=false
  local arg
  for arg in "$@"; do
    case "${arg}" in
      -i) include_headers=true ;;
      repos/*) url="${arg}" ;;
    esac
  done
  case "${url}" in
    *dependency-submission.yml/runs*)
      if [[ "${MOCK_SUBMISSION_STATE}" == "missing" ]]; then
        printf '%s' '{"workflow_runs":[]}'
      else
        status="${MOCK_SUBMISSION_STATE%% *}"
        conclusion="${MOCK_SUBMISSION_STATE#* }"
        if [[ "${conclusion}" == "null" || "${conclusion}" == "${status}" ]]; then
          conclusion_json="null"
        else
          conclusion_json=$(printf '"%s"' "${conclusion}")
        fi
        printf '{"workflow_runs":[{"id":%s,"status":"%s","conclusion":%s,"display_title":"submission pr","head_sha":"%s"}]}' \
          "${MOCK_PARENT_ID}" "${status}" "${conclusion_json}" "${MOCK_HEAD}"
      fi
      ;;
    *dependency-submission-pr.yml/runs*)
      if [[ -z "${MOCK_CHILD_ROWS}" ]]; then
        printf '%s' '{"workflow_runs":[]}'
      else
        printf '{"workflow_runs":[%s]}' "${MOCK_CHILD_ROWS}"
      fi
      ;;
    */actions/runs/*/jobs)
      printf '%s' "${MOCK_CHILD_JOBS}"
      ;;
    *dependency-graph/compare/*)
      printf '%s' "${MOCK_COMPARE_HEADERS}"
      ;;
    *)
      fail "unexpected gh_api url: ${url}"
      ;;
  esac
}

WAIT_ATTEMPTS=0
wait_sleep_mock() {
  WAIT_ATTEMPTS=$((WAIT_ATTEMPTS + 1))
}

run_mock_wait() {
  WAIT_ATTEMPTS=0
  wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 5 0 5 0 1 0
}

# success: submission ok, child bound to parent, jobs ok, compare supplementary
pass "integration success"
MOCK_SUBMISSION_STATE="completed success"
marker=$(submission_pr_source_run_marker "${MOCK_PARENT_ID}")
MOCK_CHILD_ROWS=$(printf '{"id":%s,"conclusion":"success","status":"completed","display_title":"%s"}' "${MOCK_CHILD_ID}" "${marker}")
MOCK_CHILD_JOBS='{"jobs":[{"name":"Verify dependency graph artifact","conclusion":"success"},{"name":"Submit dependency graph (PR)","conclusion":"success"}]}'
run_mock_wait || fail "expected success path"

# failed submission phase 1
pass "integration failed submission"
MOCK_SUBMISSION_STATE="completed failure"
if run_mock_wait 2>/dev/null; then
  fail "failed submission should exit non-zero"
fi

# phase 1 timeout
pass "integration phase 1 timeout"
MOCK_SUBMISSION_STATE="in_progress null"
if wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 3 0 2 0 1 0 2>/dev/null; then
  fail "phase 1 timeout should fail"
fi

# phase 2 timeout (no child for marker)
pass "integration phase 2 timeout"
MOCK_SUBMISSION_STATE="completed success"
MOCK_CHILD_ROWS=""
if wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 2 0 3 0 1 0 2>/dev/null; then
  fail "phase 2 timeout should fail"
fi

# wrong run association: child exists but for a different parent id (filtered out by list)
pass "integration wrong run association"
MOCK_SUBMISSION_STATE="completed success"
MOCK_CHILD_ROWS='{"id":3003,"conclusion":"success","status":"completed","display_title":"dependency-submission-pr • source-run-999999"}'
MOCK_CHILD_JOBS='{"jobs":[{"name":"Verify dependency graph artifact","conclusion":"success"},{"name":"Submit dependency graph (PR)","conclusion":"success"}]}'
if wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 2 0 2 0 1 0 2>/dev/null; then
  fail "unrelated child run must not satisfy wait"
fi

# child failed for correct parent
pass "integration child failure"
MOCK_SUBMISSION_STATE="completed success"
MOCK_CHILD_ROWS=$(printf '{"id":%s,"conclusion":"failure","status":"completed","display_title":"%s"}' "4004" "$(submission_pr_source_run_marker "${MOCK_PARENT_ID}")")
if wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 2 0 2 0 1 0 2>/dev/null; then
  fail "failed child should exit 1"
fi

# compare-only must not satisfy wait (child/prerequisite missing)
pass "integration compare alone insufficient"
MOCK_COMPARE_HEADERS=$'HTTP/2.0 200 OK\nX-Github-Dependency-Graph-Snapshot-Warnings:\n'
MOCK_CHILD_ROWS=""
MOCK_SUBMISSION_STATE="in_progress null"
dependency_graph_compare_supplementary "${MOCK_REPO}" "${MOCK_BASE}" "${MOCK_HEAD}" || fail "mock compare should look ready"
if wait_for_pr_dependency_snapshot "${MOCK_REPO}" "${MOCK_HEAD}" "${MOCK_BASE}" 1 0 1 0 1 0 2>/dev/null; then
  fail "compare readiness without submission must not complete wait"
fi

echo "wait-for-pr-dependency-snapshot.test.sh: all passed"