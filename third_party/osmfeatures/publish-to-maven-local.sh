#!/usr/bin/env bash
# Build the pinned osmfeatures NSI v8 patch series and publish it to mavenLocal.
# Used by GitHub Actions and for a one-time local setup on developer machines.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PATCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/patches"
BASE_SHA="30186418b8d20a6c14d5d4823fec1162e566360d"
VERSION="8.1.0-nsi.1"
REPO_URL="https://github.com/westnordost/osmfeatures.git"
WORKDIR="${OSMFEATURES_BUILD_DIR:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}/osmfeatures-scee-mc-${VERSION}}"

echo "Publishing de.westnordost:osmfeatures:${VERSION} to mavenLocal"
echo "  base:    ${BASE_SHA}"
echo "  workdir: ${WORKDIR}"

rm -rf "${WORKDIR}"
mkdir -p "${WORKDIR}"
git clone --quiet "${REPO_URL}" "${WORKDIR}"
git -C "${WORKDIR}" checkout --quiet "${BASE_SHA}"

shopt -s nullglob
patches=("${PATCH_DIR}"/*.patch)
if [[ "${#patches[@]}" -eq 0 ]]; then
  echo "No patches found in ${PATCH_DIR}" >&2
  exit 1
fi
for patch in "${patches[@]}"; do
  git -C "${WORKDIR}" am --quiet "${patch}"
done

python3 - <<'PY' "${WORKDIR}/build.gradle.kts" "${VERSION}"
from pathlib import Path
import re
import sys
path = Path(sys.argv[1])
version = sys.argv[2]
text = path.read_text(encoding="utf-8")
text, n = re.subn(r'version\s*=\s*"[^"]+"', f'version = "{version}"', text, count=1)
if n != 1:
    raise SystemExit("failed to rewrite osmfeatures version")
# Local/CI publish only — do not require Maven Central credentials or GPG signing.
text = re.sub(r'(?m)^\s*publishToMavenCentral\(\)\s*\n', "", text)
text = re.sub(r'(?m)^\s*signAllPublications\(\)\s*\n', "", text)
path.write_text(text, encoding="utf-8")
PY

(
  cd "${WORKDIR}"
  chmod +x gradlew
  ./gradlew --no-daemon publishToMavenLocal
)

echo "Published de.westnordost:osmfeatures:${VERSION} (and platform variants) to mavenLocal"
