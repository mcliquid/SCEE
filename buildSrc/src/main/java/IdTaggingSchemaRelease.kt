import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal const val ID_TAGGING_SCHEMA_REPO = "openstreetmap/id-tagging-schema"

/** Zip archive from GitHub codeload for [versionTag] (e.g. `v7.3.0`). */
fun idTaggingSchemaReleaseZipUrl(versionTag: String): String =
    "https://codeload.github.com/$ID_TAGGING_SCHEMA_REPO/zip/refs/tags/$versionTag"

private val DIST_FILE = Regex("^.*/dist/(presets\\.json|translations/[^/]+\\.json)$")

/** Keys are paths relative to `dist/` (`presets.json`, `translations/en.json`, …). Minified JSON is excluded. */
fun readIdTaggingSchemaDistFromZip(zipBytes: ByteArray): Map<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val match = DIST_FILE.matchEntire(entry.name.replace('\\', '/'))
                if (match != null) {
                    val relative = match.groupValues[1]
                    if (!relative.endsWith(".min.json")) {
                        result[relative] = zip.readBytes()
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    return result
}