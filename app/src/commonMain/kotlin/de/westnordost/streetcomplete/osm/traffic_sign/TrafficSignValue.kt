package de.westnordost.streetcomplete.osm.traffic_sign

/** A parsed value of the traffic_sign key, e.g. "DE:250,1020-30;274-30".
 *
 *  Syntax (see https://wiki.openstreetmap.org/wiki/DE:Key:traffic_sign):
 *  - the country prefix ("DE:") is written once in front of the first sign
 *  - signs that belong together (main sign + its additional signs) are separated by ","
 *  - signs that are independent of each other are separated by ";"
 *  - variable values in brackets, e.g. "274[30]"; free texts in quotes, e.g. "\"Kfz-Verkehr frei\""
 *  - named values like "city_limit" or "none" are written without country prefix
 *
 *  Parts that are not known are retained as they were written, so that a value never loses
 *  information by being parsed and formatted again. */
data class TrafficSignValue(
    /** country of the signs, e.g. "DE". null if the value does not refer to any country catalogue,
     *  e.g. "none" or "city_limit" */
    val country: String?,
    val parts: List<TrafficSignPart>,
    /** the value could not be parsed reliably, e.g. because it refers to another or several
     *  countries or has unbalanced brackets. [toOsmValue] then returns [originalValue] unchanged */
    val isUnparseable: Boolean = false,
    val originalValue: String? = null,
) {
    /** main signs with their additional signs, in the order of the value */
    val groups: List<TrafficSignGroup> get() {
        val result = ArrayList<TrafficSignGroup>()
        var main: TrafficSignPart? = null
        var modifiers = ArrayList<TrafficSignPart>()
        for (part in parts) {
            if (part.kind == TrafficSignKind.TRAFFIC_SIGN) {
                if (main != null || modifiers.isNotEmpty()) result.add(TrafficSignGroup(main, modifiers))
                main = part
                modifiers = ArrayList()
            } else {
                modifiers.add(part)
            }
        }
        if (main != null || modifiers.isNotEmpty()) result.add(TrafficSignGroup(main, modifiers))
        return result
    }

    /** whether every part is a known sign of the catalogue or a named value */
    val isFullyRecognized: Boolean get() = !isUnparseable && parts.all { it.isRecognized }

    /** whether this is the explicit statement that there are no signs ("none") */
    val isNone: Boolean get() = !isUnparseable && parts.size == 1 && parts.single().text == "none"

    /** the value as it should be written to traffic_sign, like signsToTrafficSignTagValue of the
     *  converter */
    fun toOsmValue(): String {
        if (isUnparseable && originalValue != null) return originalValue
        val sb = StringBuilder()
        var countryPrefixWritten = false
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                val previousIsNamed = parts[index - 1].isNamedValue
                sb.append(if (part.kind == TrafficSignKind.TRAFFIC_SIGN || previousIsNamed) ';' else ',')
            }
            if (!part.isNamedValue && !countryPrefixWritten && country != null) {
                sb.append(country).append(':')
                countryPrefixWritten = true
            }
            sb.append(part.text)
        }
        return sb.toString()
    }

    override fun toString(): String = toOsmValue()

    companion object {
        val NONE = TrafficSignValue(null, listOf(TrafficSignPart.named("none")))

        /** Build a value from signs selected e.g. in a form */
        fun of(country: String, groups: List<TrafficSignGroup>): TrafficSignValue =
            TrafficSignValue(country, groups.flatMap { listOfNotNull(it.main) + it.modifiers })
    }
}

/** A main sign and its additional signs. [main] is null if additional signs are given without a main
 *  sign. */
data class TrafficSignGroup(
    val main: TrafficSignPart?,
    val modifiers: List<TrafficSignPart> = emptyList(),
)

/** One sign in a traffic_sign value */
data class TrafficSignPart(
    /** normalized text of this part as written into traffic_sign (without country prefix), e.g.
     *  "274-30" also if it was written as "274[30]" before. For unknown parts, the text as written. */
    val text: String,
    /** for unknown parts, whether the part was separated with ";" (or is the first one) or with "," */
    val kind: TrafficSignKind,
    /** the sign in the catalogue, null for unknown parts and named values. For signs with variable
     *  value, this is the catalogue entry with the default value, e.g. "274[47]" for "274[33]" */
    val sign: TrafficSign? = null,
    /** variable value, e.g. "33" for "274[33]" */
    val value: String? = null,
    val isNamedValue: Boolean = false,
) {
    val isRecognized: Boolean get() = sign != null || isNamedValue

    /** a sign with variable value, but no value given, e.g. "274" */
    val isMissingValue: Boolean get() = sign != null && sign.hasVariableValue && value == null

    /** a value was given for a sign without variable value, e.g. "250[3]" */
    val hasUnexpectedValue: Boolean get() = sign != null && !sign.hasVariableValue && value != null

    /** human-readable description of this sign including its variable value, e.g.
     *  "Zulässige Höchstgeschwindigkeit von 33 km/h" for "274[33]". The text as written for unknown
     *  parts */
    val description: String get() {
        val name = sign?.descriptiveName ?: return text
        if (value == null) return name
        return if ("??" in name) name.replace("??", value) else "$name ($value)"
    }

    companion object {
        fun of(sign: TrafficSign, value: String? = null): TrafficSignPart {
            require(value == null || sign.hasVariableValue) { "Sign ${sign.osmValuePart} has no variable value" }
            val text = if (sign.hasVariableValue) {
                combineSignIdAndValue(sign.signId, value ?: sign.defaultValue)
            } else {
                sign.osmValuePart
            }
            return TrafficSignPart(text, sign.kind, sign, if (sign.hasVariableValue) value ?: sign.defaultValue else null)
        }

        fun named(value: String): TrafficSignPart {
            require(value in NAMED_TRAFFIC_SIGN_VALUES) { "$value is not a named traffic sign value" }
            return TrafficSignPart(value, TrafficSignKind.TRAFFIC_SIGN, isNamedValue = true)
        }
    }
}

