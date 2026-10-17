package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

/** What is tagged for one direction of a way, judged from the tags only (no catalogue needed, so
 *  this is cheap for many ways). Shared by the traffic sign overlay and quest. */
enum class TrafficSignTagState {
    /** nothing tagged. Strictly not the same as [NONE] */
    NOT_SURVEYED,
    /** only traffic_sign=yes: there are signs, but which ones is not said */
    UNSPECIFIC,
    /** traffic sign explicitly tagged as "none" (or "no") */
    NONE,
    /** any other value, also named values (maxspeed, ...) and values that can not be read */
    SIGNED;

    /** whether the direction still has to be surveyed */
    val isIncomplete: Boolean get() = this == NOT_SURVEYED || this == UNSPECIFIC
}

/** The effective value of a direction is the directional one, otherwise the general one. Empty or
 *  blank tags count as not set, see [effectiveTrafficSignValue] */
fun trafficSignTagState(tags: Map<String, String>, direction: TrafficSignDirection): TrafficSignTagState {
    val value = effectiveTrafficSignValue(tags, direction)?.trim()
    return when {
        value.isNullOrEmpty() -> TrafficSignTagState.NOT_SURVEYED
        value.equals("yes", ignoreCase = true) -> TrafficSignTagState.UNSPECIFIC
        value.equals("none", ignoreCase = true) || value.equals("no", ignoreCase = true) -> TrafficSignTagState.NONE
        else -> TrafficSignTagState.SIGNED
    }
}

/** Whether at least one of the two directions (of the OSM way, regardless of oneway=*) is not
 *  surveyed yet or only has traffic_sign=yes */
fun needsTrafficSignSurvey(tags: Map<String, String>): Boolean =
    TrafficSignDirection.entries.any { trafficSignTagState(tags, it).isIncomplete }

/** Whether the way (center, first and last point) is completely in Germany. Ways that cross the
 *  border are excluded: their signs would partly be of another country, whose catalogue is not
 *  available. [getCountryCode] may return a subdivision code ("DE-BY"). */
fun isWayInGermany(geometry: ElementGeometry?, getCountryCode: (LatLon) -> String?): Boolean {
    if (geometry !is ElementPolylinesGeometry) return false
    val line = geometry.polylines.singleOrNull() ?: return false
    if (line.size < 2) return false
    return listOf(geometry.center, line.first(), line.last()).all {
        val code = getCountryCode(it)
        code == "DE" || code != null && code.startsWith("DE-")
    }
}
