package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothnessOverlayStateTest {

    @Test fun `parse smoothness values`() {
        assertNull(parseSmoothnessValue(null))
        assertEquals(ParsedSmoothness.Valid(GOOD), parseSmoothnessValue("good"))
        assertEquals(ParsedSmoothness.Invalid("very_good"), parseSmoothnessValue("very_good"))
        assertNull(parseSmoothnessValue("very_good").validValue)
    }

    @Test fun `ordinary road is not segregated`() {
        val state = parseSmoothnessOverlayState(mapOf("highway" to "residential", "surface" to "asphalt", "smoothness" to "good"))
        assertFalse(state.isSegregated)
        assertEquals(SmoothnessPartState(ParsedSmoothness.Valid(GOOD), "asphalt"), state.whole)
    }

    @Test fun `road with cycleway part tags is not segregated`() {
        assertFalse(parseSmoothnessOverlayState(mapOf(
            "highway" to "primary", "cycleway:surface" to "asphalt", "cycleway:smoothness" to "good"
        )).isSegregated)
    }

    @Test fun `segregated path`() {
        assertTrue(parseSmoothnessOverlayState(mapOf("highway" to "path", "segregated" to "yes")).isSegregated)
    }

    @Test fun `path with separate surfaces is segregated as in surface overlay`() {
        assertTrue(parseSmoothnessOverlayState(mapOf("highway" to "path", "footway:surface" to "asphalt")).isSegregated)
    }

    @Test fun `path with separate smoothness is segregated`() {
        assertTrue(parseSmoothnessOverlayState(mapOf("highway" to "footway", "cycleway:smoothness" to "good")).isSegregated)
    }

    @Test fun `segregated=no is never edited in parts`() {
        val state = parseSmoothnessOverlayState(mapOf(
            "highway" to "path", "segregated" to "no", "surface" to "asphalt", "smoothness" to "good",
            "footway:smoothness" to "bad", "cycleway:smoothness" to "good", "footway:surface" to "sett",
        ))
        assertFalse(state.isSegregated)
        assertEquals(ParsedSmoothness.Valid(GOOD), state.whole.smoothness)
    }

    @Test fun `missing segregated tag with part values is edited in parts`() {
        assertTrue(parseSmoothnessOverlayState(mapOf(
            "highway" to "path", "smoothness" to "good", "footway:smoothness" to "bad",
        )).isSegregated)
    }

    @Test fun `part surfaces fall back to whole way surface`() {
        val state = parseSmoothnessOverlayState(mapOf(
            "highway" to "path", "segregated" to "yes", "surface" to "asphalt", "cycleway:surface" to "sett",
            "footway:smoothness" to "bad",
        ))
        assertEquals(SmoothnessPartState(ParsedSmoothness.Valid(BAD), "asphalt", surfaceFromWholeWay = true), state.footway)
        assertEquals(SmoothnessPartState(null, "sett", surfaceFromWholeWay = false), state.cycleway)
    }

    @Test fun `sidewalk tags detected`() {
        assertTrue(parseSmoothnessOverlayState(mapOf("highway" to "cycleway", "sidewalk:left:surface" to "sett")).hasSidewalkTags)
        assertFalse(parseSmoothnessOverlayState(mapOf("highway" to "cycleway")).hasSidewalkTags)
    }
}
