package de.westnordost.streetcomplete.osm.traffic_sign

/** What the user has entered for one direction of a way. Three strictly separate states:
 *  - [NotSurveyed]: nothing is said, nothing is changed. Never turns into "none".
 *  - [None]: it was surveyed that there are no signs
 *  - [Signed]: there are signs */
sealed interface DirectionSelection {
    data object NotSurveyed : DirectionSelection
    data object None : DirectionSelection
    data class Signed(val value: TrafficSignValue) : DirectionSelection

    /** The value as used by the tagging engine: null = not surveyed */
    fun toValue(): TrafficSignValue? = when (this) {
        NotSurveyed -> null
        None -> TrafficSignValue.NONE
        is Signed -> value
    }

    /** Text form for saving the UI state: "" = not surveyed, otherwise the traffic_sign value */
    fun encode(): String = when (this) {
        NotSurveyed -> ""
        None -> "none"
        is Signed -> value.toOsmValue()
    }

    companion object {
        /** the selection that corresponds to an existing value (null = nothing tagged) */
        fun of(value: TrafficSignValue?): DirectionSelection = when {
            value == null -> NotSurveyed
            value.isNone -> None
            else -> Signed(value)
        }

        fun decode(encoded: String, catalogue: TrafficSignCatalogue): DirectionSelection =
            if (encoded.isEmpty()) NotSurveyed else of(parseTrafficSignValue(encoded, catalogue))
    }
}

/** The selection of both directions of a way */
data class WaySignSelection(
    val forward: DirectionSelection,
    val backward: DirectionSelection,
) {
    operator fun get(direction: TrafficSignDirection): DirectionSelection = when (direction) {
        TrafficSignDirection.FORWARD -> forward
        TrafficSignDirection.BACKWARD -> backward
    }

    fun with(direction: TrafficSignDirection, selection: DirectionSelection): WaySignSelection =
        when (direction) {
            TrafficSignDirection.FORWARD -> copy(forward = selection)
            TrafficSignDirection.BACKWARD -> copy(backward = selection)
        }

    fun toWaySigns() = TrafficSignWaySigns(forward.toValue(), backward.toValue())

    /** The signs to hand to the tagging engine when the user changed [original] into this
     *  selection. Directions the user did not change (same value as before, regardless of how it
     *  was spelled in the tags, e.g. "DE:274[30]" and "DE:274-30" are the same) are passed as "not
     *  surveyed", so that their tags stay exactly as they are and are not rewritten in a
     *  normalized spelling. */
    fun toWaySigns(original: WaySignSelection): TrafficSignWaySigns {
        fun valueIfChanged(d: TrafficSignDirection) = if (this[d].encode() != original[d].encode()) this[d].toValue() else null
        return TrafficSignWaySigns(
            valueIfChanged(TrafficSignDirection.FORWARD),
            valueIfChanged(TrafficSignDirection.BACKWARD),
        )
    }

    /** whether the selection overrides an existing traffic sign of [tags] with "none" */
    fun replacesSignsWithNone(tags: Map<String, String>, catalogue: TrafficSignCatalogue): Boolean {
        val (oldForward, oldBackward) = parseTrafficSignWaySigns(tags, catalogue)
        return replaces(forward, oldForward) || replaces(backward, oldBackward)
    }

    private fun replaces(selection: DirectionSelection, old: TrafficSignValue?): Boolean =
        selection == DirectionSelection.None && old != null && !old.isNone

    companion object {
        /** the selection that corresponds to the existing tags */
        fun of(tags: Map<String, String>, catalogue: TrafficSignCatalogue): WaySignSelection {
            val (forward, backward) = parseTrafficSignWaySigns(tags, catalogue)
            return WaySignSelection(DirectionSelection.of(forward), DirectionSelection.of(backward))
        }
    }
}

/* ---------------------------------- editing of the signs ---------------------------------- */

/** Edit operations on the groups (main sign + additional signs) while the user composes the signs
 *  of one direction. Nothing is restricted here: unusual combinations stay possible. */
/** Adds a main sign:
 *  - to the first group that has additional signs but no main sign yet ("DE:1022-10" + 267 =
 *    "DE:267,1022-10"), other groups stay as they are
 *  - otherwise as a new group at the end. A placeholder "yes" (there are signs, but not which) is
 *    removed then, it is not a sign. Other named values (maxspeed, city_limit, ...) are kept. */
fun List<TrafficSignGroup>.withMainSign(part: TrafficSignPart): List<TrafficSignGroup> {
    val groups = filterNot { it.isPlaceholder() }
    val orphan = groups.indexOfFirst { it.main == null }
    if (orphan >= 0) {
        return groups.mapIndexed { i, g -> if (i == orphan) g.copy(main = part) else g }
    }
    return groups + TrafficSignGroup(part)
}

