package de.westnordost.streetcomplete.data.imagery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RasterTileUrlNormalizerTest {

    @Test fun `valid xyz with zoom`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/{zoom}/{x}/{y}.png"
        ))
    }

    @Test fun `valid xyz with z`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/{z}/{x}/{y}.png"
        ))
    }

    @Test fun `valid tms with flipped y`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/{z}/{x}/{-y}.png"
        ))
    }

    @Test fun `valid bbox-epsg`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/?BBOX={bbox-epsg-3857}"
        ))
    }

    @Test fun `valid bbox with proj`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/?BBOX={bbox}&CRS={proj}"
        ))
    }

    @Test fun `valid bbox with wkid`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/?BBOX={bbox}&imageSR={wkid}"
        ))
    }

    @Test fun `valid bbox alone`() {
        assertTrue(RasterTileUrlNormalizer.isValidTemplate(
            "https://example.com/?BBOX={bbox}&CRS=EPSG:3857"
        ))
    }

    @Test fun `invalid without placeholders`() {
        assertFalse(RasterTileUrlNormalizer.isValidTemplate("https://example.com/tile.png"))
    }

    @Test fun `normalize zoom bbox proj wkid dimensions`() {
        val n = RasterTileUrlNormalizer.normalize(
            "https://ex.com/{zoom}/{x}/{y}?b={bbox}&p={proj}&w={wkid}&W={width}&H={height}"
        )
        assertEquals(1, n.tiles.size)
        assertEquals(
            "https://ex.com/{z}/{x}/{y}?b={bbox-epsg-3857}&p=EPSG:3857&w=3857&W=256&H=256",
            n.tiles.single()
        )
        assertEquals(RasterTileScheme.Xyz, n.scheme)
    }

    @Test fun `switch regex escapes closing brace for Android ICU`() {
        // Host/JVM tests alone cannot reproduce PatternSyntaxException: OpenJDK allows
        // the buggy pattern `\{switch:([^}]+)}`, Android ICU does not. Guard the shape.
        assertEquals("""\{switch:([^}]+)\}""", RasterTileUrlNormalizer.SWITCH_PATTERN)
        assertTrue(
            RasterTileUrlNormalizer.SWITCH_PATTERN.endsWith("""\}"""),
            "trailing `}` must be escaped for Android ICU"
        )
        // Force <clinit> of RasterTileUrlNormalizer (regex compile) — must not throw.
        RasterTileUrlNormalizer.isValidTemplate("https://example.com/{z}/{x}/{y}.png")
    }

    @Test fun `normalize switch expands hosts`() {
        // Also exercises class initialization + switch expansion used by Esri/ELI URLs.
        val n = RasterTileUrlNormalizer.normalize(
            "https://{switch:a,b,c}.example.com/{z}/{x}/{y}.png"
        )
        assertEquals(
            listOf(
                "https://a.example.com/{z}/{x}/{y}.png",
                "https://b.example.com/{z}/{x}/{y}.png",
                "https://c.example.com/{z}/{x}/{y}.png",
            ),
            n.tiles
        )
    }

    @Test fun `normalize switch with services server like Esri`() {
        val n = RasterTileUrlNormalizer.normalize(
            "https://{switch:services,server}.arcgisonline.com/arcgis/rest/services/World_Imagery/MapServer/tile/{zoom}/{y}/{x}"
        )
        assertEquals(2, n.tiles.size)
        assertTrue(n.tiles[0].startsWith("https://services.arcgisonline.com/"))
        assertTrue(n.tiles[1].startsWith("https://server.arcgisonline.com/"))
        assertTrue(n.tiles.all { "{z}" in it && "{zoom}" !in it })
    }

    @Test fun `normalize flipped y sets tms scheme`() {
        val n = RasterTileUrlNormalizer.normalize("https://ex.com/{z}/{x}/{-y}.png")
        assertEquals("https://ex.com/{z}/{x}/{y}.png", n.tiles.single())
        assertEquals(RasterTileScheme.Tms, n.scheme)
    }
}
