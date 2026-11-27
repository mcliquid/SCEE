package de.westnordost.streetcomplete.overlays.maxspeed

import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedInterpretation
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedUnit
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedValue

/** Neutral gray for "no binding speed limit tagged" (there is no gray in [OverlayColor]) */
val MaxSpeedMissingColor = Color(0xff9e9e9e)

/** Returns the unit of the number in a zone code (e.g. mph for `GB:zone20`), or null if unknown */
typealias ZoneSpeedUnitOf = (MaxSpeedValue.Code) -> MaxSpeedUnit?

/** The unit of a zone code is only known if the code is of the country the way is in and that
 *  country uses exactly one unit on its speed limit signs */
fun CountryInfo.zoneSpeedUnitOf(code: MaxSpeedValue.Code): MaxSpeedUnit? {
    if (countryCode == null || code.country.substringBefore('-') != countryCode) return null
    return when (speedUnits.singleOrNull()) {
        SpeedMeasurementUnit.KILOMETERS_PER_HOUR -> MaxSpeedUnit.KMH
        SpeedMeasurementUnit.MILES_PER_HOUR -> MaxSpeedUnit.MPH
        null -> null
    }
}

/** Colors:
 *  - numeric limits: purple (≤20, walk), blue (≤30), aquamarine (≤50), lime (≤70), gold (≤100),
 *    orange (>100); solid when explicit, dashed when only given as a zone code (e.g. DE:zone30)
 *  - zone codes whose unit is unknown: gray, dashed, label is the raw code
 *  - none: orange, dashed, label ∞
 *  - variable / signals: teal, dashed
 *  - other country codes (DE:urban): gray, dashed
 *  - missing or only advisory: gray (advisory shown in parentheses in the label)
 *  - invalid or contradictory: red
 *
 *  Directional limits are shown as side lines: the forward value on the side on which traffic in
 *  way direction drives, the backward value on the other one. */
fun maxSpeedWayStyle(
    interpretation: MaxSpeedInterpretation,
    isLeftHandTraffic: Boolean,
    zoneSpeedUnitOf: ZoneSpeedUnitOf,
): OverlayStyle.Polyline {
    val label = maxSpeedLabel(interpretation, zoneSpeedUnitOf)
    if (interpretation.issues.isNotEmpty()) {
        return OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Red), label = label)
    }
    if (!interpretation.isDirectional) {
        val stroke = interpretation.displayedGeneralValue?.stroke(zoneSpeedUnitOf)
            ?: OverlayStyle.Stroke(MaxSpeedMissingColor)
        return OverlayStyle.Polyline(stroke, label = label)
    }
    val forwardStroke = interpretation.forward?.stroke(zoneSpeedUnitOf) ?: OverlayStyle.Stroke(MaxSpeedMissingColor)
    val backwardStroke = interpretation.backward?.stroke(zoneSpeedUnitOf) ?: OverlayStyle.Stroke(MaxSpeedMissingColor)
    return OverlayStyle.Polyline(
        stroke = null,
        strokeLeft = if (isLeftHandTraffic) forwardStroke else backwardStroke,
        strokeRight = if (isLeftHandTraffic) backwardStroke else forwardStroke,
        label = label,
    )
}

/** binding value, otherwise the type of the limit, otherwise the zone the way belongs to */
private val MaxSpeedInterpretation.displayedGeneralValue: MaxSpeedValue? get() =
    forward ?: typeCode ?: zoneCode

/** Label: actual value(s), `*` if additional conditional limits exist, advisory in parentheses,
 *  directional values as `→forward ←backward` (relative to the way's drawing direction) */
fun maxSpeedLabel(interpretation: MaxSpeedInterpretation, zoneSpeedUnitOf: ZoneSpeedUnitOf): String? = with(interpretation) {
    val text = if (isDirectional) {
        val f = directionLabel(forward, hasForwardConditional, forwardAdvisory, zoneSpeedUnitOf) ?: "?"
        val b = directionLabel(backward, hasBackwardConditional, backwardAdvisory, zoneSpeedUnitOf) ?: "?"
        "→$f ←$b"
    } else {
        directionLabel(displayedGeneralValue, hasForwardConditional || hasBackwardConditional, forwardAdvisory ?: backwardAdvisory, zoneSpeedUnitOf)
    }
    if (issues.isEmpty()) text else "! " + (text ?: "?")
}

private fun directionLabel(
    value: MaxSpeedValue?,
    hasConditional: Boolean,
    advisory: MaxSpeedValue?,
    zoneSpeedUnitOf: ZoneSpeedUnitOf,
): String? {
    val parts = mutableListOf<String>()
    if (value != null) {
        parts.add(value.label(zoneSpeedUnitOf) + if (hasConditional) "*" else "")
    } else if (hasConditional) {
        // only conditional limits: do not pretend there is an ordinary value
        parts.add("(*)")
    }
    if (advisory != null) parts.add("(${advisory.label(zoneSpeedUnitOf)})")
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

fun MaxSpeedValue.label(zoneSpeedUnitOf: ZoneSpeedUnitOf): String = when (this) {
    is MaxSpeedValue.Numeric -> when (unit) {
        MaxSpeedUnit.KMH -> "$value"
        MaxSpeedUnit.MPH -> "$value mph"
        MaxSpeedUnit.KNOTS -> "$value kn"
    }
    MaxSpeedValue.Unrestricted -> "∞"
    MaxSpeedValue.Walk -> "walk"
    is MaxSpeedValue.Variable -> raw
    is MaxSpeedValue.Code -> {
        // unknown unit: show the code as it is rather than a number that looks like km/h
        val unit = zoneSpeed?.let { zoneSpeedUnitOf(this) }
        when (unit) {
            MaxSpeedUnit.KMH -> "Z$zoneSpeed"
            MaxSpeedUnit.MPH -> "Z$zoneSpeed mph"
            MaxSpeedUnit.KNOTS -> "Z$zoneSpeed kn"
            null -> raw
        }
    }
    is MaxSpeedValue.Invalid -> raw
}

private fun MaxSpeedValue.stroke(zoneSpeedUnitOf: ZoneSpeedUnitOf): OverlayStyle.Stroke = when (this) {
    is MaxSpeedValue.Numeric -> OverlayStyle.Stroke(speedColor(kmh))
    MaxSpeedValue.Walk -> OverlayStyle.Stroke(OverlayColor.Purple)
    MaxSpeedValue.Unrestricted -> OverlayStyle.Stroke(OverlayColor.Orange, dashed = true)
    is MaxSpeedValue.Variable -> OverlayStyle.Stroke(OverlayColor.Teal, dashed = true)
    is MaxSpeedValue.Code -> {
        val unit = zoneSpeed?.let { zoneSpeedUnitOf(this) }
        if (zoneSpeed != null && unit != null) OverlayStyle.Stroke(speedColor(zoneSpeed * unit.kmhFactor), dashed = true)
        else OverlayStyle.Stroke(MaxSpeedMissingColor, dashed = true)
    }
    is MaxSpeedValue.Invalid -> OverlayStyle.Stroke(OverlayColor.Red)
}

/** small tolerance so that e.g. 30 mph (48.3 km/h) is classified as ≤ 50 */
fun speedColor(kmh: Double): Color = when {
    kmh <= 20.5 -> OverlayColor.Purple
    kmh <= 30.5 -> OverlayColor.Blue
    kmh <= 50.5 -> OverlayColor.Aquamarine
    kmh <= 70.5 -> OverlayColor.Lime
    kmh <= 100.5 -> OverlayColor.Gold
    else -> OverlayColor.Orange
}
