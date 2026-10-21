package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Immutable
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.ui.common.quest.Marker
import kotlin.math.abs

/** SCEE: other quests near the opened quest, as map markers and as colored groups for the side list */
@Immutable
data class NearbyQuests(
    val markers: List<Marker>,
    /** (color, quests) per element, same element as the opened quest first */
    val groups: List<Pair<Int, List<Quest>>>,
) {
    companion object { val EMPTY = NearbyQuests(emptyList(), emptyList()) }
}

private const val WHITE = -1 // quests of the opened element: listed, but no marker
private val NEARBY_COLORS = intArrayOf(
    0xFF00FF00.toInt(), // green
    0xFFFFFF00.toInt(), // yellow
    0xFF00FFFF.toInt(), // cyan
    0xFFFF00FF.toInt(), // magenta
    0xFF0000FF.toInt(), // blue
    0xFFFF7F00.toInt(), // orange
)

/** Reads the nearby quest preferences and asks [getNearby] (mode handling is in VisibleQuestsSource). */
fun findNearbyQuests(
    quest: Quest,
    prefs: Preferences,
    getNearby: (Quest, Double) -> Collection<Quest>,
): NearbyQuests {
    if (prefs.getInt(Prefs.SHOW_NEARBY_QUESTS, 0) == 0) return NearbyQuests.EMPTY
    val distance = prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 0f).toDouble() + 0.01
    return buildNearbyQuests(quest, getNearby(quest, distance))
}

fun buildNearbyQuests(current: Quest, nearby: Collection<Quest>): NearbyQuests {
    val currentElement = current.elementKeyForGrouping()
    // current quest and POI dots are not interesting
    val quests = nearby
        .filterNot { it == current || it.type.dotColor != null }
        .sortedBy { it.elementKeyForGrouping() != currentElement }
    if (quests.isEmpty()) return NearbyQuests.EMPTY

    val groups = LinkedHashMap<ElementKey, Pair<Int, MutableList<Quest>>>()
    val markers = ArrayList<Marker>()
    var colorIndex = 0
    for (quest in quests) {
        groups.getOrPut(quest.elementKeyForGrouping()) {
            val color = if (quest.elementKeyForGrouping() == currentElement) {
                WHITE
            } else {
                NEARBY_COLORS[colorIndex++ % NEARBY_COLORS.size] // cycle if there are many elements
            }
            if (color != WHITE) markers.add(Marker(quest.geometry, color = color))
            color to mutableListOf()
        }.second.add(quest)
    }
    return NearbyQuests(markers, groups.values.map { it.first to it.second.toList() })
}

/** If a highlight marker and a quest marker are at the same position, the highlight marker takes the quest color. */
fun mergeMarkersAtSamePosition(highlightMarkers: List<Marker>, questMarkers: List<Marker>): List<Marker> {
    if (questMarkers.isEmpty()) return highlightMarkers
    val merged = LinkedHashMap<LatLon, Marker>()
    highlightMarkers.associateByTo(merged) { it.geometry.center }
    for (questMarker in questMarkers) {
        val highlight = merged[questMarker.geometry.center]
        merged[questMarker.geometry.center] =
            if (highlight == null) questMarker else highlight.copy(color = questMarker.color)
    }
    return merged.values.toList()
}

// non-OSM quests get a pseudo element key, so that each of them is its own group
private fun Quest.elementKeyForGrouping(): ElementKey =
    if (this is OsmQuest) ElementKey(elementType, elementId)
    else ElementKey(ElementType.entries[abs(key.hashCode() % 3)], -abs(7 * key.hashCode()).toLong())
