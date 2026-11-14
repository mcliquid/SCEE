package de.westnordost.streetcomplete.quests.contract

import de.westnordost.streetcomplete.quests.seating.AddOutdoorSeatingType
import de.westnordost.streetcomplete.quests.seating.AddSeating
import de.westnordost.streetcomplete.quests.seating.Seating
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway
import de.westnordost.streetcomplete.osm.cycleway.CyclewayAndDirection
import de.westnordost.streetcomplete.osm.oneway.Direction
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.cycleway_track_surface.CyclewayTrackSurfaceAnswer
import de.westnordost.streetcomplete.quests.cycleway.AddCycleway
import de.westnordost.streetcomplete.quests.surface.AddRoadAttachedCyclewaySurface
import kotlin.test.Test
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossQuestScenarioTest {
    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    @Test fun `cycleway removal clears a previously surveyed track surface`() {
        val surface = AddRoadAttachedCyclewaySurface()
        val cycleway = AddCycleway { CountryInfo(null, listOf(IncompleteCountryInfo(isLeftHandTraffic = false))) }
        val before = mapOf("highway" to "residential", "cycleway:right" to "track", "note" to "keep")
        assertTrue(surface.isApplicableTo(way(tags = before)))
        val surveyed = surface.answeredTags(CyclewayTrackSurfaceAnswer(
            mapOf("cycleway:right:surface" to Surface.ASPHALT)), before)
        assertFalse(surface.isApplicableTo(way(tags = surveyed)))
        val after = cycleway.answeredTags(Sides(null,
            CyclewayAndDirection(Cycleway.NONE, Direction.FORWARD)), surveyed)
        assertNull(after["cycleway:right:surface"])
        assertEquals("keep", after["note"])
        assertFalse(surface.isApplicableTo(way(tags = after)))
    }

    @Test fun `seating and outdoor type stay closed in either answer order`() {
        val seating = AddSeating()
        val type = AddOutdoorSeatingType()
        val before = mapOf("amenity" to "cafe", "outdoor_seating" to "yes", "note" to "keep")
        assertTrue(seating.isApplicableTo(node(tags = before)))
        assertTrue(type.isApplicableTo(node(tags = before)))
        val seatingThenType = type.answeredTags("garden",
            seating.answeredTags(Seating.INDOOR_AND_OUTDOOR, before))
        val typeThenSeating = seating.answeredTags(Seating.INDOOR_AND_OUTDOOR,
            type.answeredTags("garden", before))
        assertEquals(seatingThenType, typeThenSeating)
        assertEquals("garden", typeThenSeating["outdoor_seating"])
        assertEquals("keep", typeThenSeating["note"])
        for (after in listOf(seatingThenType, typeThenSeating)) {
            assertFalse(seating.isApplicableTo(node(tags = after)))
            assertFalse(type.isApplicableTo(node(tags = after)))
        }
    }

    @Test fun `each seating combination changes only the surveyed seating state`() {
        val quest = AddSeating()
        for (answer in Seating.entries) {
            val after = quest.answeredTags(answer,
                mapOf("amenity" to "cafe", "outdoor_seating" to "terrace", "note" to "keep"))
            assertEquals(if (answer.hasOutdoorSeating) "terrace" else "no", after["outdoor_seating"])
            assertEquals(if (answer.hasIndoorSeating) "yes" else "no", after["indoor_seating"])
            assertEquals(if (answer == Seating.TAKEAWAY_ONLY) "only" else null, after["takeaway"])
            assertEquals("keep", after["note"])
            assertFalse(quest.isApplicableTo(node(tags = after)))
        }
    }
}
