package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothnessOverlayDraftTest {

    private val pathState = parseSmoothnessOverlayState(mapOf(
        "highway" to "path", "surface" to "asphalt", "smoothness" to "good",
        "foot" to "designated", "bicycle" to "designated",
    ))
    private val initial = SmoothnessOverlayDraft.of(pathState)
    private val asphalt = Surface.ASPHALT

    @Test fun `initial draft has no changes`() {
        assertFalse(initial.isSegregated)
        assertEquals(GOOD, initial.whole.selected)
        assertFalse(initial.hasChanges)
        assertNull(initial.toAnswer())
    }

    @Test fun `switching an untouched form switches immediately`() {
        val switched = initial.requestModeSwitch()
        assertTrue(switched.isSegregated)
        assertFalse(switched.pendingModeSwitch)
        assertTrue(switched.canSwitchBack)
        assertNull(switched.footway.selected)
        assertNull(switched.cycleway.selected)
        assertNull(switched.toAnswer())
    }

    @Test fun `common smoothness is not copied to the parts`() {
        val switched = initial.requestModeSwitch()
        assertNull(switched.footway.selected)
        assertNull(switched.cycleway.selected)
    }

    @Test fun `switching after changing the surface asks first`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt)
        val pending = draft.requestModeSwitch()
        assertTrue(pending.pendingModeSwitch)
        assertFalse(pending.isSegregated)
        assertEquals(Surface.GRAVEL, pending.newSurface)
    }

    @Test fun `switching after selecting smoothness asks first`() {
        val draft = initial.copy(whole = initial.whole.select(BAD))
        val pending = draft.requestModeSwitch()
        assertTrue(pending.pendingModeSwitch)
        assertEquals(BAD, pending.whole.selected)
    }

    @Test fun `switching after changing surface and smoothness asks first`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt).let { it.copy(whole = it.whole.select(BAD)) }
        val pending = draft.requestModeSwitch()
        assertTrue(pending.pendingModeSwitch)
        assertEquals(Surface.GRAVEL, pending.newSurface)
        assertEquals(BAD, pending.whole.selected)
        assertEquals(SingleSmoothnessAnswer(BAD, Surface.GRAVEL), pending.toAnswer())
    }

    @Test fun `switching after confirming unchanged smoothness asks first`() {
        val draft = initial.copy(whole = initial.whole.confirm())
        assertTrue(draft.requestModeSwitch().pendingModeSwitch)
    }

    @Test fun `canceling a switch keeps the input`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt).let { it.copy(whole = it.whole.select(BAD)) }
        assertEquals(draft, draft.requestModeSwitch().cancelModeSwitch())
    }

    @Test fun `confirming a switch discards the input of the old mode`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt).let { it.copy(whole = it.whole.select(BAD)) }
        val switched = draft.requestModeSwitch().confirmModeSwitch()
        assertTrue(switched.isSegregated)
        assertNull(switched.newSurface)
        assertEquals(GOOD, switched.whole.selected)
        assertFalse(switched.hasChanges)
        assertNull(switched.toAnswer())
    }

    @Test fun `returning to the original mode`() {
        val switched = initial.requestModeSwitch()
        val back = switched.requestModeSwitch()
        assertFalse(back.isSegregated)
        assertFalse(back.canSwitchBack)
        assertEquals(initial, back)
    }

    @Test fun `returning with part input asks first`() {
        val switched = initial.requestModeSwitch().let { it.copy(footway = it.footway.select(BAD)) }
        val pending = switched.requestModeSwitch()
        assertTrue(pending.pendingModeSwitch)
        assertTrue(pending.isSegregated)
        assertEquals(initial, pending.confirmModeSwitch())
    }

    @Test fun `saving after switching writes only the parts`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt)
            .requestModeSwitch().confirmModeSwitch()
            .let { it.copy(cycleway = it.cycleway.select(EXCELLENT)) }
        assertEquals(PartsSmoothnessAnswer(cycleway = EXCELLENT), draft.toAnswer())
    }

    @Test fun `saving in single mode writes surface and smoothness`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt).let { it.copy(whole = it.whole.select(BAD)) }
        assertEquals(SingleSmoothnessAnswer(BAD, Surface.GRAVEL), draft.toAnswer())
    }

    @Test fun `changing the surface clears the smoothness of the old surface`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt)
        assertNull(draft.whole.selected)
        assertNull(draft.toAnswer())
    }

    @Test fun `choosing the current surface again is no change`() {
        val draft = initial.selectNewSurface(Surface.GRAVEL, asphalt).selectNewSurface(asphalt, asphalt)
        assertNull(draft.newSurface)
    }

    @Test fun `reopening the form starts from the tags`() {
        val edited = initial.requestModeSwitch().let { it.copy(footway = it.footway.select(BAD)) }
        assertTrue(edited.isSegregated)
        assertEquals(initial, SmoothnessOverlayDraft.of(pathState))
    }

    @Test fun `segregated way starts in part mode without switch back`() {
        val state = parseSmoothnessOverlayState(mapOf("highway" to "path", "segregated" to "yes", "cycleway:smoothness" to "good"))
        val draft = SmoothnessOverlayDraft.of(state)
        assertTrue(draft.isSegregated)
        assertFalse(draft.canSwitchBack)
        assertEquals(GOOD, draft.cycleway.selected)
        assertEquals(PartsSmoothnessAnswer(cycleway = GOOD), draft.copy(cycleway = draft.cycleway.confirm()).toAnswer())
    }
}
