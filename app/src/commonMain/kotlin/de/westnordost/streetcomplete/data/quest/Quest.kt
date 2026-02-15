package de.westnordost.streetcomplete.data.quest

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.screens.main.map.components.Pin

/** Represents one task for the user to complete/correct  */
abstract class Quest {
    /** Key with which to uniquely identify a quest */
    abstract val key: QuestKey
    /** Position where the quest is at */
    abstract val position: LatLon
    /** Position(s) where the pins for the quest should be put. E.g. a quest on some long way could
     *  have multiple markers along the way instead just on the center. */
    abstract val markerLocations: Collection<LatLon>
    /** Geometry of the element this quest refers to. It is highlighted when selecting the quest. */
    abstract val geometry: ElementGeometry
    /** The type of the quest */
    abstract val type: QuestType

    /** caching pins in the quest allows for faster setting of pins */
    var pins: List<Pin>? = null
}
