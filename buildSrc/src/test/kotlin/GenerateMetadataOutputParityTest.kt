import com.esotericsoftware.yamlbeans.YamlReader
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateMetadataOutputParityTest {
    @Test fun `generated outputs are byte-identical for frozen inputs`() {
        val frozenDir = System.getenv("SCEE_FROZEN_METADATA_DIR")?.let(::File)
            ?: Files.createTempDirectory("country-metadata-freeze").also { dir ->
                freezeCountryMetadataInputs(dir.toFile())
            }.toFile()
        try {
            val propertyDir = File(frozenDir, "properties")
            assertTrue(propertyDir.isDirectory, "Missing frozen property inputs in $frozenDir")

            val metadataByPropertyOld = propertyDir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".yml") }
                .associate { it.nameWithoutExtension to YamlReader(it.readText()).read() as Map<String, Any> }
            val metadataByPropertyNew = propertyDir.listFiles().orEmpty()
                .filter { it.isFile && it.name.endsWith(".yml") }
                .associate { it.nameWithoutExtension to parseCountryMetadataYaml(it.readText()) }

            for ((property, oldMap) in metadataByPropertyOld) {
                val neu = metadataByPropertyNew.getValue(property)
                assertMapsContentEqual(
                    normalizeMetadataMap(oldMap),
                    normalizeMetadataMap(neu),
                    property,
                )
            }

            val outOld = Files.createTempDirectory("country-metadata-old").toFile()
            val outNew = Files.createTempDirectory("country-metadata-new").toFile()
            try {
                writeCountryMetadataFiles(metadataByCountryFromProperties(metadataByPropertyOld), outOld)
                writeCountryMetadataFiles(metadataByCountryFromProperties(metadataByPropertyNew), outNew)

                val oldFiles = outOld.listFiles().orEmpty().filter { it.name.endsWith(".yml") }.sortedBy { it.name }
                val newFiles = outNew.listFiles().orEmpty().filter { it.name.endsWith(".yml") }.sortedBy { it.name }
                assertEquals(oldFiles.map { it.name }, newFiles.map { it.name })
                assertEquals(515, oldFiles.size, "Expected 515 country metadata files")
                val mismatches = oldFiles.zip(newFiles).filterNot { (a, b) ->
                    sha256(a) == sha256(b)
                }
                assertEquals(emptyList(), mismatches.map { it.first.name }, "Byte differences in generated outputs")
            } finally {
                outOld.deleteRecursively()
                outNew.deleteRecursively()
            }
        } finally {
            if (System.getenv("SCEE_FROZEN_METADATA_DIR") == null) {
                frozenDir.deleteRecursively()
            }
        }
    }
}

private fun freezeCountryMetadataInputs(targetDir: File) {
    val propertyDir = File(targetDir, "properties").also { it.mkdirs() }
    val projectRoot = File(System.getProperty("scee.project.root"))
    val localDir = projectRoot.resolve("res/country_metadata")
    for (file in localDir.listFiles().orEmpty().filter { it.name.endsWith(".yml") }) {
        file.copyTo(File(propertyDir, file.name), overwrite = true)
    }
    val listing = java.net.URI(
        "https://api.github.com/repos/streetcomplete/countrymetadata/contents/data",
    ).toURL().readText()
    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    val items = json.decodeFromString<List<FrozenListingItem>>(listing)
    for (item in items.filter { it.type == "file" && it.name.endsWith(".yml") }) {
        val body = java.net.URI(item.download_url).toURL().readText()
        File(propertyDir, item.name).writeText(body)
    }
}

@kotlinx.serialization.Serializable
private data class FrozenListingItem(val name: String, val type: String, val download_url: String)

private fun sha256(file: File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }