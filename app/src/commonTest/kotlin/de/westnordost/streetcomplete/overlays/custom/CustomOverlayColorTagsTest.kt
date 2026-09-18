package de.westnordost.streetcomplete.overlays.custom

import kotlin.test.Test
import kotlin.test.assertEquals

class CustomOverlayColorTagsTest {

    @Test fun `formats a single color tag`() {
        assertEquals(
            "surface = asphalt",
            formatCustomOverlayColorTags(mapOf("surface" to "asphalt"))
        )
    }

    @Test fun `formats multiple color tags sorted by key on separate lines`() {
        assertEquals(
            "building:levels = 3\nsurface = grass",
            formatCustomOverlayColorTags(mapOf(
                "surface" to "grass",
                "building:levels" to "3",
            ))
        )
    }

    @Test fun `preserves long values on one line per tag`() {
        val longValue = "a".repeat(80)
        assertEquals(
            "note = $longValue",
            formatCustomOverlayColorTags(mapOf("note" to longValue))
        )
    }
}
