package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.overlays.surface.SegregatedSurface
import de.westnordost.streetcomplete.overlays.surface.SingleSurface
import de.westnordost.streetcomplete.overlays.surface.isBothFootAndBicycleTrafficFilter
import de.westnordost.streetcomplete.overlays.surface.parseSurfaceOverlayAnswer
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.smoothness.AddCyclewayPartSmoothness
import de.westnordost.streetcomplete.quests.smoothness.AddPathSmoothness
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Splitting a way into foot- and cycleway parts in the smoothness overlay, and how the
 *  resulting tags are interpreted by the other smoothness and surface tools. */
class SmoothnessSplitConsistencyTest {

    private val today = nowAsCheckDateString()

    private val sharedPath = mapOf(
        "highway" to "path",
        "foot" to "designated",
        "bicycle" to "designated",
        "surface" to "asphalt",
        "smoothness" to "good",
    )

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
    }

    private fun draftOf(tags: Map<String, String>) =
        SmoothnessOverlayDraft.of(parseSmoothnessOverlayState(tags))

    /** what the "Split into cycling and walking parts" answer of the form does */
    private fun SmoothnessOverlayDraft.split() = requestModeSwitch()

    private fun SmoothnessOverlayDraft.saveTo(tags: Map<String, String>): Map<String, String> {
        val answer = toAnswer() ?: return tags
        val builder = StringMapChangesBuilder(tags)
        answer.applyTo(builder)
        return builder.toMap()
    }

    private fun isApplicable(quest: AddPathSmoothness, tags: Map<String, String>) =
        quest.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = tags))

    private fun isApplicable(quest: AddCyclewayPartSmoothness, tags: Map<String, String>) =
        quest.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = tags))

    /* --------------------------------- explicit split confirmation -------------------------------- */

    @Test fun `the split is offered for the audit way`() {
        assertTrue(isBothFootAndBicycleTrafficFilter.matches(way(tags = sharedPath)))
        assertFalse(draftOf(sharedPath).isSegregated)
    }

    @Test fun `confirmed split with both parts writes segregated=yes`() {
        val draft = draftOf(sharedPath).split().let {
            it.copy(footway = it.footway.select(GOOD), cycleway = it.cycleway.select(INTERMEDIATE))
        }
        assertEquals(
            mapOf(
                "highway" to "path",
                "foot" to "designated",
                "bicycle" to "designated",
                "surface" to "asphalt",
                "segregated" to "yes",
                "footway:smoothness" to "good",
                "check_date:footway:smoothness" to today,
                "cycleway:smoothness" to "intermediate",
                "check_date:cycleway:smoothness" to today,
                // contradicts both parts, so it is removed (existing rule)
            ),
            draft.saveTo(sharedPath)
        )
    }

    @Test fun `confirmed split with only one part keeps the common smoothness as fallback`() {
        val draft = draftOf(sharedPath).split().let { it.copy(footway = it.footway.select(BAD)) }
        assertEquals(
            sharedPath + mapOf(
                "segregated" to "yes",
                "footway:smoothness" to "bad",
                "check_date:footway:smoothness" to today,
            ),
            draft.saveTo(sharedPath)
        )
    }

    @Test fun `split without any part value saves nothing`() {
        val draft = draftOf(sharedPath).split()
        assertNull(draft.toAnswer())
        assertEquals(sharedPath, draft.saveTo(sharedPath))
    }

    @Test fun `opening the form without input saves nothing`() {
        assertNull(draftOf(sharedPath).toAnswer())
    }

    @Test fun `switching back to the whole way withdraws the split`() {
        val draft = draftOf(sharedPath).split().requestModeSwitch().let { it.copy(whole = it.whole.select(BAD)) }
        assertFalse(draft.isSegregated)
        val result = draft.saveTo(sharedPath)
        assertNull(result["segregated"])
        assertEquals("bad", result["smoothness"])
    }

    @Test fun `splitting again after switching back confirms the split again`() {
        val draft = draftOf(sharedPath).split().requestModeSwitch().split()
            .let { it.copy(cycleway = it.cycleway.select(GOOD)) }
        assertEquals("yes", draft.saveTo(sharedPath)["segregated"])
    }

    @Test fun `split after discarding whole way input still confirms the split`() {
        val draft = draftOf(sharedPath).selectNewSurface(Surface.CONCRETE, Surface.ASPHALT)
            .split().confirmModeSwitch()
            .let { it.copy(cycleway = it.cycleway.select(GOOD)) }
        val result = draft.saveTo(sharedPath)
        assertEquals("yes", result["segregated"])
        // the discarded surface change is not saved
        assertEquals("asphalt", result["surface"])
    }

    /* ----------------------------- existing part values, no confirmation ---------------------------- */

    @Test fun `existing part values alone do not write segregated`() {
        val tags = mapOf("highway" to "path", "surface" to "asphalt", "footway:smoothness" to "good")
        val draft = draftOf(tags)
        assertTrue(draft.isSegregated)
        val result = draft.copy(cycleway = draft.cycleway.select(BAD)).saveTo(tags)
        assertNull(result["segregated"])
        assertEquals("good", result["footway:smoothness"])
        assertEquals("bad", result["cycleway:smoothness"])
    }

    @Test fun `confirming an unchanged part value does not write segregated`() {
        val tags = mapOf("highway" to "path", "footway:smoothness" to "good", "cycleway:smoothness" to "bad")
        val draft = draftOf(tags)
        val result = draft.copy(footway = draft.footway.confirm()).saveTo(tags)
        assertEquals(tags + ("check_date:footway:smoothness" to today), result)
    }

    @Test fun `segregated=no with part values is never split or reinterpreted`() {
        val tags = mapOf(
            "highway" to "path", "foot" to "designated", "bicycle" to "designated",
            "segregated" to "no", "surface" to "asphalt", "smoothness" to "good",
            "footway:smoothness" to "bad", "cycleway:smoothness" to "intermediate",
        )
        val draft = draftOf(tags)
        assertFalse(draft.isSegregated)
        val result = draft.copy(whole = draft.whole.select(INTERMEDIATE)).saveTo(tags)
        assertEquals("no", result["segregated"])
        assertEquals("bad", result["footway:smoothness"])
        assertEquals("intermediate", result["cycleway:smoothness"])
    }

    @Test fun `a part answer never overrides segregated=no`() {
        val tags = mapOf("highway" to "path", "segregated" to "no")
        val builder = StringMapChangesBuilder(tags)
        PartsSmoothnessAnswer(footway = GOOD, confirmSegregated = true).applyTo(builder)
        assertEquals("no", builder.toMap()["segregated"])
    }

    /* --------------------------- interpretation of the result by other tools -------------------------- */

    @Test fun `after a confirmed split all tools see a segregated way`() {
        val draft = draftOf(sharedPath).split().let {
            it.copy(footway = it.footway.select(GOOD), cycleway = it.cycleway.select(INTERMEDIATE))
        }
        val result = draft.saveTo(sharedPath)

        assertTrue(parseSmoothnessOverlayState(result).isSegregated)
        // surface overlay: part surfaces are now asked for, as after splitting there
        assertEquals(SegregatedSurface(footway = null, cycleway = null), parseSurfaceOverlayAnswer(result))
        // the whole-way quest cannot add a common smoothness next to the part values anymore
        assertFalse(isApplicable(AddPathSmoothness(), result))
        // part quest: needs the part surface first (asked by the part surface quests)
        assertFalse(isApplicable(AddCyclewayPartSmoothness(), result))
    }

    @Test fun `after a confirmed split of one part the missing part is asked once its surface is known`() {
        val draft = draftOf(sharedPath).split().let { it.copy(footway = it.footway.select(BAD)) }
        val result = draft.saveTo(sharedPath)
        assertFalse(isApplicable(AddPathSmoothness(), result))
        assertTrue(isApplicable(AddCyclewayPartSmoothness(), result + ("cycleway:surface" to "asphalt")))
    }

    @Test fun `unconfirmed part values stay a single surface but get no contradicting common smoothness`() {
        // unconfirmed part values (e.g. from older edits) stay as they are
        val tags = sharedPath - "smoothness" + mapOf("footway:smoothness" to "good", "cycleway:smoothness" to "bad")
        assertEquals(SingleSurface(Surface.ASPHALT), parseSurfaceOverlayAnswer(tags))
        // but the whole-way quest does not add a contradicting common smoothness to them
        assertFalse(isApplicable(AddPathSmoothness(), tags))
    }
}
