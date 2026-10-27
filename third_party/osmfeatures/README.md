# osmfeatures (NSI v8) for SCEE-mc

SCEE-mc needs an **unpublished** osmfeatures build that understands the native
Name Suggestion Index (NSI) v8 JSON format used by
`UpdateNsiPresetsTask` / `files/osmfeatures/brands`.

Upstream Maven Central only publishes `de.westnordost:osmfeatures` up to **8.0.0**,
which does not include that parser. StreetComplete still uses the Central release;
this fork pins a small patch series on top of a fixed upstream commit instead.

| | |
|---|---|
| Upstream repo | https://github.com/westnordost/osmfeatures |
| Base commit | `30186418b8d20a6c14d5d4823fec1162e566360d` |
| Patches | `patches/0001-*.patch`, `patches/0002-*.patch` (NSI v8 + location sets) |
| Published local version | `8.1.0-nsi.1` |
| License | Apache License 2.0 (upstream) |

## Publish to `mavenLocal` (required once locally, always in CI)

```bash
./third_party/osmfeatures/publish-to-maven-local.sh
```

GitHub Actions runs the same script before Gradle builds. The artifact is **not**
fetched from JitPack or Maven Central.
