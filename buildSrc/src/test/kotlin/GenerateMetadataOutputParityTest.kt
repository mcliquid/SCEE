import com.esotericsoftware.yamlbeans.YamlReader
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateMetadataOutputParityTest {
    @Test fun `generated outputs are byte-identical for frozen classpath inputs`() {
        val propertyFiles = CountryMetadataTestFixtures.propertyFiles()
        assertTrue(propertyFiles.isNotEmpty(), "Missing parity fixture property files")

        val metadataByPropertyOld = propertyFiles.associate {
            it.nameWithoutExtension to YamlReader(it.readText()).read() as Map<String, Any>
        }
        val metadataByPropertyNew = propertyFiles.associate {
            it.nameWithoutExtension to parseCountryMetadataYaml(it.readText())
        }

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
            val mismatches = oldFiles.zip(newFiles).filterNot { (a, b) -> sha256(a) == sha256(b) }
            assertEquals(emptyList(), mismatches.map { it.first.name }, "Byte differences in generated outputs")
        } finally {
            outOld.deleteRecursively()
            outNew.deleteRecursively()
        }
    }
}

private fun sha256(file: java.io.File): String =
    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }