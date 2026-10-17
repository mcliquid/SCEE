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
fun List<TrafficSignGroup>.withMainSign(part: TrafficSignPart): List<TrafficSignGroup> =
    this + TrafficSignGroup(part)

fun List<TrafficSignGroup>.withModifier(groupIndex: Int, part: TrafficSignPart): List<TrafficSignGroup> =
    mapIndexed { i, g -> if (i == groupIndex) g.copy(modifiers = g.modifiers + part) else g }

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

/** A sign entered by its number, e.g. "1010-50". Returns null if it is not a single sign that can be
 *  written into a traffic_sign value. Signs that are not in the catalogue are returned as unknown
 *  parts, so that unusual signs can still be recorded. */
fun parseCustomTrafficSign(text: String, catalogue: TrafficSignCatalogue, asModifier: Boolean): TrafficSignPart? {
    val t = text.trim().removePrefix("${catalogue.country}:").trim()
    if (t.isEmpty() || t.any { it == ',' || it == ';' || it == '=' || it.isWhitespace() && !t.startsWith("\"") }) return null
    val value = parseTrafficSignValue("${catalogue.country}:$t", catalogue)
    val part = value.parts.singleOrNull() ?: return null
    if (value.isUnparseable || part.isNamedValue || part.isMissingValue || part.hasUnexpectedValue) return null
    // unknown signs take the kind of the place they are added to
    return if (part.sign == null) part.copy(kind = if (asModifier) TrafficSignKind.EXCEPTION_MODIFIER else TrafficSignKind.TRAFFIC_SIGN) else part
}
