#!/usr/bin/env bash
# Waits until the Gradle dependency graph for a PR head is generated and submitted.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=dependency-snapshot-wait-lib.sh
source "${ROOT}/dependency-snapshot-wait-lib.sh"

wait_for_pr_dependency_snapshot \
  "${1:?repository (owner/name)}" \
  "${2:?PR head sha}" \
  "${3:?PR base sha}" \
  "${4:-75}" \
  "${5:-16}" \
  "${6:-30}" \
  "${7:-10}" \
  "${8:-6}" \
  "${9:-5}" || exit 1