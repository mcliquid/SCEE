#!/usr/bin/env bash
# Shared helpers for waiting on PR Gradle dependency graph submission (safe to source).
set -euo pipefail

SUBMISSION_PR_SOURCE_RUN_MARKER_PREFIX="${SUBMISSION_PR_SOURCE_RUN_MARKER_PREFIX:-source-run-}"

_dgw_pick_python() {
  local probe out python3_path
  if [[ -n "${DGW_PYTHON:-}" ]]; then
    printf '%s' "${DGW_PYTHON}"
    return
  fi
  probe='{"ok":1}'
  if command -v python3 >/dev/null 2>&1; then
    python3_path="$(command -v python3)"
    case "${python3_path}" in
      *WindowsApps* | *windowsapps*) ;;
      *)
        out=$(printf '%s' "${probe}" | python3 -c 'import json,sys; print(json.load(sys.stdin)["ok"])' 2>/dev/null || true)
        if [[ "${out}" == "1" ]]; then
          printf 'python3'
          return
        fi
        ;;
    esac
  fi
  if command -v py >/dev/null 2>&1; then
    out=$(printf '%s' "${probe}" | py -3 -c 'import json,sys; print(json.load(sys.stdin)["ok"])' 2>/dev/null || true)
    if [[ "${out}" == "1" ]]; then
      printf 'py -3'
      return
    fi
  fi
  printf 'python3'
}

_dgw_python() {
  local runner
  runner="$(_dgw_pick_python)"
  if [[ "${runner}" == "py -3" ]]; then
    py -3 "$@"
  else
    python3 "$@"
  fi
}

submission_pr_source_run_marker() {
  printf '%s%s' "${SUBMISSION_PR_SOURCE_RUN_MARKER_PREFIX}" "${1:?parent workflow run id}"
}

# Returns 0 when the child workflow run display title is bound to the parent submission run id.
child_run_display_title_matches_parent() {
  local display_title="${1-}"
  local parent_run_id="${2:?parent run id}"
  local marker
  [[ -n "${display_title}" ]] || return 1
  marker=$(submission_pr_source_run_marker "${parent_run_id}")
  [[ "${display_title}" == *"${marker}"* ]]
}

# Parse one child workflow run JSON object (from list_submission_pr_children_for_parent).
child_workflow_run_fields() {
  local child_json="${1:?child json}"
  printf '%s' "${child_json}" | _dgw_python -c '
import json, sys
run = json.load(sys.stdin)
for key in ("id", "conclusion", "status", "display_title"):
    value = run.get(key)
    print("" if value is None else str(value))
'
}

# Returns 0 when both required jobs completed successfully (not skipped).
pr_submission_child_jobs_ok() {
  local jobs_json="${1:?jobs api json}"
  _dgw_python -c '
import json, sys
required = {"Verify dependency graph artifact", "Submit dependency graph (PR)"}
data = json.loads(sys.argv[1])
ok = {
    job.get("name")
    for job in data.get("jobs", [])
    if job.get("conclusion") == "success" and job.get("name") in required
}
sys.exit(0 if ok == required else 1)
' "${jobs_json}"
}

# Supplementary only — must not be used as the sole success signal.
dependency_graph_snapshot_ready_from_headers() {
  local headers="${1:?compare response headers}"
  local warnings trimmed
  headers=$(printf '%s\n' "${headers}" | tr -d '\r')
  warnings=$(printf '%s\n' "${headers}" | awk 'tolower($0) ~ /^x-github-dependency-graph-snapshot-warnings:/ { sub(/^[^:]+:[[:space:]]*/, ""); print; exit }')
  trimmed="${warnings//[[:space:]]/}"
  if [[ -z "${trimmed}" ]]; then
    return 0
  fi
  printf '%s' "${trimmed}"
  return 1
}

gh_api() {
  local args=("$@")
  local include_headers=false filtered=() arg
  for arg in "${args[@]}"; do
    case "${arg}" in
      -i) include_headers=true ;;
      *) filtered+=("${arg}") ;;
    esac
  done

  if declare -F gh_api_mock >/dev/null 2>&1; then
  # shellcheck disable=SC2091
    if [[ "${include_headers}" == true ]]; then
      gh_api_mock -i "${filtered[@]}"
    else
      gh_api_mock "${filtered[@]}"
    fi
  else
    gh api "${args[@]}"
  fi
}

fetch_dependency_compare_headers() {
  local repo="${1:?repository}"
  local base_sha="${2:?base sha}"
  local head_sha="${3:?head sha}"
  gh_api -i "repos/${repo}/dependency-graph/compare/${base_sha}...${head_sha}" 2>/dev/null | tr -d '\r' || true
}

