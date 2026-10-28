package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparation
import de.westnordost.streetcomplete.osm.cycleway_separate.parseCyclewaySeparationState
import de.westnordost.streetcomplete.quests.cycleway_separation.CyclewaySeparationSidePresentation.*
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CyclewaySeparationSidePresentationTest {

    private val baseTags = mapOf("highway" to "cycleway")

    @Test fun `missing side is surveyable with empty selection`() {
        val state = parseCyclewaySeparationState(baseTags)!!
        val sides = presentCyclewaySeparationSides(state, baseTags, Sides(null, null))
        assertEquals(Surveyable(null), sides.left)
        assertEquals(Surveyable(null), sides.right)
    }

    @Test fun `known existing kerb is locked known on that side`() {
        val tags = baseTags + ("separation:left" to "kerb")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(LockedKnown(CyclewaySeparation.KERB), sides.left)
        assertEquals(Surveyable(null), sides.right)
    }

    @Test fun `explicit no is locked known`() {
        val tags = baseTags + ("separation:left" to "no")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(LockedKnown(CyclewaySeparation.NO), sides.left)
    }

    @Test fun `compound value is locked other with raw string`() {
        val tags = baseTags + ("separation:left" to "kerb;hedge")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(LockedOther("kerb;hedge"), sides.left)
        assertEquals(Surveyable(null), sides.right)
    }

    @Test fun `unknown single value is locked other`() {
        val tags = baseTags + ("separation:left" to "experimental_value")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(LockedOther("experimental_value"), sides.left)
    }

    @Test fun `both sides tagged show locked presentations`() {
        val tags = baseTags + mapOf("separation:left" to "kerb", "separation:right" to "fence")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(LockedKnown(CyclewaySeparation.KERB), sides.left)
        assertEquals(LockedKnown(CyclewaySeparation.FENCE), sides.right)
    }

    @Test fun `surveyable side shows user selection`() {
        val tags = baseTags + ("separation:left" to "kerb")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(
            state, tags, Sides(null, CyclewaySeparation.BOLLARD),
        )
        assertIs<LockedKnown>(sides.left)
        assertEquals(Surveyable(CyclewaySeparation.BOLLARD), sides.right)
    }

    @Test fun `locked raw helper returns osm value for read-only sides`() {
        val tags = baseTags + ("separation:left" to "kerb;hedge")
        val state = parseCyclewaySeparationState(tags)!!
        assertEquals("kerb;hedge", lockedSeparationOsmValueForUiSide(Side.LEFT, state, tags))
        assertNull(lockedSeparationOsmValueForUiSide(Side.RIGHT, state, tags))
    }

    @Test fun `oneway minus one still maps locked side to geographic ui side`() {
        val tags = baseTags + mapOf("oneway" to "-1", "separation:right" to "kerb")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        assertEquals(Surveyable(null), sides.right)
        assertEquals(LockedKnown(CyclewaySeparation.KERB), sides.left)
    }
}
