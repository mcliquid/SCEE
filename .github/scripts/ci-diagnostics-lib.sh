#!/usr/bin/env bash
# Shared helpers for CI test diagnostics (artifact-safe paths).
# Sourced by ci-test-with-diagnostics.sh and ci-test-with-diagnostics.test.sh.

# UTC timestamp for log output (ISO-8601 with colons).
ci_diag_log_timestamp_utc() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

# UTC timestamp for artifact file names (no characters forbidden by upload-artifact / NTFS).
ci_diag_file_timestamp_utc() {
  date -u +%Y-%m-%dT%H-%M-%SZ
}

# True when $1 is safe for actions/upload-artifact paths on all platforms.
ci_diag_artifact_path_is_valid() {
  local path=$1
  [[ "$path" != *:* && "$path" != *\"* && "$path" != *"*"* && "$path" != *"?"* \
    && "$path" != *"|"* && "$path" != *"<"* && "$path" != *">"* ]]
}