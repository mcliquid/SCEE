package de.westnordost.streetcomplete.quests.oneway

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddOnewayBicycleTest {
    private lateinit var questType: AddOnewayBicycle

    @BeforeTest fun setUp() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = mockPrefs3()
        questType = AddOnewayBicycle()
    }

    @Test fun `bicycle permission overrides private access`() {
        val way = way(tags = mapOf(
            "highway" to "cycleway",
            "access" to "private",
            "bicycle" to "yes",
        ))

        assertNull(questType.isApplicableTo(way))
    }

    @Test fun `not applicable when bicycles are explicitly prohibited`() {
        val way = way(tags = mapOf(
            "highway" to "cycleway",
            "access" to "yes",
            "bicycle" to "no",
        ))

        assertFalse(questType.isApplicableTo(way)!!)
    }
}
