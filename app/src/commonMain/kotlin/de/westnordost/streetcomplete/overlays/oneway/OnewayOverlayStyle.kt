package de.westnordost.streetcomplete.overlays.oneway

import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.overlays.OverlayStyle.ArrowDirection
import de.westnordost.streetcomplete.data.overlays.OverlayStyle.ArrowShape
import de.westnordost.streetcomplete.osm.oneway.BicycleDirectionSource
import de.westnordost.streetcomplete.osm.oneway.OnewayInterpretation
import de.westnordost.streetcomplete.osm.oneway.OnewayIssue
import de.westnordost.streetcomplete.osm.oneway.TravelDirection

enum class OnewayCategory {
    /** no directional restriction (for bicycles): not highlighted */
    NONE,
    /** oneway that also applies to bicycles */
    GENERAL_ONEWAY,
    /** oneway, but bicycles may ride in both directions (oneway:bicycle=no) */
    BICYCLE_EXEMPT,
    /** like [BICYCLE_EXEMPT], but only inferred from cycleway tagging */
    BICYCLE_EXEMPT_INFERRED,
    /** oneway only for bicycles */
    BICYCLE_ONLY,
    /** a oneway on which bicycles are not allowed */
    NO_BICYCLE_ACCESS,
    /** junction=circular without oneway */
    UNCERTAIN,
    /** contradictory or undocumented values */
    CONFLICT,
}

fun OnewayInterpretation.category(): OnewayCategory {
    if (OnewayIssue.CONFLICT in issues || OnewayIssue.UNKNOWN_VALUE in issues) return OnewayCategory.CONFLICT
    val isAnyOneway = isGeneralOneway || motorVehicle.isOneway
    val bicycle = bicycle ?: return if (isAnyOneway) OnewayCategory.NO_BICYCLE_ACCESS else OnewayCategory.NONE
    if (OnewayIssue.CIRCULAR_WITHOUT_ONEWAY in issues) return OnewayCategory.UNCERTAIN
    return when (bicycle) {
        TravelDirection.FORWARD, TravelDirection.BACKWARD ->
            if (bicycleSource != BicycleDirectionSource.EXPLICIT || general == bicycle) OnewayCategory.GENERAL_ONEWAY
            else OnewayCategory.BICYCLE_ONLY
        TravelDirection.BOTH -> when {
            !isAnyOneway -> OnewayCategory.NONE
            bicycleSource == BicycleDirectionSource.EXPLICIT -> OnewayCategory.BICYCLE_EXEMPT
            else -> OnewayCategory.BICYCLE_EXEMPT_INFERRED
        }
        TravelDirection.UNKNOWN -> OnewayCategory.CONFLICT
    }
}

/** Gray for uncertain (there is no gray in [OverlayColor]) */
val OnewayUncertainColor = Color(0xff9e9e9e)

/** Colors and arrows:
 *  - orange, solid arrows: oneway, also for bicycles
 *  - blue, double arrows: oneway, bicycles exempt (dashed if only inferred)
 *  - purple, open arrows: oneway only for bicycles
 *  - black, no arrows: oneway on which bicycles are not allowed
 *  - red, no arrows: contradictory or invalid
 *  - gray dashed: junction=circular without oneway
 *  - nothing: no restriction (still selectable)
 *  Label `*`: conditional oneway restrictions exist */
fun onewayWayStyle(interpretation: OnewayInterpretation): OverlayStyle.Polyline {
    val label = if (interpretation.isConditional) "*" else null
    val direction = interpretation.bicycle?.toArrowDirection()
    return when (interpretation.category()) {
        OnewayCategory.NONE ->
            OverlayStyle.Polyline(null, label = label)
        OnewayCategory.GENERAL_ONEWAY -> OverlayStyle.Polyline(
            OverlayStyle.Stroke(OverlayColor.Orange), label = label,
            arrows = direction?.let { OverlayStyle.Arrows(it, OverlayColor.Orange, ArrowShape.SOLID) },
        )
        OnewayCategory.BICYCLE_EXEMPT -> OverlayStyle.Polyline(
            OverlayStyle.Stroke(OverlayColor.Blue), label = label,
            arrows = OverlayStyle.Arrows(ArrowDirection.BOTH, OverlayColor.Blue),
        )
        OnewayCategory.BICYCLE_EXEMPT_INFERRED -> OverlayStyle.Polyline(
            OverlayStyle.Stroke(OverlayColor.Blue, dashed = true), label = label,
            arrows = OverlayStyle.Arrows(ArrowDirection.BOTH, OverlayColor.Blue),
        )
        OnewayCategory.BICYCLE_ONLY -> OverlayStyle.Polyline(
            OverlayStyle.Stroke(OverlayColor.Purple), label = label,
            arrows = direction?.let { OverlayStyle.Arrows(it, OverlayColor.Purple, ArrowShape.OPEN) },
        )
        OnewayCategory.NO_BICYCLE_ACCESS ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Black), label = label)
        OnewayCategory.UNCERTAIN ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(OnewayUncertainColor, dashed = true), label = label)
        OnewayCategory.CONFLICT ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Red), label = label)
    }
}

private val TravelDirection.isOneway get() = this == TravelDirection.FORWARD || this == TravelDirection.BACKWARD

private fun TravelDirection.toArrowDirection(): ArrowDirection? = when (this) {
    TravelDirection.FORWARD -> ArrowDirection.FORWARD
    TravelDirection.BACKWARD -> ArrowDirection.BACKWARD
    TravelDirection.BOTH -> ArrowDirection.BOTH
    TravelDirection.UNKNOWN -> null
}
