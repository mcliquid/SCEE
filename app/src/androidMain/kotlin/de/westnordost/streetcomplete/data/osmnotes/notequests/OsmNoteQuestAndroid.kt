package de.westnordost.streetcomplete.data.osmnotes.notequests

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.Quest
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.quests.note_discussion.OsmNoteQuestType

/** Represents one task for the user to contribute to a public OSM note */
data class OsmNoteQuestAndroid(
    override val id: Long,
    override val position: LatLon
) : OsmNoteQuest() {
    override val type: QuestType get() = OsmNoteQuestType
    override val key: OsmNoteQuestKey = OsmNoteQuestKey(id)
    override val markerLocations: Collection<LatLon> get() = listOf(position)
    override val geometry: ElementGeometry get() = ElementPointGeometry(position)
}

actual fun createOsmNoteQuest(id: Long, position: LatLon): OsmNoteQuest =
    OsmNoteQuestAndroid(id, position)
