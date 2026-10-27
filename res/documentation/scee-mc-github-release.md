# SCEE-mc: signierte Android-APKs über GitHub Releases

Anleitung für den privaten Fork **SCEE-mc** (`mcliquid/SCEE`): Signing-Secrets und
Release-Ablauf. Offizielle StreetComplete- bzw. Helium314/SCEE-Releases sind das nicht.

Eine **eigenständige** Android-Application-ID für SCEE-mc ist bewusst zurückgestellt.
Aktuelle Builds nutzen weiterhin die SCEE-Paket-ID und die bestehende SCEE-OAuth-
Konfiguration. Die Umstellung auf eine eigene Identität bleibt für später vorgesehen.

Lizenz: **GPL-3.0** (siehe [`LICENSE`](../../LICENSE)). Zu jedem veröffentlichten APK
gehört der passende Git-Tag/Commit als Quelltext.

## Aktuelle App-Identität

| Feld | Wert |
|---|---|
| `applicationId` | `de.westnordost.streetcomplete.expert` |
| Launcher-Name | `SCEE` |
| `created_by` / User-Agent | `StreetComplete_ee <versionName>` |
| OAuth | Upstream-SCEE-Client, Redirect `streetcomplete://oauth`, Scopes `read_prefs write_api write_notes write_gpx` |
| `versionName` | `app.streetcomplete.versionName` in `gradle.properties` |
| `versionCode` | `androidApp/build.gradle.kts` → `defaultConfig.versionCode` |
| Release-APK | eine universelle APK (ABIs: `armeabi-v7a`, `arm64-v8a`, `x86_64`) |

Build-Types (wie vor der Flavor-Umstellung): `debug`, `release`, `legacyDebug`, `perf`.

## Gradle-Tasks

`de.westnordost:osmfeatures:8.1.0-nsi.1` comes from `mavenLocal` (NSI v8 patches;
not on Maven Central). CI publishes it automatically; locally once:

```bash
./third_party/osmfeatures/publish-to-maven-local.sh
```

See [`third_party/osmfeatures/README.md`](../../third_party/osmfeatures/README.md).

```bash
./gradlew :androidApp:assembleRelease   # Distribution / GitHub Release
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleLegacyDebug
./gradlew :androidApp:assemblePerf
```

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

### Hinweis zu Signatur und Paket-ID

Gleiches `applicationId` wie F-Droid-/GitHub-SCEE mit **anderem** Signing-Key bedeutet:
kein In-Place-Update zwischen offiziellem SCEE und diesem Fork — Installation
erfordert Deinstallation der jeweils anderen Signatur. Das ist der aktuelle Stand,
bis eine eigene Application-ID eingeführt wird.

## Ablauf zukünftiger Releases

Voraussetzungen: Secrets gesetzt; `versionName` / `versionCode` angepasst und committed.

### A) Versionstag (Prerelease)

```bash
git tag v64.0-mc.1
git push origin v64.0-mc.1
```

Tags `v*` starten [`.github/workflows/build-signed-apk.yml`](../../.github/workflows/build-signed-apk.yml):

1. Prüfung, dass alle Signing-Secrets gesetzt sind (sonst Abbruch, keine unsignierte APK)
2. `:androidApp:assembleRelease` (signiert via injected AGP-Signing)
3. Umbenennung zu `SCEE-mc-<tag>.apk` + SHA-256-Datei
4. `apksigner verify` (Pflicht) und Paket-ID-Check `de.westnordost.streetcomplete.expert`
5. Workflow-Artifacts
6. GitHub **Prerelease** mit APK + `.sha256` (kein Debug-APK; erneute Läufe aktualisieren Assets mit `--clobber`)

### B) Manuell ohne Tag

Actions → **Build signed release APK** → Run workflow → nur Artifacts, kein Release.

### C) Lokal

```bash
./gradlew :androidApp:assembleRelease
```

Desktop und iOS werden von diesem Workflow nicht gebaut.

## Play App Signing (später)

Noch nicht aktiv. Bei späterer Play-Veröffentlichung: AAB, eigene Upload-Keys und
idealerweise eigene Application-ID. Dieser Workflow veröffentlicht nicht automatisch
auf Play.
