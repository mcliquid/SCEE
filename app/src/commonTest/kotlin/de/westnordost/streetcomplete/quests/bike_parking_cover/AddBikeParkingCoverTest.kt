package de.westnordost.streetcomplete.quests.bike_parking_cover

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

class AddBikeParkingCoverTest {

    private lateinit var quest: AddBikeParkingCover

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        quest = AddBikeParkingCover()
    }

    @Test fun `answer preserves access and cargo_bike tags`() {
        val before = mapOf(
            "amenity" to "bicycle_parking",
            "bicycle_parking" to "stands",
            "access" to "no",
            "cargo_bike" to "yes",
        )
        val changes = quest.answerAppliedTo(true, before)
        assertEquals(setOf(StringMapEntryAdd("covered", "yes")), changes)
    }

    @Test fun `shed type remains excluded even with cargo access`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "bicycle_parking" to "shed",
            "access" to "no",
            "cargo_bike" to "designated",
        )
        assertFalse(quest.isApplicableTo(node(tags = tags)) == true)
    }

    @Test fun `existing covered value excludes quest`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "bicycle_parking" to "stands",
            "covered" to "no",
            "access" to "no",
            "cargo_bike" to "yes",
        )
        assertFalse(quest.isApplicableTo(node(tags = tags)) == true)
    }
}
