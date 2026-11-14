package de.westnordost.streetcomplete.quests.place_name

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.osm.localized_name.LocalizedName
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.feature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AddPlaceNameTest {

    private val questType = AddPlaceName(
        getFeature = { feature() }
    )

    @Test fun `apply no name answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("name:signed", "no")),
            questType.answerApplied(PlaceName(listOf()))
        )
    }

    @Test fun `brand answer trims and clears fixme name`() {
        val changes = questType.answerAppliedTo(
            PlaceNameAnswer.BrandName("  Y  "),
            mapOf("shop" to "bakery", "name" to "X", "fixme" to "name"),
        )
        assertEquals("Y", (changes.single { it is StringMapEntryAdd && it.key == "brand" } as StringMapEntryAdd).value)
        assertTrue(changes.any { it is StringMapEntryDelete && it.key == "fixme" })
        assertTrue(changes.any { it.key == "name" })
    }

    @Test fun `empty brand answer is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            questType.answerAppliedTo(PlaceNameAnswer.BrandName(""), mapOf("shop" to "bakery"))
        }
    }

    @Test fun `apply name answer`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("name", "Hey ya!"),
                StringMapEntryAdd("name:de", "He ja!")
            ),
            questType.answerApplied(PlaceName(listOf(
                LocalizedName("", "Hey ya!"),
                LocalizedName("de", "He ja!"),
            )))
        )
    }
}
