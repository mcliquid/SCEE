package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.*
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CyclewaySeparationLastPickedTest {
    private val preferences = inMemoryPrefs()
    private val tags = mapOf("highway" to "cycleway")
    private val answer = CyclewaySeparationAnswer(CyclewaySeparation.KERB, CyclewaySeparation.FENCE)

    @Test fun `history round trips asymmetric and identical values`() {
        assertNull(loadSeparationLastPicked(preferences, tags))
        for (value in listOf(answer, answer.copy(right = CyclewaySeparation.KERB))) {
            saveSeparationLastPicked(preferences, tags, value)
            assertEquals(value, loadSeparationLastPicked(preferences, tags))
        }
    }

    @Test fun `tag orientation survives reversed travel and conversion back to answer`() {
        val forward = tags + ("oneway" to "yes")
        val reverse = tags + ("oneway" to "-1")
        saveSeparationLastPicked(preferences, forward, answer)
        val restored = loadSeparationLastPicked(preferences, reverse)!!
        val selections = separationSelectionsForAnswer(restored, reverse)
        assertEquals(Sides<CyclewaySeparation?>(CyclewaySeparation.FENCE, CyclewaySeparation.KERB), selections)
        assertEquals(answer, buildAnswer(parseCyclewaySeparationState(reverse)!!, reverse, selections))
        assertNull(loadSeparationLastPicked(preferences, tags + ("oneway:bicycle" to "no")))
    }

    @Test fun `partial context matches existing side and preserves compound raw value`() {
        val partial = tags + ("separation:left" to "kerb;greenery")
        val value = CyclewaySeparationAnswer(null, CyclewaySeparation.FENCE)
        saveSeparationLastPicked(preferences, partial, value)
        assertEquals(value, loadSeparationLastPicked(preferences, partial))
        assertNull(loadSeparationLastPicked(preferences, tags))
        assertNull(loadSeparationLastPicked(preferences, tags + ("separation:right" to "kerb;greenery")))
        assertNull(loadSeparationLastPicked(preferences, partial + ("separation:left" to "greenery;kerb")))
    }

    @Test fun `incomplete invalid completed and conflicting contexts do not replace history`() {
        saveSeparationLastPicked(preferences, tags, answer)
        for ((context, value) in listOf(
            tags to CyclewaySeparationAnswer(null, null),
            tags to answer.copy(right = null),
            (tags + ("highway" to "primary")) to answer,
            (tags + ("separation:both" to "kerb")) to answer,
            (tags + mapOf("separation:both" to "kerb", "separation:left" to "fence")) to answer,
        )) saveSeparationLastPicked(preferences, context, value)
        assertEquals(answer, loadSeparationLastPicked(preferences, tags))
        assertNull(loadSeparationLastPicked(preferences, tags + ("separation:both" to "kerb")))
    }
}
