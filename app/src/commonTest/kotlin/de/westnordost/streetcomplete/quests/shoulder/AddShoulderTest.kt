package de.westnordost.streetcomplete.quests.shoulder

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddShoulderTest {
    private lateinit var questType: AddShoulder

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddShoulder()
    }

    @Test fun `applicable to motorway with legal bicycle traffic without shoulder tagging`() {
        assertTrue(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "bicycle" to "yes",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `applicable to rural primary with high maxspeed`() {
        assertTrue(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "primary",
            "maxspeed" to "80",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `applicable to trunk without maxspeed`() {
        assertTrue(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "trunk",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable when shoulder is already tagged`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "shoulder" to "yes",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable when shoulder side is already tagged`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "shoulder:left" to "yes",
            "surface" to "asphalt",
        ))))
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "shoulder:both" to "no",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable with cycle lane`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "cycleway:right" to "lane",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable with on-road parking lane`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "parking:lane:right" to "parallel",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable without lane markings`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "lane_markings" to "no",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `not applicable on unpaved surface`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "motorway",
            "surface" to "gravel",
        ))))
    }

    @Test fun `maps both shoulders to shoulder both`() {
        assertEquals(
            setOf(StringMapEntryAdd("shoulder", "both")),
            questType.answerAppliedTo(ShoulderSides(left = true, right = true), emptyMap()),
        )
    }

    @Test fun `maps single sides and none`() {
        assertEquals(
            setOf(StringMapEntryAdd("shoulder", "left")),
            questType.answerAppliedTo(ShoulderSides(left = true, right = false), emptyMap()),
        )
        assertEquals(
            setOf(StringMapEntryAdd("shoulder", "right")),
            questType.answerAppliedTo(ShoulderSides(left = false, right = true), emptyMap()),
        )
        assertEquals(
            setOf(StringMapEntryAdd("shoulder", "no")),
            questType.answerAppliedTo(ShoulderSides(left = false, right = false), emptyMap()),
        )
    }

    @Test fun `stores last shoulder answer for reuse`() {
        Prefs.preferences.setLastPicked("AddShoulderForm", listOf(ShoulderSides(left = true, right = false)))
        assertEquals(
            listOf(ShoulderSides(left = true, right = false)),
            Prefs.preferences.getLastPicked<ShoulderSides>("AddShoulderForm"),
        )
    }

    @Test fun `does not overwrite existing shoulder tags`() {
        val original = mapOf(
            "highway" to "motorway",
            "shoulder" to "left",
        )
        assertEquals(
            emptySet(),
            questType.answerAppliedTo(ShoulderSides(left = true, right = true), original),
        )
    }
}
