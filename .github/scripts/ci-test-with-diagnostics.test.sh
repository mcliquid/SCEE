#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=ci-diagnostics-lib.sh
source "${ROOT}/ci-diagnostics-lib.sh"

fail() {
  echo "fail: $*" >&2
  exit 1
}

pass() {
  echo "ok: $*"
}

pass "file timestamp has no colons"
file_ts="$(ci_diag_file_timestamp_utc)"
[[ "$file_ts" == *-* ]] || fail "expected hyphenated date in file timestamp"
[[ "$file_ts" != *:* ]] || fail "file timestamp must not contain colons: $file_ts"

pass "log timestamp keeps ISO-8601 colons"
log_ts="$(ci_diag_log_timestamp_utc)"
[[ "$log_ts" == *:*:* ]] || fail "expected colons in log timestamp: $log_ts"

pass "artifact path validation"
sample="build/ci-diagnostics/suite/thread-dump-${file_ts}-elapsed-600s.txt"
ci_diag_artifact_path_is_valid "${sample}" || fail "valid path rejected"
ci_diag_artifact_path_is_valid "build/ci-diagnostics/bad/thread-dump-2026-11-21T08:13:33Z-elapsed-600s.txt" && fail "colon path must be rejected"

pass "diagnostics script sources lib"
grep -q 'ci-diagnostics-lib.sh' "${ROOT}/ci-test-with-diagnostics.sh" || fail "main script must source ci-diagnostics-lib.sh"

echo "All ci-test-with-diagnostics tests passed."