/** Adds an additional sign to a group. For a placeholder "yes" group, the placeholder is dropped
 *  and the additional sign is left without main sign (a main sign can be added to it later) */
fun List<TrafficSignGroup>.withModifier(groupIndex: Int, part: TrafficSignPart): List<TrafficSignGroup> =
    mapIndexed { i, g ->
        when {
            i != groupIndex -> g
            g.isPlaceholder() -> TrafficSignGroup(null, listOf(part))
            else -> g.copy(modifiers = g.modifiers + part)
        }
    }

/** Whether this is only the value "yes", which says that there are signs but not which ones */
fun TrafficSignGroup.isPlaceholder(): Boolean =
    modifiers.isEmpty() && main != null && main.sign == null && main.text.equals("yes", ignoreCase = true)

/** removes the whole group including its additional signs */
fun List<TrafficSignGroup>.withoutGroup(groupIndex: Int): List<TrafficSignGroup> =
    filterIndexed { i, _ -> i != groupIndex }

fun List<TrafficSignGroup>.withoutModifier(groupIndex: Int, modifierIndex: Int): List<TrafficSignGroup> =
    mapIndexed { i, g ->
        if (i == groupIndex) g.copy(modifiers = g.modifiers.filterIndexed { j, _ -> j != modifierIndex }) else g
    }

/** The draft as a value, or null if there are no signs yet */
fun List<TrafficSignGroup>.toValueOrNull(country: String): TrafficSignValue? =
    if (isEmpty()) null else TrafficSignValue.of(country, this)

/** Whether [text] is an acceptable variable value for a sign with this prompt format */
fun isValidTrafficSignValueInput(format: String, text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.any { it == '[' || it == ']' || it == '"' || it == ';' && format != "opening_hours" }) return false
    return when (format) {
        "integer" -> t.toIntOrNull() != null
        "float" -> t.replace(',', '.').toDoubleOrNull() != null
        else -> true
    }
}

/** The value as it is written into the sign (decimal comma → point for floats) */
fun normalizeTrafficSignValueInput(format: String, text: String): String =
    text.trim().let { if (format == "float") it.replace(',', '.') else it }

/** A sign entered by its number, e.g. "1010-50", or as free text, e.g. "Anwohner, Taxi frei" in
 *  quotation marks. Returns null if it is not a single sign that can be written into a
 *  traffic_sign value without ambiguity. Signs that are not in the catalogue are returned as
 *  unknown parts, so that unusual signs can still be recorded.
 *
 *  "," and ";" separate signs, but not inside quotation marks or brackets, like in
 *  [parseTrafficSignValue]. A free text is one pair of quotation marks around everything (quotation
 *  marks can not be escaped in a traffic_sign value, so there can not be others inside). Spaces,
 *  "=", "," and ";" outside of quotation marks and brackets are not accepted. */
fun parseCustomTrafficSign(text: String, catalogue: TrafficSignCatalogue, asModifier: Boolean): TrafficSignPart? {
    val t = text.trim().removePrefix("${catalogue.country}:").trim()
    if (t.isEmpty() || !isSingleToken(t)) return null
    val value = parseTrafficSignValue("${catalogue.country}:$t", catalogue)
    val part = value.parts.singleOrNull() ?: return null
    if (value.isUnparseable || part.isNamedValue || part.isMissingValue || part.hasUnexpectedValue) return null
    // unknown signs take the kind of the place they are added to
    return if (part.sign == null) part.copy(kind = if (asModifier) TrafficSignKind.EXCEPTION_MODIFIER else TrafficSignKind.TRAFFIC_SIGN) else part
}

/** whether [t] has no separator, "=" or space outside of one pair of quotation marks around all of
 *  it or outside of (balanced) brackets */
private fun isSingleToken(t: String): Boolean {
    if ('"' in t) {
        // a free text: exactly one pair of quotation marks around everything
        return t.length > 2 && t.first() == '"' && t.last() == '"' && t.count { it == '"' } == 2 &&
            t.substring(1, t.length - 1).isNotBlank()
    }
    // at most one pair of brackets, at the end (the value of a sign), no nested ones
    var depth = 0
    t.forEachIndexed { i, c ->
        when {
            c == '[' -> { if (depth > 0) return false; depth++ }
            c == ']' -> { if (depth == 0) return false; depth--; if (i != t.lastIndex) return false }
            depth == 0 && (c == ',' || c == ';' || c == '=' || c.isWhitespace()) -> return false
        }
    }
    return depth == 0
}
