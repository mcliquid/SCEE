package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class SmoothnessOverlayStyleTest {

    private fun style(vararg tags: Pair<String, String>) = getSmoothnessStyle(way(tags = mapOf(*tags)))

    private fun strokeColor(vararg tags: Pair<String, String>) =
        assertIs<OverlayStyle.Polyline>(style(*tags)).stroke?.color

    @Test fun `palette for valid values`() {
        val expected = mapOf(
            "excellent" to OverlayColor.Blue,
            "good" to OverlayColor.Sky,
            "intermediate" to OverlayColor.Cyan,
            "bad" to OverlayColor.Lime,
            "very_bad" to OverlayColor.Gold,
            "horrible" to OverlayColor.Orange,
            "very_horrible" to OverlayColor.Orange,
            "impassable" to OverlayColor.Black,
        )
        assertEquals(Smoothness.entries.map { it.osmValue }.toSet(), expected.keys)
        for ((value, color) in expected) {
            assertEquals(color, strokeColor("highway" to "residential", "smoothness" to value), value)
        }
    }

    @Test fun `missing smoothness is red without label`() {
        val style = assertIs<OverlayStyle.Polyline>(style("highway" to "residential", "surface" to "asphalt"))
        assertEquals(OverlayColor.Red, style.stroke?.color)
        assertNull(style.label)
    }

    @Test fun `missing smoothness on unpaved and unknown surface is red`() {
        assertEquals(OverlayColor.Red, strokeColor("highway" to "track", "surface" to "grass"))
        assertEquals(OverlayColor.Red, strokeColor("highway" to "footway"))
    }

    @Test fun `missing smoothness is not highlighted where tagging is not expected`() {
        assertEquals(OverlayColor.Invisible, strokeColor("highway" to "footway", "indoor" to "yes"))
        assertEquals(OverlayColor.Invisible, strokeColor("highway" to "footway", "access" to "private"))
    }

    @Test fun `missing smoothness area is red`() {
        val style = assertIs<OverlayStyle.Polygon>(style("highway" to "pedestrian", "area" to "yes"))
        assertEquals(OverlayColor.Red, style.color)
    }

    @Test fun `segregated way without any smoothness is red`() {
        assertEquals(OverlayColor.Red, strokeColor("highway" to "path", "segregated" to "yes"))
    }

    @Test fun `invalid smoothness is red and labelled`() {
        val style = assertIs<OverlayStyle.Polyline>(style("highway" to "residential", "smoothness" to "very_good"))
        assertEquals(OverlayColor.Red, style.stroke?.color)
        assertEquals("very_good", style.label)
    }

    @Test fun `segregated way shows the worse part`() {
        assertEquals(OverlayColor.Gold, strokeColor(
            "highway" to "path", "segregated" to "yes",
            "footway:smoothness" to "good", "cycleway:smoothness" to "very_bad",
        ))
    }

    @Test fun `segregated way with one known part shows that part`() {
        assertEquals(OverlayColor.Sky, strokeColor("highway" to "path", "segregated" to "yes", "cycleway:smoothness" to "good"))
    }

    @Test fun `segregated way with invalid part is red`() {
        assertEquals(OverlayColor.Red, strokeColor(
            "highway" to "path", "segregated" to "yes",
            "footway:smoothness" to "nice", "cycleway:smoothness" to "good",
        ))
    }

    @Test fun `segregated way without part values shows common smoothness`() {
        assertEquals(OverlayColor.Cyan, strokeColor("highway" to "path", "segregated" to "yes", "smoothness" to "intermediate"))
    }

    @Test fun `segregated=no shows the common smoothness`() {
        assertEquals(OverlayColor.Sky, strokeColor(
            "highway" to "path", "segregated" to "no", "smoothness" to "good",
            "footway:smoothness" to "bad", "cycleway:smoothness" to "impassable",
        ))
    }

    @Test fun `areas are polygons`() {
        val style = assertIs<OverlayStyle.Polygon>(style("highway" to "pedestrian", "area" to "yes", "smoothness" to "good"))
        assertEquals(OverlayColor.Sky, style.color)
    }

    @Test fun `styled elements are roads and paths but not steps`() {
        // missing values are styled too, so they can be selected
        val road = way(1, tags = mapOf("highway" to "residential"))
        val path = way(2, tags = mapOf("highway" to "footway"))
        val steps = way(3, tags = mapOf("highway" to "steps", "smoothness" to "bad"))
        val other = way(4, tags = mapOf("amenity" to "parking", "smoothness" to "good"))
        val ids = SmoothnessOverlay()
            .getStyledElements(TestMapDataWithGeometry(listOf(road, path, steps, other)))
            .map { it.first.id }.toSet()
        assertEquals(setOf(1L, 2L), ids)
    }
}
