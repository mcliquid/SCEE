package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class AddRoadSmoothnessTest {
    private lateinit var questType: AddRoadSmoothness

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddRoadSmoothness()
    }

    @Test fun `applicable to old way tagged with smoothness-date`() {
        assertTrue(questType.isApplicableTo(way(tags = mapOf(
            "highway" to "residential",
            "surface" to "asphalt",
            "smoothness" to "excellent",
            "smoothness:date" to "2014-10-10"
        ))))
    }
}
