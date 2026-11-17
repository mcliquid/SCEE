import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PresetDataUpdateTest {

    private fun buildReleaseZip(vararg distFiles: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((path, content) in distFiles) {
                val entry = ZipEntry("id-tagging-schema-0.0.0/dist/$path")
                zip.putNextEntry(entry)
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

  private val minimalPresets = """
      {
        "amenity/cafe": {
          "icon": "temaki-cafe",
          "name": "Cafe",
          "tags": { "amenity": "cafe" }
        },
        "disused/shop": { "name": "x" },
        "@templates/foo": { "name": "t" }
      }
      """.trimIndent()

    private val deTranslation = """
      {
        "de": {
          "presets": {
            "presets": { "amenity/cafe": { "name": "Café" } },
            "categories": { "should": "go" },
            "fields": { "should": "go" }
          }
        }
      }
      """.trimIndent()

    @Test
    fun usesPinnedReleaseZipForPresetsAndTranslations() {
        val zip = buildReleaseZip(
            "presets.json" to minimalPresets,
            "translations/de.json" to deTranslation,
            "translations/de.min.json" to """{"min":true}""",
        )
        val target = Files.createTempDirectory("presets-target").toFile()
        try {
            target.resolve("presets.json").writeText("""{"keep":true}""")
            val requested = mutableListOf<String>()

            updatePresetData(
                PresetDataUpdateConfig("v0.0.0", listOf("de"), target),
                fetchBytes = { url ->
                    requested.add(url)
                    when {
                        url.contains("codeload.github.com") -> zip
                        else -> error("unexpected URL $url")
                    }
                },
            )

            assertEquals(listOf(idTaggingSchemaReleaseZipUrl("v0.0.0")), requested)
            val presets = target.resolve("presets.json").readText()
            assertTrue(!presets.contains("disused/"))
            assertTrue(!presets.contains("@templates"))
            assertTrue(!presets.contains("\"fields\""))
            val de = target.resolve("de.json").readText()
            assertContains(de, "Café")
            assertTrue(!de.contains("categories"))
            assertTrue(!de.contains("\"fields\""))
        } finally {
            target.deleteRecursively()
        }
    }

    @Test
    fun networkFailureDoesNotModifyExistingTarget() {
        val target = Files.createTempDirectory("presets-keep").toFile()
        try {
            val keep = """{"stable":true}"""
            target.resolve("presets.json").writeText(keep)
            target.resolve("de.json").writeText("locale")

            assertFailsWith<PresetDataUpdateException> {
                updatePresetData(
                    PresetDataUpdateConfig("v9.9.9", listOf("de"), target),
                    fetchBytes = { throw java.io.IOException("offline") },
                )
            }

            assertEquals(keep, target.resolve("presets.json").readText())
            assertEquals("locale", target.resolve("de.json").readText())
        } finally {
            target.deleteRecursively()
        }
    }

    @Test
    fun reduceOutputIsDeterministic() {
        val first = reducePresetsJson(minimalPresets)
        val second = reducePresetsJson(minimalPresets)
        assertEquals(first, second)
    }
}