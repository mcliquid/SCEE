#!/usr/bin/env bash
# Coordinated catalogue refresh for Renovate @osm-traffic-signs/converter PRs.
# Invoked only from renovate-traffic-signs-sha256.yml; trusted script copy lives on the PR base SHA.
set -euo pipefail

REPO_ROOT="${REPO_ROOT:?REPO_ROOT required}"
TRUSTED_DIR="${TRUSTED_DIR:?TRUSTED_DIR required}"
RUNNER_TEMP="${RUNNER_TEMP:-/tmp}"
export REPO_ROOT TRUSTED_DIR RUNNER_TEMP

cd "$REPO_ROOT"

cp app/build.gradle.kts "${RUNNER_TEMP}/build.gradle.before-pin"
export GRADLE_PATH="$REPO_ROOT/app/build.gradle.kts"
node "$TRUSTED_DIR/.github/scripts/pin-traffic-signs-sha256.mjs"

node --input-type=module --eval "
  import fs from 'node:fs';
  import path from 'node:path';
  import { pathToFileURL } from 'node:url';
  const trusted = path.resolve(process.env.TRUSTED_DIR);
  const repo = path.resolve(process.env.REPO_ROOT);
  const pinUrl = pathToFileURL(path.join(trusted, '.github/scripts/pin-traffic-signs-sha256.mjs')).href;
  const { assertOnlySha256PinChange } = await import(pinUrl);
  assertOnlySha256PinChange(
    fs.readFileSync(path.join(process.env.RUNNER_TEMP, 'build.gradle.before-pin'), 'utf8'),
    fs.readFileSync(path.join(repo, 'app/build.gradle.kts'), 'utf8'),
  );
"

read_version() {
  node --input-type=module --eval "
    import fs from 'node:fs';
    import path from 'node:path';
    import { pathToFileURL } from 'node:url';
    const trusted = path.resolve(process.env.TRUSTED_DIR);
    const repo = path.resolve(process.env.REPO_ROOT);
    const pinUrl = pathToFileURL(path.join(trusted, '.github/scripts/pin-traffic-signs-sha256.mjs')).href;
    const { readPins } = await import(pinUrl);
    const field = process.argv[1];
    console.log(readPins(fs.readFileSync(path.join(repo, 'app/build.gradle.kts'), 'utf8'))[field]);
  " "$1"
}

VERSION="$(read_version version)"
SHA="$(read_version sha256)"
if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "traffic_sign_version=$VERSION"
    echo "traffic_sign_sha256=$SHA"
  } >>"$GITHUB_OUTPUT"
fi

echo "Running updateTrafficSigns dry-run for $VERSION ..."
DRY_LOG="${RUNNER_TEMP}/updateTrafficSigns-dry-run.log"
./gradlew :app:updateTrafficSigns --no-daemon --console=plain 2>&1 | tee "$DRY_LOG"

REPORT_DIR="$REPO_ROOT/app/build/traffic-signs-update"
export REPORT_DIR
REPORT_FILE="$(node --input-type=module --eval "
  import path from 'node:path';
  import { pathToFileURL } from 'node:url';
  const trusted = path.resolve(process.env.TRUSTED_DIR);
  const libUrl = pathToFileURL(path.join(trusted, '.github/scripts/traffic-sign-renovate-lib.mjs')).href;
  const { findLatestUpdateReport } = await import(libUrl);
  const p = findLatestUpdateReport(process.env.REPORT_DIR);
  if (!p) process.exit(2);
  console.log(p);
")"
export REPORT_FILE

node --input-type=module --eval "
  import fs from 'node:fs';
  import path from 'node:path';
  import { pathToFileURL } from 'node:url';
  const trusted = path.resolve(process.env.TRUSTED_DIR);
  const libUrl = pathToFileURL(path.join(trusted, '.github/scripts/traffic-sign-renovate-lib.mjs')).href;
  const { parseReportNeedsReview } = await import(libUrl);
  const md = fs.readFileSync(process.env.REPORT_FILE, 'utf8');
  const { needsReview, reasons } = parseReportNeedsReview(md);
  if (!needsReview) process.exit(0);
  console.error('Manual review required (refusing -PtrafficSignsAcceptReview=true):');
  for (const line of reasons) console.error('  - ' + line);
  process.exit(2);
"

HAS_CATALOGUE_CHANGES="$(node --input-type=module --eval "
  import fs from 'node:fs';
  import path from 'node:path';
  import { pathToFileURL } from 'node:url';
  const trusted = path.resolve(process.env.TRUSTED_DIR);
  const libUrl = pathToFileURL(path.join(trusted, '.github/scripts/traffic-sign-renovate-lib.mjs')).href;
  const { shouldApplyCatalogueUpdate } = await import(libUrl);
  const dry = fs.readFileSync('${DRY_LOG}', 'utf8');
  const report = fs.readFileSync(process.env.REPORT_FILE, 'utf8');
  console.log(shouldApplyCatalogueUpdate(dry, report) ? 'true' : 'false');
")"

if [ "$HAS_CATALOGUE_CHANGES" = "true" ]; then
  echo "Applying updateTrafficSigns for $VERSION ..."
  ./gradlew :app:updateTrafficSigns -PtrafficSignsApply=true --no-daemon --console=plain
else
  echo "Catalogue file set already matches converter $VERSION; skipping apply."
fi

cd "$REPO_ROOT/tools/traffic-sign-parity"
export NPM_CONFIG_IGNORE_SCRIPTS=true
npm install --ignore-scripts --save-exact "@osm-traffic-signs/converter@${VERSION}"
npm ci --ignore-scripts
node reference.mjs
node reference.mjs --check

cd "$REPO_ROOT"
collect_changed_paths() {
  git diff --name-only
  git ls-files --others --exclude-standard
}

PATHS="$(collect_changed_paths | sort -u | grep -v '^$' || true)"
if [ -z "$PATHS" ]; then
  echo "No working tree changes after refresh pipeline."
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "has_tree_changes=false" >>"$GITHUB_OUTPUT"
  fi
  exit 0
fi

export CHANGED_PATHS="$PATHS"
node --input-type=module --eval "
  import path from 'node:path';
  import { pathToFileURL } from 'node:url';
  const trusted = path.resolve(process.env.TRUSTED_DIR);
  const libUrl = pathToFileURL(path.join(trusted, '.github/scripts/traffic-sign-renovate-lib.mjs')).href;
  const { assertBotCommitAllowlist } = await import(libUrl);
  const paths = process.env.CHANGED_PATHS.trim().split(/\\n+/).filter(Boolean);
  assertBotCommitAllowlist(paths);
"

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  echo "has_tree_changes=true" >>"$GITHUB_OUTPUT"
fi
echo "Refresh produced $(echo \"$PATHS\" | wc -l | tr -d ' ') path(s)."