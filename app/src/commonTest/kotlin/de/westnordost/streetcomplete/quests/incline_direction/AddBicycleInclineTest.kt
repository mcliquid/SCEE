package de.westnordost.streetcomplete.quests.incline_direction

import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddBicycleInclineTest {
    private val questType = AddBicycleIncline()

    @Test fun `bicycle permission overrides private access`() {
        val way = way(tags = mapOf(
            "highway" to "cycleway",
            "mtb:scale:uphill" to "1",
            "access" to "private",
            "bicycle" to "yes",
        ))

        assertNull(questType.isApplicableTo(way))
    }

    @Test fun `not applicable when bicycles are explicitly prohibited`() {
        val way = way(tags = mapOf(
            "highway" to "cycleway",
            "mtb:scale:uphill" to "1",
            "access" to "yes",
            "bicycle" to "no",
        ))

        assertFalse(questType.isApplicableTo(way)!!)
    }
}
