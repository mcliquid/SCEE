import com.esotericsoftware.yamlbeans.YamlReader
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test

/** Optional live parity against full streetcomplete/countrymetadata (needs network). */
class CountryMetadataUpstreamIntegrationTest {
    @Test fun `full upstream corpus matches yamlbeans before write`() {
        assumeTrue("Set SCEE_NETWORK_INTEGRATION=true to run live upstream parity", System.getenv("SCEE_NETWORK_INTEGRATION") == "true")
        val frozenDir = Files.createTempDirectory("country-metadata-live").toFile()
        try {
            freezeCountryMetadataInputs(frozenDir)
            val propertyDir = File(frozenDir, "properties")
            val files = propertyDir.listFiles().orEmpty().filter { it.name.endsWith(".yml") }
            for (file in files) {
                val text = file.readText()
                val old = YamlReader(text).read() as Map<String, Any>
                val neu = parseCountryMetadataYaml(text)
                assertMapsContentEqual(
                    normalizeMetadataMap(old),
                    normalizeMetadataMap(neu),
                    file.nameWithoutExtension,
                )
            }
        } finally {
            frozenDir.deleteRecursively()
        }
    }
}

private fun freezeCountryMetadataInputs(targetDir: File) {
    val propertyDir = File(targetDir, "properties").also { it.mkdirs() }
    val projectRoot = File(System.getProperty("scee.project.root"))
    for (file in projectRoot.resolve("res/country_metadata").listFiles().orEmpty().filter { it.name.endsWith(".yml") }) {
        file.copyTo(File(propertyDir, file.name), overwrite = true)
    }
    val listing = java.net.URI(
        "https://api.github.com/repos/streetcomplete/countrymetadata/contents/data",
    ).toURL().readText()
    val items = Json { ignoreUnknownKeys = true }.decodeFromString<List<GithubListingItem>>(listing)
    for (item in items.filter { it.type == "file" && it.name.endsWith(".yml") }) {
        val body = java.net.URI(item.download_url).toURL().readText()
        File(propertyDir, item.name).writeText(body)
    }
}

@Serializable
private data class GithubListingItem(val name: String, val type: String, val download_url: String)