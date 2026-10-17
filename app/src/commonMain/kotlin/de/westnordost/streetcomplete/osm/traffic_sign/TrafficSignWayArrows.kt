package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.initialBearingTo
import de.westnordost.streetcomplete.util.math.translate

/** A position on a way and the bearing (degrees clockwise from north) of the way segment there,
 *  pointing in the direction in which the way is drawn */
data class WayPointWithBearing(val position: LatLon, val bearing: Double)

/** Positions on the polyline (nodes in OSM order) at the given fractions of its length (0..1), each
 *  with the bearing of the segment it lies on. Following the real geometry means that arrows on
 *  curved ways follow the curve: on each part of the way, the arrow points along the way and not
 *  to a fixed screen direction. */
fun List<LatLon>.pointsWithBearingAt(fractions: List<Double>): List<WayPointWithBearing> {
    if (size < 2) return emptyList()
    val segmentLengths = zipWithNext { a, b -> a.distanceTo(b) }
    val total = segmentLengths.sum()
    if (total <= 0.0) return emptyList()
    return fractions.mapNotNull { fraction ->
        if (fraction < 0.0 || fraction > 1.0) return@mapNotNull null
        var remaining = fraction * total
        for (i in segmentLengths.indices) {
            val length = segmentLengths[i]
            // zero-length segments have no bearing
            if (length <= 0.0) continue
            if (remaining <= length || i == segmentLengths.lastIndex) {
                val start = this[i]
                val bearing = start.initialBearingTo(this[i + 1])
                return@mapNotNull WayPointWithBearing(start.translate(remaining.coerceAtMost(length), bearing), bearing)
            }
            remaining -= length
        }
        null
    }
}

/** Arrow positions for the two directions along a way. Forward arrows point in the direction in
 *  which the way is drawn (first to last node), backward arrows point the opposite way. The two sets
 *  are interleaved along the way so they never overlap. */
fun List<LatLon>.directionArrows(direction: TrafficSignDirection): List<WayPointWithBearing> {
    val fractions = when (direction) {
        TrafficSignDirection.FORWARD -> listOf(0.2, 0.5, 0.8)
        TrafficSignDirection.BACKWARD -> listOf(0.35, 0.65)
    }
    val points = pointsWithBearingAt(fractions)
    return when (direction) {
        TrafficSignDirection.FORWARD -> points
        TrafficSignDirection.BACKWARD -> points.map { it.copy(bearing = (it.bearing + 180.0) % 360.0) }
    }
}
