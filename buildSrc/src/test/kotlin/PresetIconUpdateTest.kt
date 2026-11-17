import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PresetIconUpdateTest {

    private val minimalPresets = """{ "x": { "icon": "maki-park" }, "y": { "icon": "iD-restriction-only-u-turn" } }"""

    private val sampleLock = """
      {
        "packages": {
          "node_modules/@openstreetmap/id-tagging-schema": { "version": "7.3.0" },
          "node_modules/@mapbox/maki": { "version": "8.2.0" },
          "node_modules/@rapideditor/temaki": { "version": "5.13.0" },
          "node_modules/@waysidemapping/pinhead": { "version": "16.26.0" },
          "node_modules/@enzet/roentgen": { "version": "0.16.0" },
          "node_modules/@fortawesome/free-solid-svg-icons": { "version": "7.2.0" }
        }
      }
      """.trimIndent()

    private val dtdSvg = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 1.1//EN" "http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd">
      <svg xmlns="http://www.w3.org/2000/svg" width="15" height="15" viewBox="0 0 15 15">
        <path d="M 1 1 L 2 2 Z"/>
      </svg>
      """.trimIndent()

    private val plainSvg = """
      <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 14 14">
        <path d="M 0 0 L 14 14"/>
      </svg>
      """.trimIndent()

    @Test
    fun parsesSvgWithExternalDtdWithoutFetchingNetwork() {
        val doc = parseSvgStream(ByteArrayInputStream(dtdSvg.toByteArray()))
        val drawable = createAndroidDrawableFromSvg(doc, 34)
        assertTrue(drawable.documentElement.tagName == "vector")
    }

    @Test
    fun invalidSvgIsRejected() {
        assertFailsWith<SAXException> {
            parseSvgStream(ByteArrayInputStream("<not-svg".toByteArray()))
        }
    }

    @Test
    fun iconUpdateFailureLeavesExistingDrawablesUntouched() {
        val target = Files.createTempDirectory("preset-icons").toFile()
        try {
            val keep = target.resolve("preset_keep_me.xml")
            keep.writeText("<vector/>")

            assertFailsWith<PresetIconUpdateException> {
                updatePresetIcons(
                    PresetIconUpdateConfig(
                        versionTag = "v7.3.0",
                        targetDir = target,
                        iconSize = 34,
                        nameTransform = { "preset_$it" },
                        idEditorRevisionLockFile = null,
                    ),
                    fetchText = { url ->
                        when {
                            url == "https://api.github.com/repos/openstreetmap/iD/commits/develop" ->
                                """{"sha":"e42f8d4fa79e86d42b07a23316a6fcb75d9460a9"}"""
                            url == idEditorPackageLockUrl("e42f8d4fa79e86d42b07a23316a6fcb75d9460a9") -> sampleLock
                            url.contains("pinhead@") && url.endsWith("id_upgrades.json") -> "{}"
                            url.contains("/openstreetmap/iD/") && url.endsWith("package-lock.json") ->
                                throw java.io.IOException("no tag lock")
                            else -> error(url)
                        }
                    },
                    fetchBytes = { url ->
                        when {
                            url.contains("id-tagging-schema/zip/") -> buildZip("presets.json" to minimalPresets)
                            url.endsWith("park.svg") -> plainSvg.toByteArray()
                            else -> throw java.io.IOException("missing $url")
                        }
                    },
                )
            }
            assertEquals("<vector/>", keep.readText())
        } finally {
            target.deleteRecursively()
        }
    }

    @Test
    fun successfulStagingReplacesOnlyPresetIconPrefix() {
        val target = Files.createTempDirectory("preset-icons-replace").toFile()
        val staging = Files.createTempDirectory("staging").toFile()
        try {
            target.resolve("preset_old.xml").writeText("old")
            target.resolve("quest_other.xml").writeText("stay")
            staging.resolve("preset_new.xml").writeText("new")

            replacePresetIconDrawables(target, staging, "preset_")

            assertEquals("new", target.resolve("preset_new.xml").readText())
            assertTrue(!target.resolve("preset_old.xml").exists())
            assertEquals("stay", target.resolve("quest_other.xml").readText())
        } finally {
            target.deleteRecursively()
            staging.deleteRecursively()
        }
    }

    @Test
    fun iconUrlResolutionUsesIdEditorLockfileVersions() {
        val catalog = idEditorIconCatalogFromLock(sampleLock, "develop", "7.3.0")
        val maki = iconDownloadUrls("maki-park", catalog)!!.single()
        assertTrue(maki.contains("@mapbox/maki@8.2.0"))
        val idSprite = iconDownloadUrls("iD-restriction-only-u-turn", catalog)!!.single()
        assertTrue(idSprite.contains("/0123456789abcdef0123456789abcdef01234567/"))
    }

    private fun idEditorIconCatalogFromLock(lock: String, ref: String, schema: String) =
        IdEditorIconCatalog(
            idEditorCommitSha = "0123456789abcdef0123456789abcdef01234567",
            resolutionLabel = "test $ref",
            idTaggingSchemaVersion = schema,
            makiVersion = readLockfilePackageVersion(lock, "@mapbox/maki")!!,
            temakiVersion = readLockfilePackageVersion(lock, "@rapideditor/temaki")!!,
            pinheadVersion = readLockfilePackageVersion(lock, "@waysidemapping/pinhead")!!,
            pinheadIdUpgrades = emptyMap(),
            roentgenVersion = readLockfilePackageVersion(lock, "@enzet/roentgen")!!,
            fontAwesomeVersion = readLockfilePackageVersion(lock, "@fortawesome/free-solid-svg-icons")!!,
        )

    private fun buildZip(vararg distFiles: Pair<String, String>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((path, content) in distFiles) {
                zip.putNextEntry(java.util.zip.ZipEntry("id-tagging-schema/dist/$path"))
                zip.write(content.encodeToByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}