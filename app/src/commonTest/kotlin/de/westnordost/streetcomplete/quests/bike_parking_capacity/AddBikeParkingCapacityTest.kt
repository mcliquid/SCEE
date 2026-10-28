package de.westnordost.streetcomplete.quests.bike_parking_capacity

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddBikeParkingCapacityTest {

    private lateinit var quest: AddBikeParkingCapacity

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        quest = AddBikeParkingCapacity()
    }

    @Test fun `answer preserves access and cargo_bike tags`() {
        val before = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "designated",
            "bicycle_parking" to "stands",
        )
        val changes = quest.answerAppliedTo(4, before)
        assertTrue(changes.any { it.key == "capacity" })
        assertFalse(changes.any { it.key == "access" || it.key == "cargo_bike" })
    }

    @Test fun `floor parking remains excluded`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "bicycle_parking" to "floor",
            "access" to "no",
            "cargo_bike" to "yes",
        )
        assertFalse(quest.isApplicableTo(node(tags = tags)) == true)
    }

    @Test fun `writes capacity only`() {
        assertEquals(
            setOf(StringMapEntryAdd("capacity", "3")),
            quest.answerAppliedTo(3, mapOf("amenity" to "bicycle_parking")),
        )
    }
}