dependency_graph_compare_supplementary() {
  local headers
  headers=$(fetch_dependency_compare_headers "${1:?repository}" "${2:?base sha}" "${3:?head sha}")
  dependency_graph_snapshot_ready_from_headers "${headers}"
}

# Returns via stdout: "<status> <conclusion-or-null>" for the newest pull_request submission run.
latest_submission_run_state_for_head() {
  local repo="${1:?repository}"
  local head_sha="${2:?head sha}"
  local json
  json=$(gh_api "repos/${repo}/actions/workflows/dependency-submission.yml/runs?head_sha=${head_sha}&event=pull_request&per_page=1")
  _dgw_python -c '
import json, sys
data = json.load(sys.stdin)
runs = data.get("workflow_runs") or []
if not runs:
    print("missing null")
else:
    run = runs[0]
    conclusion = run.get("conclusion")
    print(
        "{} {}".format(
            run.get("status"),
            conclusion if conclusion is not None else "null",
        )
    )
' <<<"${json}"
}

latest_submission_run_for_head() {
  local repo="${1:?repository}"
  local head_sha="${2:?head sha}"
  local json
  json=$(gh_api "repos/${repo}/actions/workflows/dependency-submission.yml/runs?head_sha=${head_sha}&event=pull_request&per_page=1")
  _dgw_python -c '
import json, sys
data = json.load(sys.stdin)
runs = data.get("workflow_runs") or []
print(json.dumps(runs[0] if runs else None))
' <<<"${json}"
}

workflow_run_id_from_json() {
  local run_json="${1:?run json}"
  _dgw_python -c 'import json,sys; d=json.loads(sys.argv[1]); print(d.get("id") or "")' "${run_json}"
}

# stdout: one JSON object per matching child run (newest API order).
list_submission_pr_children_for_parent() {
  local repo="${1:?repository}"
  local parent_run_id="${2:?parent run id}"
  local marker per_page page runs
  marker=$(submission_pr_source_run_marker "${parent_run_id}")
  per_page=30
  for page in 1 2 3; do
    runs=$(gh_api "repos/${repo}/actions/workflows/dependency-submission-pr.yml/runs?event=workflow_run&per_page=${per_page}&page=${page}" || true)
    MARKER="${marker}" _dgw_python -c '
import json, os, sys
marker = os.environ["MARKER"]
data = json.loads(sys.stdin.read())
for run in data.get("workflow_runs") or []:
    title = run.get("display_title") or ""
    if marker not in title:
        continue
    payload = {
        "id": run.get("id"),
        "conclusion": run.get("conclusion"),
        "status": run.get("status"),
        "display_title": title,
    }
    print(json.dumps(payload, ensure_ascii=False))
' <<<"${runs}"
    count=$(_dgw_python -c 'import json,sys; print(len(json.load(sys.stdin).get("workflow_runs") or []))' <<<"${runs}")
    if [[ "${count}" -lt "${per_page}" ]]; then
      break
    fi
  done
}

fetch_workflow_run_jobs() {
  local repo="${1:?repository}"
  local run_id="${2:?run id}"
  gh_api "repos/${repo}/actions/runs/${run_id}/jobs"
}

wait_sleep() {
  local seconds="${1:?seconds}"
  if declare -F wait_sleep_mock >/dev/null 2>&1; then
  # shellcheck disable=SC2091
    wait_sleep_mock "${seconds}"
  else
    sleep "${seconds}"
  fi
}

