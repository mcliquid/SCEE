import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.inspector.TagInspector
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.representer.Representer

private const val MAX_ALIASES = 16
private const val MAX_NESTING = 4

private val metadataLoaderOptions = createCountryMetadataLoaderOptions()
private val countryMetadataYaml = run {
    val dumperOptions = DumperOptions()
    val constructor = SafeConstructor(metadataLoaderOptions)
    Yaml(
        constructor,
        Representer(dumperOptions),
        dumperOptions,
        metadataLoaderOptions,
        CountryMetadataResolver(),
    )
}

private fun createCountryMetadataLoaderOptions(): LoaderOptions = LoaderOptions().apply {
    isAllowDuplicateKeys = true
    isWarnOnDuplicateKeys = false
    maxAliasesForCollections = MAX_ALIASES
    nestingDepthLimit = MAX_NESTING
    allowRecursiveKeys = false
    setTagInspector(TagInspector { tag -> !tag.isCustomGlobal })
}

/** Parse country-metadata property files without Java-bean style class instantiation. */
internal fun parseCountryMetadataYaml(text: String): Map<String, Any> {
    val loaded = countryMetadataYaml.load<Any>(text)
    require(loaded is Map<*, *>) { "Country metadata must be a YAML mapping" }
    val result = linkedMapOf<String, Any>()
    for ((key, value) in loaded) {
        val keyText = metadataKeyText(key)
        require(keyText.isNotBlank()) { "Country metadata keys must be non-blank strings" }
        result[keyText] = metadataValue(value)
    }
    return result
}

private fun metadataKeyText(key: Any?): String = when (key) {
    null -> ""
    is String -> key
    is Boolean -> key.toString()
    is Number -> key.toString()
    else -> key.toString()
}

private fun metadataValue(value: Any?): Any = when (value) {
    null -> emptyList<Any>()
    is Boolean, is String -> value
    is Number -> numberMetadataValue(value)
    is List<*> -> value.map {
        when (val item = metadataValue(it)) {
            is Int, is Long -> item.toString()
            is Double -> if (item == item.toLong().toDouble()) item.toLong().toString() else item.toString()
            else -> item
        }
    }
    is Map<*, *> -> throw IllegalArgumentException("Nested mappings are not supported in country metadata")
    else -> throw IllegalArgumentException("Unsupported YAML value type: ${value::class.java.name}")
}

private fun numberMetadataValue(value: Number): Any {
    val d = value.toDouble()
    if (d == value.toLong().toDouble()) {
        val long = value.toLong()
        return if (long in Int.MIN_VALUE..Int.MAX_VALUE) long.toInt() else long
    }
    return d
}