# SCEE-mc: signierte Android-APKs über GitHub Releases

Anleitung für den privaten Fork **SCEE-mc** (`mcliquid/SCEE`): Signing-Secrets und
Release-Ablauf. Offizielle StreetComplete- bzw. Helium314/SCEE-Releases sind das nicht.

Lizenz: **GPL-3.0** (siehe [`LICENSE`](../../LICENSE)). Zu jedem veröffentlichten APK
gehört der passende Git-Tag/Commit als Quelltext.

## Aktuelle App-Identität

| Feld | Wert |
|---|---|
| `applicationId` | `de.westnordost.streetcomplete.expert.mc` |
| Launcher-Name | `SCEE-mc` |
| `created_by` / User-Agent | `SCEE-mc <versionName>` |
| OAuth | Eigene OSM-App, Redirect `sceemc://oauth`, Scopes `read_prefs write_api write_notes write_gpx` |
| `versionName` | `app.streetcomplete.versionName` in `gradle.properties` |
| `versionCode` | `androidApp/build.gradle.kts` → `defaultConfig.versionCode` |
| Release-APK | eine universelle APK (ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64`) |
| Parallel-Installation | ja, neben StreetComplete und SCEE |

Build-Types: `debug` (`…expert.mc.debug`), `release`, `legacyDebug`, `perf`.
Release: `isDebuggable = false`, `APPLICATION_DEBUG = false`.

FileProvider- und Startup-Authorities nutzen `${applicationId}` und kollidieren
nicht mit SCEE/StreetComplete.

## Gradle-Tasks

`de.westnordost:osmfeatures:8.1.0-nsi.1` kommt aus `mavenLocal` (NSI-v8-Patches;
nicht auf Maven Central). CI published automatisch; lokal einmal:

```bash
./third_party/osmfeatures/publish-to-maven-local.sh
```

Siehe [`third_party/osmfeatures/README.md`](../../third_party/osmfeatures/README.md).

```bash
./gradlew :androidApp:assembleRelease   # Distribution / GitHub Release
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleLegacyDebug
./gradlew :androidApp:assemblePerf
```

## OAuth-Client (SCEE-mc)

1. OSM-Anwendung unter https://www.openstreetmap.org/oauth2/applications anlegen.
2. Redirect URI exakt: `sceemc://oauth`
3. Scopes: `read_prefs`, `write_api`, `write_notes`, `write_gpx`
4. Client-IDs **nicht** committen. Lokal in `~/.gradle/gradle.properties`:

   ```properties
   app.sceemc.oauth2ClientId=...
   app.sceemc.oauth2ClientIdTest=...
   ```

5. GitHub Actions Secrets (zusätzlich zu den Signing-Secrets):

   | Secret | Inhalt |
   |---|---|
   | `sceemc_oauth2_client_id` | Live-Client-ID |
   | `sceemc_oauth2_client_id_test` | optional, Test-API-Client-ID |

Desktop behält die Upstream-SCEE-OAuth-Defaults (`streetcomplete://oauth`).

## Einmalige Einrichtung der Signing-Secrets

1. Keystore lokal erzeugen (Beispiel):

   ```bash
   keytool -genkeypair -v \
     -keystore scee-mc-release.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias scee-mc
   ```

2. Keystore **niemals** committen. Lokal optional `keystore.properties` im Repo-Root
   (gitignored):

   ```properties
   storeFile=../path/to/scee-mc-release.jks
   storePassword=***
   keyAlias=scee-mc
   keyPassword=***
   ```

3. GitHub **Settings → Secrets and variables → Actions**:

   | Secret | Inhalt |
   |---|---|
   | `keystore` | Base64 des Keystore-Files |
   | `keystore_password` | Store-Passwort |
   | `key_alias` | z. B. `scee-mc` |
   | `key_password` | Key-Passwort |

   Encode (Linux/macOS): `base64 -w0 scee-mc-release.jks`  
   PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes('scee-mc-release.jks'))`

4. Keystore-Backup offline verwahren.

Der Workflow schreibt den Keystore nur auf dem Runner und löscht ihn am Ende.
Derselbe Release-Keystore wie bisher wird weiterverwendet (Update-Pfad für die
**neue** Package-ID ab dem ersten Tag mit `…expert.mc`).

## Migration von älteren SCEE-mc-APKs

Frühere Prereleases (z. B. **`v64.0-mc.3`**) nutzten noch
`de.westnordost.streetcomplete.expert` und können **nicht** in-place auf
`…expert.mc` aktualisiert werden:

- Android behandelt das als andere App → Parallelinstallation möglich.
- App-Daten/Login der alten Package-ID werden **nicht** migriert.
- `v64.0-mc.3` und ältere Tags/Releases nicht nachträglich umschreiben.

## Ablauf zukünftiger Releases

Voraussetzungen: Signing- und OAuth-Secrets gesetzt; `versionName` / `versionCode`
angepasst und committed.

### A) Versionstag (Prerelease)

```bash
git tag v64.0-mc.4
git push origin v64.0-mc.4
```

Tags `v*` starten [`.github/workflows/build-signed-apk.yml`](../../.github/workflows/build-signed-apk.yml):

1. Prüfung, dass alle Signing-Secrets gesetzt sind (sonst Abbruch, keine unsignierte APK)
2. Publish von `osmfeatures:8.1.0-nsi.1` nach `mavenLocal`
3. `:androidApp:assembleRelease` (signiert via injected AGP-Signing, OAuth-Client aus Secret)
4. Umbenennung zu `SCEE-mc-<tag>.apk` + SHA-256-Datei
5. `apksigner verify` (Pflicht) und Paket-ID-Check `de.westnordost.streetcomplete.expert.mc`
6. Workflow-Artifacts
7. GitHub **Prerelease** mit APK + `.sha256` (erneute Läufe aktualisieren Assets mit `--clobber`)

### B) Manuell ohne Tag

Actions → **Build signed release APK** → Run workflow → nur Artifacts, kein Release.

### C) Lokal

```bash
./third_party/osmfeatures/publish-to-maven-local.sh
./gradlew :androidApp:assembleRelease
```

Desktop und iOS werden von diesem Workflow nicht gebaut.

## Play App Signing (später)

Noch nicht aktiv. Bei späterer Play-Veröffentlichung: AAB und Upload-Keys.
Dieser Workflow veröffentlicht nicht automatisch auf Play.
