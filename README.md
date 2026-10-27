# SCEE-mc

**SCEE-mc** is an OpenStreetMap editor for experienced contributors.
It is a private fork based on [StreetComplete](https://github.com/streetcomplete/StreetComplete)
and [SCEE](https://github.com/Helium314/SCEE), with additional features and a
Kotlin Multiplatform / Desktop codebase maintained on branch `SCEE-mc`.

It is **not** an official StreetComplete or SCEE release. Advanced editing features
can damage map data if used carelessly—read warnings and prefer StreetComplete if
you are new to OSM.

## Downloads

Signed Android builds are published as GitHub **prereleases**:

- Application id: `de.westnordost.streetcomplete.expert.mc` (installs **alongside** StreetComplete and SCEE)
- Launcher name: **SCEE-mc**
- **GitHub Releases (prereleases):** once tags are published,
  [mcliquid/SCEE releases](https://github.com/mcliquid/SCEE/releases)
- Build and signing notes: [res/documentation/scee-mc-github-release.md](res/documentation/scee-mc-github-release.md)

```bash
./gradlew :androidApp:assembleRelease   # signed distribution APK (needs keystore + OAuth client id)
./gradlew :androidApp:assembleDebug     # …expert.mc.debug
```

## Features

### Inherited from StreetComplete / SCEE

- Quest-based surveying with offline map data and upload to OpenStreetMap
- Overlays, notes, achievements and expert-mode tagging tools from SCEE
- Large set of SCEE-only quests (enabled via expert mode / quest settings)
- Custom overlays and external-source quests (e.g. Osmose, custom CSV)

### SCEE-mc-oriented additions and platform work

Verified in this repository’s current code (availability may depend on settings):

- **Kotlin Multiplatform** shared core (`:app`) with Android, Desktop and iOS targets
- **Windows Desktop** launcher (`:desktopApp`) for development and experimentation
- **Photon** location search (`PhotonSearchClient`)
- **Pedestrian routing** integration on the main map (`PedestrianRoutingClient`)
- **Aerial imagery** with Editor Layer Index–based source picking (SCEE-mc work)
- **Continuous / automatic map-data download** options (including viewport-based continuous download when enabled)
- **Traffic sign** overlay and related quests
- Further specialized quests and usability tweaks maintained on branch `SCEE-mc`

Treat anything not listed here as inherited upstream behaviour unless documented
elsewhere in the repo.

## Android and Desktop

Android ships as an independent app (`de.westnordost.streetcomplete.expert.mc`) with
OAuth redirect `sceemc://oauth` and its own OSM application client id. Build types
include `debug`, `release`, `legacyDebug` and `perf`.

Desktop builds share the multiplatform core and keep the upstream SCEE OAuth defaults;
they are not the primary distribution channel.

## Origins, credits and license

SCEE-mc stands on the work of:

- [StreetComplete](https://github.com/streetcomplete/StreetComplete) and its contributors
- [SCEE (StreetComplete_ee)](https://github.com/Helium314/SCEE) by Helium314 and contributors
- OpenStreetMap contributors and the many libraries listed in the Gradle version catalog

This software is released under the [GNU General Public License v3](LICENSE).

Upstream StreetComplete documentation is retained as
[README_StreetComplete.md](README_StreetComplete.md) for historical reference.
Further signing / GitHub Release detail:
[res/documentation/scee-mc-github-release.md](res/documentation/scee-mc-github-release.md).
