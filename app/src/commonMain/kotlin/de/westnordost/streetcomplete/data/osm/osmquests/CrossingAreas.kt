package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.distanceTo

/** Bounded, run-local groups, never a cached approximation of the queried area. Large anchors
 * and antimeridian areas keep their original query; no region is expanded without a size limit. */
internal fun crossingAreaGroups(areas: List<BoundingBox>): List<List<BoundingBox>> {
    val groups = mutableListOf<MutableList<BoundingBox>>()
    val extents = mutableListOf<BoundingBox>()
    for (area in areas) {
        var selected = -1
        var extent = area
        if (!area.crosses180thMeridian) for (i in groups.indices) {
            val old = extents[i]
            if (groups[i].size >= 64 || old.crosses180thMeridian) continue
            val combined = BoundingBox(minOf(old.min.latitude, area.min.latitude), minOf(old.min.longitude, area.min.longitude),
                maxOf(old.max.latitude, area.max.latitude), maxOf(old.max.longitude, area.max.longitude))
            if (combined.max.longitude - combined.min.longitude > 180.0
                || combined.enclosingTilesRect(16).size > 64) continue
            if (combined.min.distanceTo(LatLon(combined.max.latitude, combined.min.longitude)) > 2000.0
                || combined.min.distanceTo(LatLon(combined.min.latitude, combined.max.longitude)) > 2000.0) continue
            selected = i
            extent = combined
            break
        }
        if (selected < 0) { groups.add(mutableListOf(area)); extents.add(area) }
        else { groups[selected].add(area); extents[selected] = extent }
    }
    return groups
}
