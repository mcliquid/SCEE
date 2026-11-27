package de.westnordost.streetcomplete.osm.maxspeed

/** Read-only interpretation of the speed limit tagging of a way, for display and QA purposes.
 *  Never infers a legal default from the road classification. */

enum class MaxSpeedUnit(val osmSuffix: String, val kmhFactor: Double) {
    KMH("km/h", 1.0),
    MPH("mph", 1.609344),
    KNOTS("knots", 1.852),
}

sealed interface MaxSpeedValue {
    /** e.g. `50`, `30 mph`. [unitWasExplicit] is true for `50 km/h` */
    data class Numeric(val value: Int, val unit: MaxSpeedUnit, val unitWasExplicit: Boolean = false) : MaxSpeedValue {
        val kmh: Double get() = value * unit.kmhFactor
    }
    /** `none`: explicitly no fixed limit. Not the same as missing data! */
    data object Unrestricted : MaxSpeedValue
    /** `walk`: walking speed */
    data object Walk : MaxSpeedValue
    /** `signals` (legacy) or `variable`: limit set by variable message signs */
    data class Variable(val raw: String) : MaxSpeedValue
    /** Country specific code like `DE:urban`, `DE:zone30`, `DE:zone:30`. [zoneSpeed] is only set
     *  for zone codes that literally contain the speed. [isKnownType] is false for syntactically
     *  plausible codes with an unknown context part */
    data class Code(
        val raw: String,
        val country: String,
        val type: String,
        val zoneSpeed: Int?,
        val isKnownType: Boolean,
    ) : MaxSpeedValue
    /** Malformed value or unknown unit */
    data class Invalid(val raw: String) : MaxSpeedValue
}

enum class MaxSpeedIssue {
    /** a numeric limit contradicts the speed of a zone type code (e.g. `maxspeed=50` + `maxspeed:type=DE:zone30`).
     *  Not raised for `zone:maxspeed`, which is only the zone membership */
    ZONE_SPEED_MISMATCH,
    /** `maxspeed=none` together with a zone type code */
    UNRESTRICTED_IN_ZONE,
    /** any of the speed values could not be parsed */
    INVALID_VALUE,
    /** `maxspeed`, `maxspeed:type` and `source:maxspeed` name different speed limit codes
     *  (e.g. `maxspeed:type=PL:urban` + `source:maxspeed=PL:rural`) */
    TYPE_CONFLICT,
}

data class MaxSpeedInterpretation(
    /** binding limit in way direction; null = not tagged */
    val forward: MaxSpeedValue?,
    /** binding limit against way direction; null = not tagged */
    val backward: MaxSpeedValue?,
    /** where the limit comes from: the speed limit code in `maxspeed:type` or `source:maxspeed`.
     *  null if not tagged or if these keys contradict each other ([MaxSpeedIssue.TYPE_CONFLICT]) */
    val typeCode: MaxSpeedValue.Code?,
    /** zone the way belongs to (`zone:maxspeed`). Membership only: a lower signed limit within the
     *  zone does not contradict it */
    val zoneCode: MaxSpeedValue.Code?,
    val forwardAdvisory: MaxSpeedValue?,
    val backwardAdvisory: MaxSpeedValue?,
    val hasForwardConditional: Boolean,
    val hasBackwardConditional: Boolean,
    val issues: Set<MaxSpeedIssue>,
) {
    val isDirectional: Boolean get() = forward != backward
    val hasBindingValue: Boolean get() = forward != null || backward != null
    val hasAdvisory: Boolean get() = forwardAdvisory != null || backwardAdvisory != null
    val hasConditional: Boolean get() = hasForwardConditional || hasBackwardConditional
    val isMissing: Boolean get() =
        !hasBindingValue && typeCode == null && zoneCode == null && !hasAdvisory && !hasConditional && issues.isEmpty()
}

