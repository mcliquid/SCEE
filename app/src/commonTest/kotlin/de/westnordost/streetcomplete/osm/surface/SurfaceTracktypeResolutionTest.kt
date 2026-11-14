package de.westnordost.streetcomplete.osm.surface

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.surface.Surface.*
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.quests.contract.answeredTags
import de.westnordost.streetcomplete.quests.surface.AddRoadSurface
import de.westnordost.streetcomplete.quests.tracktype.AddTracktype
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceTracktypeResolutionTest {
    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
    }

    @Test fun `matrix detects compacted grade1 as consistent`() {
        assertFalse(hasSurfaceTracktypeConflict("compacted", "grade1"))
    }

    @Test fun `matrix flags asphalt grade3`() {
        assertTrue(hasSurfaceTracktypeConflict("asphalt", "grade3"))
    }

    @Test fun `material only does not delete tracktype`() {
        assertEquals(
            setOf(
                StringMapEntryModify("surface", "asphalt", "asphalt"),
                StringMapEntryAdd("check_date:surface", nowAsCheckDateString()),
            ),
            SurfaceTracktypeResolution.MaterialOnly(ASPHALT).appliedTo(
                mapOf(
                    "highway" to "track",
                    "surface" to "asphalt",
                    "tracktype" to "grade3",
                )
            )
        )
    }

    @Test fun `paired correction updates both atomically`() {
        val changes = SurfaceTracktypeResolution.PairedCorrection(ASPHALT, "grade1").appliedTo(
            mapOf("highway" to "track", "surface" to "gravel", "tracktype" to "grade3")
        )
        assertTrue(changes.any { it is StringMapEntryModify && it.key == "surface" })
        assertTrue(changes.any { it is StringMapEntryModify && it.key == "tracktype" && it.value == "grade1" })
        assertFalse(changes.any { it is StringMapEntryDelete && it.key == "note" })
    }

    @Test fun `justified exception only refreshes check dates`() {
        val before = mapOf(
            "highway" to "track",
            "surface" to "asphalt",
            "tracktype" to "grade3",
            "note" to "keep",
        )
        val changes = SurfaceTracktypeResolution.JustifiedException(ASPHALT, "grade3").appliedTo(before)
        assertTrue(changes.any { it.key == "check_date:surface" })
        assertTrue(changes.any { it.key == "check_date:tracktype" })
        assertFalse(changes.any { it is StringMapEntryDelete })
        assertFalse(changes.any { it is StringMapEntryModify && it.key == "tracktype" && it.value != "grade3" })
    }

    @Test fun `loop order A surface then tracktype closes both quests`() {
        val roadSurface = AddRoadSurface()
        val tracktype = AddTracktype()
        val start = mapOf(
            "highway" to "track",
            "surface" to "asphalt",
            "tracktype" to "grade3",
            "check_date:surface" to nowAsCheckDateString(),
            "check_date:tracktype" to "2000-01-01",
            "note" to "keep",
        )
        val way = way(tags = start)
        assertFalse(roadSurface.isApplicableTo(way))
        assertTrue(tracktype.isApplicableTo(way))

        val afterTrack = tracktype.answeredTags(
            SurfaceTracktypeResolution.PairedCorrection(GRAVEL, "grade3"),
            start,
        )
        assertEquals("keep", afterTrack["note"])
        assertEquals("grade3", afterTrack["tracktype"])
        assertFalse(tracktype.isApplicableTo(way(tags = afterTrack)))
        assertFalse(roadSurface.isApplicableTo(way(tags = afterTrack)))
    }

    @Test fun `loop order B tracktype then surface closes both quests`() {
        val roadSurface = AddRoadSurface()
        val tracktype = AddTracktype()
        val start = mapOf(
            "highway" to "track",
            "surface" to "asphalt",
            "tracktype" to "grade3",
            "check_date:surface" to "2000-01-01",
            "check_date:tracktype" to nowAsCheckDateString(),
        )
        assertTrue(roadSurface.isApplicableTo(way(tags = start)))
        val way = way(tags = start)
        val afterSurface = roadSurface.answeredTags(
            SurfaceTracktypeResolution.PairedCorrection(ASPHALT, "grade1"),
            start,
        )
        assertEquals("grade1", afterSurface["tracktype"])
        assertFalse(roadSurface.isApplicableTo(way(tags = afterSurface)))
        assertFalse(tracktype.isApplicableTo(way(tags = afterSurface)))
    }
}

private fun SurfaceTracktypeResolution.appliedTo(tags: Map<String, String>): Set<StringMapEntryChange> {
    val cb = StringMapChangesBuilder(tags)
    applyTo(cb)
    return cb.create().changes
}