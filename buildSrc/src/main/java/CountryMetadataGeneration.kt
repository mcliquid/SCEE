import com.esotericsoftware.yamlbeans.YamlConfig
import com.esotericsoftware.yamlbeans.YamlWriter
import java.io.File
import java.io.StringWriter

internal fun writeCountryMetadataFiles(
    metadataByCountry: Map<String, Map<String, Any>>,
    targetDir: File,
) {
    val config = YamlConfig().apply {
        writeConfig.setWriteClassname(YamlConfig.WriteClassName.NEVER)
        writeConfig.isFlowStyle = true
        writeConfig.setWrapColumn(Int.MAX_VALUE)
        writeConfig.setEscapeUnicode(false)
    }
    targetDir.mkdirs()
    for ((countryCode, valuesByProperty) in metadataByCountry) {
        val targetFile = File(targetDir, "$countryCode.yml")
        targetFile.writer().use { fileWriter ->
            fileWriter.write(
                "# Do not edit. Data is from res/country/metadata and https://github.com/streetcomplete/countrymetadata\n",
            )
            for ((property, value) in valuesByProperty.toSortedMap()) {
                val str = StringWriter()
                YamlWriter(str, config).use { it.write(prepareMetadataValueForWrite(value)) }
                fileWriter.write("$property: $str")
            }
        }
    }
}

private fun stringMetadataScalar(value: String): Any = when (value.lowercase()) {
    "true" -> true
    "false" -> false
    else -> value.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it } ?: value
}

internal fun prepareMetadataValueForWrite(value: Any): Any = when (value) {
    is String -> stringMetadataScalar(value)
    is List<*> -> value.map { prepareMetadataValueForWrite(it as Any) }
    else -> value
}

internal fun metadataByCountryFromProperties(
    metadataByProperty: Map<String, Map<String, Any>>,
): Map<String, Map<String, Any>> {
    val metadataByCountry = mutableMapOf<String, MutableMap<String, Any>>()
    for ((property, valuesByCountry) in metadataByProperty) {
        for ((countryCode, value) in valuesByCountry) {
            metadataByCountry.getOrPut(countryCode) { mutableMapOf() }[property] = value
        }
    }
    return metadataByCountry
}