fun interpretMaxSpeed(tags: Map<String, String>): MaxSpeedInterpretation {
    val general = tags["maxspeed"]?.let(::parseMaxSpeedValue)
    // the more specific directional value wins over the general one
    val forward = tags["maxspeed:forward"]?.let(::parseMaxSpeedValue) ?: general
    val backward = tags["maxspeed:backward"]?.let(::parseMaxSpeedValue) ?: general

    val advisory = tags["maxspeed:advisory"]?.let(::parseMaxSpeedValue)
    val forwardAdvisory = tags["maxspeed:advisory:forward"]?.let(::parseMaxSpeedValue) ?: advisory
    val backwardAdvisory = tags["maxspeed:advisory:backward"]?.let(::parseMaxSpeedValue) ?: advisory

    // values that are no code (sign, free text, URLs, ...) say nothing about the type
    val typeCodes = TYPE_KEYS.mapNotNull { key -> tags[key]?.let { parseMaxSpeedValue(it) as? MaxSpeedValue.Code } }
    val allCodes = listOfNotNull(general as? MaxSpeedValue.Code) + typeCodes
    val hasTypeConflict = allCodes.map { it.meaning() }.distinct().size > 1
    val typeCode = if (hasTypeConflict) null else typeCodes.firstOrNull()

    val zoneCode = tags["zone:maxspeed"]?.let { parseMaxSpeedValue(normalizeZoneMaxSpeed(it)) as? MaxSpeedValue.Code }

    val generalConditional = tags.containsKey("maxspeed:conditional")
    val hasForwardConditional = generalConditional || tags.containsKey("maxspeed:forward:conditional")
    val hasBackwardConditional = generalConditional || tags.containsKey("maxspeed:backward:conditional")

    val issues = mutableSetOf<MaxSpeedIssue>()
    val values = listOfNotNull(forward, backward, forwardAdvisory, backwardAdvisory)
    if (values.any { it is MaxSpeedValue.Invalid }) issues.add(MaxSpeedIssue.INVALID_VALUE)

    if (hasTypeConflict) issues.add(MaxSpeedIssue.TYPE_CONFLICT)

    val numericKmh = (general as? MaxSpeedValue.Numeric)?.takeIf { it.unit == MaxSpeedUnit.KMH }?.value
    // the type says the limit *is* the zone speed: any other number contradicts it
    val typeZoneSpeed = typeCode?.zoneSpeed ?: (general as? MaxSpeedValue.Code)?.zoneSpeed
    if (typeZoneSpeed != null) {
        if (numericKmh != null && numericKmh != typeZoneSpeed) issues.add(MaxSpeedIssue.ZONE_SPEED_MISMATCH)
        if (general == MaxSpeedValue.Unrestricted) issues.add(MaxSpeedIssue.UNRESTRICTED_IN_ZONE)
    }
    // zone membership (`zone:maxspeed`) is never compared with the limit: a different local limit
    // within the zone does not prove an error

    return MaxSpeedInterpretation(
        forward = forward,
        backward = backward,
        typeCode = typeCode,
        zoneCode = zoneCode,
        forwardAdvisory = forwardAdvisory,
        backwardAdvisory = backwardAdvisory,
        hasForwardConditional = hasForwardConditional,
        hasBackwardConditional = hasBackwardConditional,
        issues = issues,
    )
}

fun parseMaxSpeedValue(value: String): MaxSpeedValue {
    val v = value.trim()
    when (v) {
        "none" -> return MaxSpeedValue.Unrestricted
        "walk" -> return MaxSpeedValue.Walk
        "signals", "variable" -> return MaxSpeedValue.Variable(v)
    }
    NUMERIC_REGEX.matchEntire(v)?.let { match ->
        val number = match.groupValues[1].toIntOrNull() ?: return MaxSpeedValue.Invalid(v)
        if (number <= 0) return MaxSpeedValue.Invalid(v)
        val unitString = match.groupValues[2]
        val unit = when (unitString) {
            "" -> MaxSpeedUnit.KMH
            else -> MaxSpeedUnit.entries.find { it.osmSuffix == unitString }
                ?: return MaxSpeedValue.Invalid(v)
        }
        return MaxSpeedValue.Numeric(number, unit, unitWasExplicit = unitString.isNotEmpty())
    }
    CODE_REGEX.matchEntire(v)?.let { match ->
        val country = match.groupValues[1]
        val type = match.groupValues[2]
        val zoneSpeed = ZONE_REGEX.matchEntire(type)?.groupValues?.get(1)?.toIntOrNull()
        val isKnownType = zoneSpeed != null || type in KNOWN_CODE_TYPES
        return MaxSpeedValue.Code(v, country, type, zoneSpeed, isKnownType)
    }
    return MaxSpeedValue.Invalid(v)
}

/** `zone:maxspeed=DE:30` names the same zone as `maxspeed:type=DE:zone30` */
private fun normalizeZoneMaxSpeed(value: String): String {
    val match = ZONE_MAXSPEED_NUMBER_REGEX.matchEntire(value) ?: return value
    return "${match.groupValues[1]}:zone${match.groupValues[2]}"
}

/** codes that mean the same compare equal, e.g. `DE:zone30` and `DE:zone:30` */
private fun MaxSpeedValue.Code.meaning(): String =
    if (zoneSpeed != null) "$country:zone$zoneSpeed" else "$country:$type"

/** keys that say where the current limit comes from. `zone:maxspeed` and `zone:traffic` are zone
 *  memberships and deliberately not part of this */
private val TYPE_KEYS = listOf("maxspeed:type", "source:maxspeed")

/** number, optionally followed by a space and a unit. Everything with digits but another format is
 *  not considered numeric */
private val NUMERIC_REGEX = Regex("""([0-9]+)(?: ([a-z/]+))?""")
private val CODE_REGEX = Regex("""([A-Z]{2}(?:-[A-Z0-9]{1,3})?):([a-z_]+:?[a-z0-9_]*)""")
private val ZONE_REGEX = Regex("""zone:?([0-9]{1,3})""")
private val ZONE_MAXSPEED_NUMBER_REGEX = Regex("""([A-Z]{2}(?:-[A-Z0-9]{1,3})?):([0-9]{1,3})""")

private val KNOWN_CODE_TYPES = setOf(
    "urban", "rural", "motorway", "trunk", "living_street", "bicycle_road", "school",
    "nsl_single", "nsl_dual", "nsl_restricted", "express", "motorroad", "pedestrian_zone",
    "zone",
)
