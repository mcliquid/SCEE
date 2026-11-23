import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.events.AliasEvent
import org.yaml.snakeyaml.events.DocumentEndEvent
import org.yaml.snakeyaml.events.DocumentStartEvent
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.events.MappingEndEvent
import org.yaml.snakeyaml.events.MappingStartEvent
import org.yaml.snakeyaml.events.ScalarEvent
import org.yaml.snakeyaml.events.SequenceEndEvent
import org.yaml.snakeyaml.events.SequenceStartEvent
import org.yaml.snakeyaml.events.StreamEndEvent
import org.yaml.snakeyaml.events.StreamStartEvent
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader

private const val MAX_ALIASES = 16
private const val MAX_NESTING = 4

private val metadataLoaderOptions = createCountryMetadataLoaderOptions()

/** Parse country-metadata property files without instantiating arbitrary Java types. */
internal fun parseCountryMetadataYaml(text: String): Map<String, Any> =
    CountryMetadataEventParser(metadataLoaderOptions).parse(stabilizeYamlCountryCodeKeys(text))

/** Quote YAML-1.1 boolean-like country codes so keys stay strings (e.g. Norway = NO). */
internal fun stabilizeYamlCountryCodeKeys(text: String): String {
    val yaml11BooleanLikeCountryKeys = listOf("NO", "YES", "Y", "N", "ON", "OFF")
    var stabilized = text
    for (key in yaml11BooleanLikeCountryKeys) {
        stabilized = stabilized.replace(
            Regex("""^(\s*)$key(\s*:)""", RegexOption.MULTILINE),
        ) { match -> "${match.groupValues[1]}'$key'${match.groupValues[2]}" }
    }
    return stabilized
}

private fun createCountryMetadataLoaderOptions(): LoaderOptions = LoaderOptions().apply {
    isAllowDuplicateKeys = true
    isWarnOnDuplicateKeys = false
    maxAliasesForCollections = MAX_ALIASES
    nestingDepthLimit = MAX_NESTING
    allowRecursiveKeys = false
}

private class CountryMetadataEventParser(private val loaderOptions: LoaderOptions) {
    fun parse(text: String): Map<String, Any> {
        val parser = ParserImpl(StreamReader(text), loaderOptions)
        consumeOptionalStreamStart(parser)
        if (parser.peekEvent() is DocumentStartEvent) parser.getEvent()
        val mapping = parseMapping(parser)
        if (parser.peekEvent() is DocumentEndEvent) parser.getEvent()
        consumeOptionalStreamEnd(parser)
        return mapping
    }

    private fun parseMapping(parser: ParserImpl): LinkedHashMap<String, Any> {
        require(parser.getEvent() is MappingStartEvent)
        val result = LinkedHashMap<String, Any>()
        while (parser.peekEvent() !is MappingEndEvent) {
            val keyEvent = parser.getEvent()
            val key = scalarKey(keyEvent)
            result[key] = parseValue(parser)
        }
        parser.getEvent()
        return result
    }

    private fun parseSequence(parser: ParserImpl): List<Any> {
        require(parser.getEvent() is SequenceStartEvent)
        val items = mutableListOf<Any>()
        while (parser.peekEvent() !is SequenceEndEvent) {
            items += parseValue(parser)
        }
        parser.getEvent()
        return items.map(::normalizeListItem)
    }

    private fun parseValue(parser: ParserImpl): Any = when (parser.peekEvent()) {
        is ScalarEvent -> scalarMetadataValue((parser.getEvent() as ScalarEvent).also(::rejectCustomTag).value)
        is SequenceStartEvent -> parseSequence(parser)
        is MappingStartEvent -> {
            parser.getEvent()
            throw IllegalArgumentException("Nested mappings are not supported in country metadata")
        }
        is AliasEvent -> throw IllegalArgumentException("YAML aliases are not supported in country metadata")
        else -> throw IllegalArgumentException("Unsupported YAML value")
    }

    private fun scalarKey(event: Event): String {
        require(event is ScalarEvent) { "Country metadata keys must be scalars" }
        rejectCustomTag(event)
        val keyText = metadataKeyText(event.value)
        require(keyText.isNotBlank()) { "Country metadata keys must be non-blank strings" }
        return keyText
    }

    private fun rejectCustomTag(event: ScalarEvent) {
        val tag = event.tag ?: return
        require(tag.startsWith("tag:yaml.org,2002:")) { "Tagged YAML nodes are not supported" }
    }

    private fun consumeOptionalStreamStart(parser: ParserImpl) {
        if (parser.peekEvent() is StreamStartEvent) parser.getEvent()
    }

    private fun consumeOptionalStreamEnd(parser: ParserImpl) {
        if (parser.peekEvent() is StreamEndEvent) parser.getEvent()
    }
}

private fun metadataKeyText(key: Any?): String = when (key) {
    null -> ""
    is String -> key
    is Boolean -> key.toString()
    is Number -> key.toString()
    else -> key.toString()
}

private fun scalarMetadataValue(content: String): Any {
    when {
        content.equals("true", ignoreCase = true) -> return true
        content.equals("false", ignoreCase = true) -> return false
    }
    content.toLongOrNull()?.let { long ->
        return if (long in Int.MIN_VALUE..Int.MAX_VALUE) long.toInt() else long
    }
    content.toDoubleOrNull()?.let { return it }
    return content
}

private fun normalizeListItem(item: Any): Any = when (item) {
    is Int, is Long -> item.toString()
    is Double -> if (item == item.toLong().toDouble()) item.toLong().toString() else item.toString()
    is Map<*, *> -> throw IllegalArgumentException("Nested mappings are not supported in country metadata")
    else -> item
}