/** Parse the value of the traffic_sign key with the given catalogue, see [TrafficSignValue].
 *  Keys of the form "traffic_sign=..." in front of the value are ignored. Values of other countries
 *  than the catalogue's are returned as unparseable. */
fun parseTrafficSignValue(value: String, catalogue: TrafficSignCatalogue): TrafficSignValue {
    val country = catalogue.country
    var input = value.trim()
    // like removeKeys of the converter: "traffic_sign=DE:250" -> "DE:250"
    val equalsIndex = input.indexOf('=')
    if (equalsIndex >= 0 && "traffic_sign" in input.substring(0, equalsIndex)) {
        input = input.substring(equalsIndex + 1).trim()
    }

    val unparseable = TrafficSignValue(null, emptyList(), isUnparseable = true, originalValue = value)
    val rawParts = splitIntoParts(input) ?: return unparseable

    val parts = ArrayList<TrafficSignPart>()
    var valueCountry: String? = null
    for ((separator, raw) in rawParts) {
        val text = raw.trim()
        if (text.isEmpty()) continue
        val prefix = COUNTRY_PREFIX_REGEX.find(text)?.groupValues?.get(1)
        val withoutPrefix = if (prefix != null) text.substring(prefix.length + 1) else text
        if (withoutPrefix.isBlank()) return unparseable
        if (prefix != null) {
            // signs of other countries or a mix of countries are not supported
            if (!prefix.equals(country, ignoreCase = true)) return unparseable
            valueCountry = country
        }
        val kindIfUnknown = if (separator == ',') TrafficSignKind.EXCEPTION_MODIFIER else TrafficSignKind.TRAFFIC_SIGN

        val named = GENERAL_REDIRECTS[withoutPrefix.lowercase()] ?: withoutPrefix
        if (named in NAMED_TRAFFIC_SIGN_VALUES) {
            parts.add(TrafficSignPart(named, TrafficSignKind.TRAFFIC_SIGN, isNamedValue = true))
            continue
        }
        val resolved = catalogue.resolve(withoutPrefix)
        if (resolved != null) {
            parts.add(TrafficSignPart(resolved.osmValuePart, resolved.sign.kind, resolved.sign, resolved.value))
        } else {
            parts.add(TrafficSignPart(withoutPrefix, kindIfUnknown))
        }
    }
    if (parts.isEmpty()) return unparseable
    // signs without any country prefix (e.g. "250") are not interpreted as signs of that country
    if (valueCountry == null && parts.any { !it.isNamedValue }) return unparseable
    return TrafficSignValue(valueCountry, parts)
}

/** country prefixes are two uppercase (or lowercase) letters followed by ':' */
private val COUNTRY_PREFIX_REGEX = Regex("^([A-Za-z]{2}):")

/** Split on "," and ";", but not within brackets or quotes. Returns pairs of the separator in front
 *  of the part (null for the first part) and the part. Returns null for unbalanced brackets or
 *  quotes. */
private fun splitIntoParts(input: String): List<Pair<Char?, String>>? {
    val result = ArrayList<Pair<Char?, String>>()
    var bracketDepth = 0
    var inQuotes = false
    var separator: Char? = null
    val current = StringBuilder()
    for (c in input) {
        when {
            c == '"' -> { inQuotes = !inQuotes; current.append(c) }
            inQuotes -> current.append(c)
            c == '[' -> { bracketDepth++; current.append(c) }
            c == ']' -> { bracketDepth--; if (bracketDepth < 0) return null; current.append(c) }
            (c == ',' || c == ';') && bracketDepth == 0 -> {
                result.add(separator to current.toString())
                current.clear()
                separator = c
            }
            else -> current.append(c)
        }
    }
    if (bracketDepth != 0 || inQuotes) return null
    result.add(separator to current.toString())
    return result
}
