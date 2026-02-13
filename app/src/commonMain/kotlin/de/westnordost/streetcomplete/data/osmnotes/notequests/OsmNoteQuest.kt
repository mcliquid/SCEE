package de.westnordost.streetcomplete.data.osmnotes.notequests

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.Quest

// TODO multiplatform: this indirection can be removed once OsmNoteQuest is moved to common
abstract class OsmNoteQuest : Quest() {
    abstract val id: Long
}

expect fun createOsmNoteQuest(id: Long, position: LatLon): OsmNoteQuest