wait_for_pr_dependency_snapshot() {
  local repo="${1:?repository}"
  local head_sha="${2:?PR head sha}"
  local base_sha="${3:?PR base sha}"
  local generate_max_attempts="${4:-75}"
  local generate_sleep="${5:-16}"
  local child_max_attempts="${6:-30}"
  local child_sleep="${7:-10}"
  local compare_max_attempts="${8:-6}"
  local compare_sleep="${9:-5}"

  local attempt state submission_json parent_run_id

  echo "Phase 1: dependency-submission.yml (pull_request) for ${head_sha}"
  for attempt in $(seq 1 "${generate_max_attempts}"); do
    state=$(latest_submission_run_state_for_head "${repo}" "${head_sha}")
    echo "  attempt ${attempt}/${generate_max_attempts}: ${state}"
    case "${state}" in
      "completed success")
        submission_json=$(latest_submission_run_for_head "${repo}" "${head_sha}")
        parent_run_id=$(workflow_run_id_from_json "${submission_json}")
        if [[ -z "${parent_run_id}" ]]; then
          echo "Missing submission run id after success." >&2
          return 1
        fi
        echo "  submission run id: ${parent_run_id}"
        break
        ;;
      "completed "*)
        echo "Dependency graph generation failed; refusing dependency review." >&2
        return 1
        ;;
    esac
    if [[ "${attempt}" -eq "${generate_max_attempts}" ]]; then
      echo "Timed out waiting for dependency-submission (pull_request)." >&2
      return 1
    fi
    wait_sleep "${generate_sleep}"
  done

  echo "Phase 2: dependency-submission-pr.yml for source-run-${parent_run_id}"
  for attempt in $(seq 1 "${child_max_attempts}"); do
    local found=false child_json child_id child_conclusion child_status child_title
    local -a child_fields
    while IFS= read -r child_json || [[ -n "${child_json}" ]]; do
      [[ -z "${child_json}" ]] && continue
      child_fields=()
      while IFS= read -r line; do
        line="${line%$'\r'}"
        child_fields+=("${line}")
      done < <(child_workflow_run_fields "${child_json}")
      if [[ "${#child_fields[@]}" -ne 4 ]]; then
        echo "  skip: malformed child run payload" >&2
        continue
      fi
      child_id="${child_fields[0]}"
      child_conclusion="${child_fields[1]}"
      child_status="${child_fields[2]}"
      child_title="${child_fields[3]}"
      [[ -z "${child_id}" ]] && continue
      found=true
      echo "  attempt ${attempt}/${child_max_attempts}: child ${child_id} ${child_status} ${child_conclusion:-∅} (${child_title})"
      if ! child_run_display_title_matches_parent "${child_title}" "${parent_run_id}"; then
        echo "  skip: display_title does not match parent run id" >&2
        continue
      fi
      case "${child_status}" in
        queued | in_progress | waiting | requested | pending)
          continue 2
          ;;
      esac
      if [[ "${child_status}" == "completed" && -z "${child_conclusion}" ]]; then
        continue 2
      fi
      case "${child_conclusion}" in
        failure | cancelled | timed_out | action_required)
          echo "dependency-submission-pr failed for source-run-${parent_run_id} (run ${child_id})." >&2
          return 1
          ;;
        skipped | neutral)
          echo "dependency-submission-pr did not execute required jobs (run ${child_id}: ${child_conclusion})." >&2
          return 1
          ;;
        success)
          local jobs_json
          jobs_json=$(fetch_workflow_run_jobs "${repo}" "${child_id}")
          if ! pr_submission_child_jobs_ok "${jobs_json}"; then
            echo "dependency-submission-pr run ${child_id} missing successful verify/submit jobs." >&2
            return 1
          fi
          echo "  child run ${child_id} verified (jobs ok)."
          local compare_attempt headers warnings=""
          for compare_attempt in $(seq 1 "${compare_max_attempts}"); do
            headers=$(fetch_dependency_compare_headers "${repo}" "${base_sha}" "${head_sha}")
            if dependency_graph_snapshot_ready_from_headers "${headers}"; then
              echo "  supplementary compare: snapshot visible for ${head_sha}."
              echo "Dependency graph submission complete (parent ${parent_run_id}, child ${child_id})."
              return 0
            fi
            warnings=$(dependency_graph_snapshot_ready_from_headers "${headers}" || true)
            echo "  supplementary compare attempt ${compare_attempt}/${compare_max_attempts}: warnings='${warnings}'"
            if [[ "${compare_attempt}" -lt "${compare_max_attempts}" ]]; then
              wait_sleep "${compare_sleep}"
            fi
          done
          echo "::warning::dependency-submission-pr succeeded (run ${child_id}) but compare API has not caught up yet; proceeding."
          echo "Dependency graph submission complete (parent ${parent_run_id}, child ${child_id})."
          return 0
          ;;
        *)
          echo "  unknown child conclusion: ${child_conclusion}" >&2
          ;;
      esac
    done < <(list_submission_pr_children_for_parent "${repo}" "${parent_run_id}")

    if [[ "${found}" == "false" ]]; then
      echo "  attempt ${attempt}/${child_max_attempts}: no child run with marker source-run-${parent_run_id} yet"
    fi
    if [[ "${attempt}" -eq "${child_max_attempts}" ]]; then
      echo "Timed out waiting for dependency-submission-pr for source-run-${parent_run_id}." >&2
      return 1
    fi
    wait_sleep "${child_sleep}"
  done
}