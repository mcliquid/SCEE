package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.osm.level.levelsIntersect
import de.westnordost.streetcomplete.osm.level.parseLevelsOrNull
import de.westnordost.streetcomplete.ui.common.quest.Marker
import org.jetbrains.compose.resources.DrawableResource

/**
 * Markers for the elements a quest wants to highlight. Without a [reference] element (external source
 * quests that are not linked to one), nothing can be compared, so no level / layer filtering happens.
 */
fun highlightedMarkers(
    highlighted: Sequence<Element>,
    reference: Element?,
    hideOnDifferentLevel: Boolean,
    getGeometry: (Element) -> ElementGeometry?,
    getIcon: (Element) -> DrawableResource?,
    getTitle: (Element) -> String?,
): List<Marker> {
    val levels = reference?.let { parseLevelsOrNull(it.tags) }
    return highlighted.mapNotNull { other ->
        if (reference == other) return@mapNotNull null
        if (reference != null && hideOnDifferentLevel) {
            if (!levels.levelsIntersect(parseLevelsOrNull(other.tags))) return@mapNotNull null
            if (reference.tags["layer"] != other.tags["layer"]) return@mapNotNull null
        }
        val geometry = getGeometry(other) ?: return@mapNotNull null
        Marker(geometry, getIcon(other), getTitle(other))
    }.toList()
}
