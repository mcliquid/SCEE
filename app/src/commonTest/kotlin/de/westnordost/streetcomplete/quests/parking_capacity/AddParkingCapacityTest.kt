package de.westnordost.streetcomplete.quests.parking_capacity

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddParkingCapacityTest {

    private lateinit var quest: AddParkingCapacity

    private val parkingTags = mapOf(
        "amenity" to "parking",
        "parking" to "surface",
        "access" to "yes",
    )

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        quest = AddParkingCapacity()
    }

    @Test fun `unclear count sentinel rejected by writer`() {
        assertFailsWith<IllegalArgumentException> {
            quest.answerAppliedTo(-1, parkingTags)
        }
    }

    @Test fun `negative capacity rejected by writer`() {
        assertFailsWith<IllegalArgumentException> {
            quest.answerAppliedTo(-5, parkingTags)
        }
    }

    @Test fun `zero capacity is valid`() {
        assertEquals(
            setOf(StringMapEntryAdd("capacity", "0")),
            quest.answerAppliedTo(0, parkingTags),
        )
    }

    @Test fun `positive capacity is valid`() {
        assertEquals(
            setOf(StringMapEntryAdd("capacity", "12")),
            quest.answerAppliedTo(12, parkingTags),
        )
    }

    @Test fun `updates existing capacity`() {
        val changes = quest.answerAppliedTo(
            8,
            parkingTags + mapOf("capacity" to "3"),
        )
        assertEquals(
            setOf(StringMapEntryModify("capacity", "3", "8")),
            changes,
        )
    }

    @Test fun `does not touch capacity disabled`() {
        val before = parkingTags + mapOf(
            "capacity:disabled" to "yes",
            "fee" to "yes",
        )
        val changes = quest.answerAppliedTo(5, before)
        assertTrue(changes.any { it.key == "capacity" })
        assertFalse(changes.any { it.key == "capacity:disabled" || it.key == "fee" })
    }

    @Test fun `quest closes after valid capacity answer`() {
        val updated = parkingTags + mapOf("capacity" to "4")
        assertFalse(quest.isApplicableTo(way(tags = updated)))
    }

    @Test fun `quest remains open without capacity`() {
        assertTrue(quest.isApplicableTo(way(tags = parkingTags)))
    }
}