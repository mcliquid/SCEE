package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection

/** What is tagged for one direction of a way, judged from the tags only (no catalogue needed, so
 *  this is cheap for many ways) */
enum class TrafficSignTagState {
    /** nothing tagged. Strictly not the same as [NONE] */
    NOT_SURVEYED,
    /** traffic sign explicitly tagged as "none" */
    NONE,
    /** any other value, also values that can not be read */
    SIGNED,
}

fun trafficSignTagState(tags: Map<String, String>, direction: TrafficSignDirection): TrafficSignTagState {
    val value = (tags["traffic_sign:${direction.osmSuffix}"] ?: tags["traffic_sign"])?.trim()
    return when {
        value.isNullOrEmpty() -> TrafficSignTagState.NOT_SURVEYED
        value.equals("none", ignoreCase = true) || value.equals("no", ignoreCase = true) -> TrafficSignTagState.NONE
        else -> TrafficSignTagState.SIGNED
    }
}

/** Gray for "explicitly no signs in both directions" (there is no gray in [OverlayColor]) */
val NoSignsColor = Color(0xff9e9e9e)

/** Colors:
 *  - red: nothing surveyed in either direction
 *  - orange, dashed: only one direction surveyed
 *  - green (lime): both directions surveyed, both with signs
 *  - gray: both directions explicitly without signs
 *  - teal (also green family = complete): both surveyed, one with signs and one without */
fun trafficSignWayStyle(tags: Map<String, String>): OverlayStyle.Polyline {
    val forward = trafficSignTagState(tags, TrafficSignDirection.FORWARD)
    val backward = trafficSignTagState(tags, TrafficSignDirection.BACKWARD)
    val notSurveyed = listOf(forward, backward).count { it == TrafficSignTagState.NOT_SURVEYED }
    val stroke = when {
        notSurveyed == 2 -> OverlayStyle.Stroke(OverlayColor.Red)
        notSurveyed == 1 -> OverlayStyle.Stroke(OverlayColor.Orange, dashed = true)
        forward == TrafficSignTagState.NONE && backward == TrafficSignTagState.NONE ->
            OverlayStyle.Stroke(NoSignsColor)
        forward == TrafficSignTagState.SIGNED && backward == TrafficSignTagState.SIGNED ->
            OverlayStyle.Stroke(OverlayColor.Lime)
        else -> OverlayStyle.Stroke(OverlayColor.Teal)
    }
    return OverlayStyle.Polyline(stroke)
}
