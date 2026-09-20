package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddPathSmoothnessTest {
    private lateinit var questType: AddPathSmoothness

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddPathSmoothness()
    }

    @Test fun `applicable to non-segregated path when otherwise eligible`() {
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(
            "highway" to "path",
            "surface" to "asphalt",
            "segregated" to "no",
        ))))
    }

    @Test fun `not applicable to segregated path`() {
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(
            "highway" to "path",
            "surface" to "asphalt",
            "segregated" to "yes",
        ))))
    }
}
