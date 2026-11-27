package de.westnordost.streetcomplete.overlays.sidewalk

import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SidewalkOverlayFootwayStyleTest {

    private val overlay = SidewalkOverlay()

    private fun style(tags: Map<String, String>): OverlayStyle.Polyline {
        val mapData = TestMapDataWithGeometry(listOf(way(1, listOf(1, 2), tags)))
        return overlay.getStyledElements(mapData).single().second as OverlayStyle.Polyline
    }

    private fun color(tags: Map<String, String>): Color? = style(tags).stroke?.color

    @Test fun `footway without footway tag is neutral, not red`() {
        val s = style(mapOf("highway" to "footway"))
        assertEquals(OverlayColor.Sky, s.stroke?.color)
        assertFalse(s.disabled)
    }

    @Test fun `unsupported footway value is visible but not marked as error`() {
        for (value in listOf("link", "alley", "something")) {
            val c = color(mapOf("highway" to "footway", "footway" to value))
            assertEquals(OverlayColor.Purple, c, value)
        }
    }

    @Test fun `supported footway kinds are distinguishable and never red or black`() {
        val colors = listOf("sidewalk", "crossing", "traffic_island", "access_aisle").map {
            color(mapOf("highway" to "footway", "footway" to it))
        }
        assertEquals(colors.size, colors.toSet().size)
        assertTrue(OverlayColor.Sky !in colors)
        assertTrue(colors.none { it == OverlayColor.Red || it == OverlayColor.Black || it == OverlayColor.Invisible })
    }

    @Test fun `footway not accessible on foot stays invisible`() {
        assertEquals(OverlayColor.Invisible, color(mapOf("highway" to "footway", "foot" to "no", "footway" to "sidewalk")))
        assertEquals(OverlayColor.Invisible, color(mapOf("highway" to "footway", "foot" to "private")))
    }

    @Test fun `paths and cycleways keep previous display and stay selectable`() {
        assertEquals(OverlayColor.Sky, color(mapOf("highway" to "path")))
        assertEquals(OverlayColor.Invisible, color(mapOf("highway" to "path", "foot" to "no")))
        assertEquals(OverlayColor.Invisible, color(mapOf("highway" to "cycleway")))
        assertEquals(OverlayColor.Sky, color(mapOf("highway" to "cycleway", "foot" to "designated")))
        // footway=* is not interpreted on other highways
        assertEquals(OverlayColor.Sky, color(mapOf("highway" to "path", "footway" to "crossing")))
        // cycleway with sidewalk keeps the sidewalk display
        val withSidewalk = style(mapOf("highway" to "cycleway", "sidewalk" to "left"))
        assertEquals(OverlayColor.Sky, withSidewalk.strokeLeft?.color)
        assertFalse(style(mapOf("highway" to "path")).disabled)
        assertFalse(style(mapOf("highway" to "cycleway")).disabled)
    }

    @Test fun `steps and bridleways are displayed but not selectable`() {
        val steps = style(mapOf("highway" to "steps"))
        assertEquals(OverlayColor.Sky, steps.stroke?.color)
        assertTrue(steps.disabled)
        val bridleway = style(mapOf("highway" to "bridleway"))
        assertEquals(OverlayColor.Invisible, bridleway.stroke?.color)
        assertTrue(bridleway.disabled)
        assertTrue(style(mapOf("highway" to "bridleway", "foot" to "designated")).disabled)
    }

    @Test fun `roads are not affected`() {
        val s = style(mapOf("highway" to "residential", "footway" to "sidewalk", "sidewalk" to "both"))
        assertEquals(OverlayColor.Sky, s.strokeLeft?.color)
        assertEquals(OverlayColor.Sky, s.strokeRight?.color)
        assertFalse(s.disabled)
        assertNotEquals(OverlayColor.Blue, s.stroke?.color)
    }
}
