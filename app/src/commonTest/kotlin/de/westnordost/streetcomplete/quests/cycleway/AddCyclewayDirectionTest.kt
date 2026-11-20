package de.westnordost.streetcomplete.quests.cycleway

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.default_disabled_msg_ee
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AddCyclewayDirectionTest {
    private lateinit var quest: AddCyclewayDirection

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        quest = AddCyclewayDirection()
    }

    @Test fun `applicable to segregated cycle sidepath without direction tags`() {
        val w = way(tags = mapOf(
            "highway" to "cycleway",
            "footway" to "sidepath",
            "bicycle" to "designated",
        ))
        assertTrue(quest.isApplicableTo(w))
    }

    @Test fun `applicable when is_sidepath is yes`() {
        val w = way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
            "is_sidepath" to "yes",
        ))
        assertTrue(quest.isApplicableTo(w))
    }

    @Test fun `not applicable without sidepath context`() {
        val w = way(tags = mapOf("highway" to "cycleway", "bicycle" to "designated"))
        assertFalse(quest.isApplicableTo(w))
    }

    @Test fun `not applicable when bicycle access is private`() {
        val w = way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "private",
            "footway" to "sidewalk",
            "access" to "yes",
        ))
        assertFalse(quest.isApplicableTo(w))
    }

    @Test fun `enabled geographically in Germany`() {
        val countries = quest.enabledInCountries
        assertIs<NoCountriesExcept>(countries)
        assertTrue("DE" in countries.exceptions)
    }

    @Test fun `default visibility off with SCEE disabled message`() {
        assertEquals(Res.string.default_disabled_msg_ee, quest.defaultDisabledMessage)
    }

    @Test fun `supports custom element selection like former AddOnewayBicycle`() {
        assertTrue(quest.hasQuestSettings)
    }

    @Test fun `not applicable with existing oneway bicycle`() {
        val w = way(tags = mapOf(
            "highway" to "cycleway",
            "footway" to "sidepath",
            "oneway:bicycle" to "yes",
        ))
        assertFalse(quest.isApplicableTo(w))
    }

    @Test fun `exclusive cycleway writes oneway tag`() {
        val original = mapOf("highway" to "cycleway", "footway" to "sidepath")
        assertEquals(
            setOf(StringMapEntryAdd("oneway", "yes")),
            quest.answerAppliedTo(OnewayAnswer.FORWARD, original),
        )
    }

    @Test fun `shared path writes oneway bicycle tag`() {
        val original = mapOf("highway" to "path", "footway" to "sidewalk", "bicycle" to "yes", "foot" to "designated")
        assertEquals(
            setOf(StringMapEntryAdd("oneway:bicycle", "-1")),
            quest.answerAppliedTo(OnewayAnswer.BACKWARD, original),
        )
    }

    @Test fun `bidirectional answer`() {
        val original = mapOf("highway" to "footway", "cycleway" to "sidepath", "bicycle" to "yes")
        assertEquals(
            setOf(StringMapEntryAdd("oneway:bicycle", "no")),
            quest.answerAppliedTo(OnewayAnswer.NO_ONEWAY, original),
        )
    }
}