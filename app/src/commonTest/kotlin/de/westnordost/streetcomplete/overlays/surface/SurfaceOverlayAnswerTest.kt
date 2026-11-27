package de.westnordost.streetcomplete.overlays.surface

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.surface.Surface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SurfaceOverlayAnswerTest {

    private fun SegregatedSurface.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.toMap()
    }

    @Test fun `changing one part surface invalidates only that part smoothness`() {
        val tags = mapOf(
            "highway" to "path",
            "segregated" to "yes",
            "surface" to "asphalt",
            "footway:surface" to "compacted",
            "cycleway:surface" to "asphalt",
            "smoothness" to "good",
            "footway:smoothness" to "intermediate",
            "cycleway:smoothness" to "excellent",
        )
        val result = SegregatedSurface(footway = Surface.ASPHALT, cycleway = null).appliedTo(tags)
        assertEquals("asphalt", result["footway:surface"])
        assertNull(result["footway:smoothness"])
        assertEquals("excellent", result["cycleway:smoothness"])
        assertEquals("good", result["smoothness"])
    }
}