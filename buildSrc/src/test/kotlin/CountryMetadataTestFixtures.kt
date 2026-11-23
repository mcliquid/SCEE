import java.io.File

internal object CountryMetadataTestFixtures {
    fun propertiesDir(): File {
        val url = checkNotNull(CountryMetadataTestFixtures::class.java.getResource("/country-metadata-parity/properties")) {
            "Missing classpath fixture directory country-metadata-parity/properties"
        }
        return File(url.toURI())
    }

    fun readProperty(fileName: String): String = File(propertiesDir(), fileName).readText()

    fun propertyFiles(): List<File> =
        propertiesDir().listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".yml") }.sortedBy { it.name }
}