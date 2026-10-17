# Updating the traffic sign catalogue

The catalogue of traffic signs (`app/src/commonMain/composeResources/files/traffic_signs/`: `DE.json`,
`DE/*.xml` images converted from SVG, `VERSION`) comes from the npm package
[`@osm-traffic-signs/converter`](https://www.npmjs.com/package/@osm-traffic-signs/converter)
(source: https://github.com/osmberlin/osm-traffic-sign-tool, data AGPL-3.0-or-later). The pinned
version and the SHA-256 of its tarball are in `app/build.gradle.kts` (`trafficSignsVersion`,
`trafficSignsSha256`) and in `traffic_signs/VERSION`. Nothing updates itself: the update is a manual
Gradle task, `:app:updateTrafficSigns` (the presets tasks `updatePresets`,
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

## What the task does
1. Reads the registry data of the version (`latest` is resolved), downloads the tarball and verifies it: the given SHA-256, or, for a new version without one, the integrity (sha512) of the registry.
2. Stages everything in memory: `DE.json`, every SVG converted to a vector drawable, `VERSION`. Validates: JSON structure and required fields, `kind`, empty or duplicate ids, redirects to nothing or contradicting each other, signs that would share an image name, signs without SVG, SVGs that cannot be converted, version of the file.
3. Compares with the existing files: signs added, removed, changed (and which fields), images changed, new or removed fields of the entries, changes of the signs of the curated rules.
4. Writes the report. Only with `-PtrafficSignsApply=true`: copies the previous files to `app\build\traffic-signs-update\backup`, writes only the files that differ and deletes the images that are gone. If writing fails, the previous files are restored. The history (`app\traffic-signs-update\history\<old>-to-<new>.md`) is written on success.
5. On any problem (download, hash, archive, invalid data) nothing is changed.

## Files that a update changes
`traffic_signs/DE.json`, `traffic_signs/DE/*.xml` (added, changed, removed), `traffic_signs/VERSION`, `app/traffic-signs-update/history/*.md`. By hand: `app/build.gradle.kts` (pin), `tools/traffic-sign-parity/` (`package.json`, `package-lock.json`, `reference.jsonl`), the expected values in `TrafficSignCatalogueTest` (version, number of signs), see the report. `TrafficSignCatalogueUpdateGuardTest` fails until the version, the build pin, `VERSION`, the parity reference and the tooling agree.

## Recognize new, changed and removed signs
In the report: `added`, `removed`, `changed` (sign: fields), `images changed`, and under "Structure" the added and removed fields. `git diff --stat` and `git diff -- app/src/commonMain/composeResources/files/traffic_signs/DE.json` show the same in the repository.

## Undo
Before applying: nothing to undo. After applying: `git checkout -- app/src/commonMain/composeResources/files/traffic_signs app/build.gradle.kts` (and delete the new history file), or copy the files from `app\build\traffic-signs-update\backup` back.

## Changes that need a manual review (`-PtrafficSignsAcceptReview=true`)
- the signs of the curated tag rules (`rule-signs.txt`, the 11 `WAY_RULES`): kind, signId, recommendations, compatibility, redirects, values, or removal; and redirects that start or end at them. **The rules themselves are in the code and are never changed by an update**; a new or changed catalogue entry cannot create tags (tested), but a changed rule sign may mean that a rule should be reviewed (`TrafficSignRuleCatalogueTest` compares the rules with the catalogue).
- a changed structure of the entries (fields added or removed).
- removed signs (existing values with them stay as unknown signs and are kept).
- changes of `kind`, `signId`, `redirects`, `redirectDashValues`, `signValue` of any sign: they change how existing values are read.
- always: after a new version, the converter parity (`reference.jsonl`) has to be regenerated and its differences read; a new version without that makes `TrafficSignCatalogueUpdateGuardTest` fail on purpose.
