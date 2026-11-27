# Updating the traffic sign catalogues

The catalogues of traffic signs (`app/src/commonMain/composeResources/files/traffic_signs/`: one
`{CC}.json` and `{CC}/*.xml` (images converted from SVG) per country, the index `catalogues.json`,
`VERSION`) come from the npm package
[`@osm-traffic-signs/converter`](https://www.npmjs.com/package/@osm-traffic-signs/converter)
(source: https://github.com/osmberlin/osm-traffic-sign-tool, data AGPL-3.0-or-later). The pinned
version and the SHA-256 of its tarball are in `app/build.gradle.kts` (`trafficSignsVersion`,
`trafficSignsSha256`) and in `traffic_signs/VERSION`. Local updates use the Gradle task
`:app:updateTrafficSigns` (the presets tasks `updatePresets`,
`downloadAndConvertPresetIcons` and `generateTagSuggestions` are for other data and not involved).

## Commands (PowerShell, from the repository root)

```powershell
# 1. Look: download, verify, validate, compare. Writes only a report, changes nothing.
.\gradlew.bat :app:updateTrafficSigns -PtrafficSignsVersion=latest      # or a version like 0.9.0
#    prints the SHA-256 to pin and the path of the report (app\build\traffic-signs-update\traffic-signs-update-<version>.md)

# 2. Read the report. Then apply, with the hash that was printed:
.\gradlew.bat :app:updateTrafficSigns -PtrafficSignsVersion=0.9.0 -PtrafficSignsSha256=<sha256> -PtrafficSignsApply=true
#    changes that need a manual review (below) are refused until you add -PtrafficSignsAcceptReview=true

# 3. Pin the version: edit trafficSignsVersion / trafficSignsSha256 in app\build.gradle.kts, then the follow-ups:
cd tools\traffic-sign-parity
npm install --save-exact "@osm-traffic-signs/converter@0.9.0"
node reference.mjs            # rewrites reference.jsonl: read the differences, do not just accept them
cd ..\..
.\gradlew.bat -p buildSrc test
.\gradlew.bat :app:testAndroidHostTest
```
Without parameters, `.\gradlew.bat :app:updateTrafficSigns` checks the pinned version against the files
(it reports "identical"; running it repeatedly changes nothing).

## Renovate automation (GitHub Actions)

Renovate bumps `trafficSignsVersion` in `app/build.gradle.kts` (custom regex manager). The workflow
[`.github/workflows/renovate-traffic-signs-sha256.yml`](../.github/workflows/renovate-traffic-signs-sha256.yml)
runs **only** for Renovate PRs to `SCEE-mc` when `trafficSignsVersion` changes (not for NSI/preset
bumps in the same file).

**Automatic steps** (trusted orchestration scripts from the PR **base** SHA; generation uses
`contents: read`, commit uses `contents: write`):

1. Tarball SHA-256 pin (`trafficSignsSha256` in `build.gradle.kts`)
2. `:app:updateTrafficSigns` dry-run and report check
3. Apply catalogue update when the report has file changes (`-PtrafficSignsApply=true`)
4. `tools/traffic-sign-parity`: exact npm version, `reference.mjs`, `reference.mjs --check`
5. One bot commit on the Renovate branch (allowlisted paths only)

Renovate does **not** manage `@osm-traffic-signs/converter` in `tools/traffic-sign-parity/package.json`
(anymore); the workflow keeps parity tooling in sync with the Gradle pin.

**Security model**

- Workflow logic under `.github/scripts/` is checked out from the **merge base** of the PR, not from
  the Renovate branch.
- The generation job uses `persist-credentials: false`; only the commit job pushes with `GITHUB_TOKEN`.
- Tarball SHA-256 verification stays in Gradle / `pin-traffic-signs-sha256.mjs` (HTTPS registry only).
- npm runs with `NPM_CONFIG_IGNORE_SCRIPTS=true` / `--ignore-scripts`.
- Bot commits may touch only: `app/build.gradle.kts`, `app/src/commonMain/composeResources/files/traffic_signs/**`,
  `app/traffic-signs-update/history/**`, `tools/traffic-sign-parity/package.json`, `package-lock.json`,
  `reference.jsonl`. Never `app/src/commonTest/**`.
- Before push, the workflow checks that the PR head SHA has not moved (no overwrite of concurrent edits).
- **Never** uses `-PtrafficSignsAcceptReview=true` automatically.

Gradle and `reference.mjs` are project tooling executed with repo permissions; their logic is not
replaced by the workflow scripts—they run as documented below.

**Manual review required**

If the dry-run report contains **“Needs a manual review”**, the workflow **fails**, uploads the
report artifact, and **does not** commit. Finish the update locally (read the report, apply with
`-PtrafficSignsAcceptReview=true` only after human review, regenerate parity, adjust tests, open your
own PR).

**Windows checkout note (local parity only)**

`DE.json` and most catalogue JSON files use `* text=auto` without a fixed `eol=lf` (unlike
`traffic_signs/VERSION` and `reference.jsonl`). With `core.autocrlf=true`, the working copy can
differ in byte length from `git show` and from `reference.mjs`’s npm package comparison even when
the committed blob is correct. Ubuntu CI and refresh workflows use LF checkouts; use `git show
HEAD:…/DE.json` or run `reference.mjs --check` on Linux when validating locally on Windows.

**Manual steps after a successful bot refresh**

- Update **deliberate** expected counts/strings in `TrafficSignCatalogueTest` and
  `TrafficSignCatalogueRobustnessTest` when sign totals change (the bot does **not** edit tests).
- PR unit tests (`Gradle unit tests`) must pass after those edits; a failing catalogue count test is
  intentional until you adjust expectations.
- Read `reference.jsonl` diff when sign coverage grows (new parity rows for new DE signs/combinations).
- Device smoke-check for new graphics in the picker when signs were added.

**Idempotency**

While the PR is open, SCEE-mc may still advertise the **previous** converter version (e.g. 0.8.0)
while the Renovate branch already targets **0.8.1** — comparing versions alone would re-run the
pipeline forever. The workflow therefore runs the coordinated refresh **only** when the PR file list
is the initial Renovate bump (`app/build.gradle.kts` only). After the bot commit (catalogue + parity
paths on the PR), further `synchronize` events **skip** generate/commit (no Apply, no artifact, no
second bot commit). The shell refresh script itself still skips Apply when the dry-run log contains
`(identical to the existing files)`.

**After a failed refresh**

Fix the Renovate branch locally or close the PR; do not merge a branch that failed the coordinated
refresh or still has mismatched pins/catalogue/parity. Re-push to the Renovate branch only after
Renovate rebases or you follow the manual commands above.

**CI after the bot commit (GitHub policy, same repository)**

Per [GitHub Docs — Triggering a workflow](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow) and [Events that trigger workflows — `pull_request`](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows):

- A push with `GITHUB_TOKEN` does **not** trigger `push` workflows (e.g. unit tests on `SCEE-mc`).
- Updating the PR branch **does** enqueue `pull_request` runs with activity `synchronize`, but they often start in **approval-required** state until someone with **write** access approves “Run workflows” on the PR (fork rules differ; this repo uses same-repo Renovate branches).
- Other `pull_request` activity types (`labeled`, `edited`, …) do not start runs.

So after the bot commit, unit tests / CodeQL / dependency review on the new head are expected but may need **manual workflow approval** once per push. No PAT is used by this automation. Settings that affect this: Actions enabled, fork PR approval policy (not applicable to same-repo Renovate), and org rules for required reviewers.

`gitIgnoredAuthors` in `renovate.json` only affects Renovate rebases, not GitHub’s workflow approval rules.

## What the task does
1. Reads the registry data of the version (`latest` is resolved), downloads the tarball and verifies it: the given SHA-256, or, for a new version without one, the integrity (sha512) of the registry.
2. Finds all catalogues in the package (`dist/data/{CC}.json`). **There is no list of countries**: a country that is added to the package is imported by the next update, without any change of code or configuration.
3. Stages everything in memory per country: `{CC}.json` (unchanged), the images, the index, `VERSION`. Validates the catalogue as a whole (JSON, country, version, list of signs, redirects to nothing or contradicting each other) and each sign. Only the signs are imported that
   - can be read (required fields, known `kind`, no duplicate id), otherwise `invalid_entry`,
   - have an id that the app reads back as exactly this sign: no `,` `;` `+`, no country prefix, no named value like `none`, only letters, digits, spaces, `.` `-` `_` `/` and one value in brackets at the end, or a free text in quotation marks (`osmValuePartProblem`, the same rule in buildSrc and the app); otherwise `unsupported_id` (e.g. `BE:B1+M1`, `PL:B-9,T-22`),
   - do not share an image name with another sign (`image_name_collision`),
   - have an SVG (`no_image`) with a licence (`no_image_licence`) that Svg2Vector converts without error (`defective_image`; warnings are accepted and listed).
   A sign that is left out never stops the import of its country. A new country that can not be read at all is reported and left out; an existing one stops the update.
4. Compares with the existing files per country: signs added, removed, changed (and which fields), images changed, new or removed fields of the entries, changes of the signs of the curated rules, signs that were imported and are not anymore.
5. Writes the report with a table of imported, skipped (no image, no licence, unsupported id) and erroneous (invalid entry, defective image, image name collision) signs per country, and the list of each with the reason. Only with `-PtrafficSignsApply=true`: copies the previous files to `app\build\traffic-signs-update\backup`, writes only the files that differ and deletes the images that are gone. If writing fails, the previous files are restored. The history (`app\traffic-signs-update\history\<old>-to-<new>.md`) is written on success.
6. On any problem (download, hash, archive, a broken existing catalogue) nothing is changed.

The import is deterministic: the same package gives the same files (countries and signs sorted), and running it again changes nothing.

## The index `catalogues.json`
Written by the task, read by the app (`TrafficSignCatalogues`): per imported country the number of signs in the catalogue, imported, skipped and erroneous, the licences of the images and the signs that are left out with the reason. The app offers the editor for exactly the countries in it, and leaves out the listed signs. The catalogues themselves are loaded only when a way or node of that country is edited (no network, nothing at app start).

## Countries and derived tags
The traffic sign editor exists for every imported country. Other tags than `traffic_sign*` are derived only for the countries with curated rules (`WAY_RULES` in `TrafficSignWayTagging.kt`, so far only `DE`); the tag recommendations of the catalogues are never applied. Rules for another country can be added there (and their signs to `rule-signs.txt` as `CC:sign`) without changing the import. Additional signs can only be combined with main signs if the catalogue says which ones fit (`compatibility`, so far only in `DE`).

## Files that a update changes
`traffic_signs/{CC}.json`, `traffic_signs/{CC}/*.xml` (added, changed, removed), `traffic_signs/catalogues.json`, `traffic_signs/VERSION`, `app/traffic-signs-update/history/*.md`. By hand: `app/build.gradle.kts` (pin), `tools/traffic-sign-parity/` (`package.json`, `package-lock.json`, `reference.jsonl`), the expected values in `TrafficSignCatalogueTest` (version, number of signs), see the report. `TrafficSignCatalogueUpdateGuardTest` fails until the version, the build pin, `VERSION`, the parity reference and the tooling agree.

## Recognize new, changed and removed signs
In the report: `added`, `removed`, `changed` (sign: fields), `images changed`, and under "Structure" the added and removed fields. `git diff --stat` and `git diff -- app/src/commonMain/composeResources/files/traffic_signs/DE.json` show the same in the repository.

## Undo
Before applying: nothing to undo. After applying: `git checkout -- app/src/commonMain/composeResources/files/traffic_signs app/build.gradle.kts` (and delete the new history file), or copy the files from `app\build\traffic-signs-update\backup` back.

## Changes that need a manual review (`-PtrafficSignsAcceptReview=true`)
- the signs of the curated tag rules (`rule-signs.txt`, one `CC:sign` per line, the 11 `WAY_RULES` of `DE`), also if they are not imported anymore: kind, signId, recommendations, compatibility, redirects, values, or removal; and redirects that start or end at them. **The rules themselves are in the code and are never changed by an update**; a new or changed catalogue entry cannot create tags (tested), but a changed rule sign may mean that a rule should be reviewed (`TrafficSignRuleCatalogueTest` compares the rules with the catalogue).
- a changed structure of the entries (fields added or removed).
- removed signs, and signs that were imported before and are not anymore (e.g. their image is missing now): existing values with them stay as unknown signs and are kept.
- a country that is in the existing files but not in the package anymore.
- changes of `kind`, `signId`, `redirects`, `redirectDashValues`, `signValue` of any sign: they change how existing values are read.
- always: after a new version, the converter parity (`reference.jsonl`) has to be regenerated and its differences read; a new version without that makes `TrafficSignCatalogueUpdateGuardTest` fail on purpose.
