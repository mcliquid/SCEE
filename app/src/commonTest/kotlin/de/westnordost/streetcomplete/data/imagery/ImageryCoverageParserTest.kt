package de.westnordost.streetcomplete.data.imagery

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImageryCoverageParserTest {

    @Test fun `bbox coverage contains point`() {
        val parsed = ImageryCoverageParser.parse(
            ManualRasterCoverage(minLat = 0.0, minLon = 0.0, maxLat = 2.0, maxLon = 2.0)
        )
        assertNotNull(parsed)
        assertTrue(parsed.contains(LatLon(1.0, 1.0)))
        assertFalse(parsed.contains(LatLon(3.0, 1.0)))
    }

    @Test fun `geojson polygon coverage`() {
        val geo = """
            {"type":"Polygon","coordinates":[[[0,0],[2,0],[2,2],[0,2],[0,0]]]}
        """.trimIndent()
        val parsed = ImageryCoverageParser.parse(ManualRasterCoverage(geoJson = geo))
        assertNotNull(parsed)
        assertTrue(parsed.contains(LatLon(1.0, 1.0)))
        assertFalse(parsed.contains(LatLon(5.0, 5.0)))
    }

    @Test fun `feature collection multipolygon`() {
        val geo = """
            {"type":"FeatureCollection","features":[{
              "type":"Feature","properties":{},
              "geometry":{"type":"MultiPolygon","coordinates":[[[[10,48],[11,48],[11,49],[10,49],[10,48]]]]}
            }]}
        """.trimIndent()
        val parsed = ImageryCoverageParser.parseGeoJson(geo)
        assertNotNull(parsed)
        assertTrue(parsed.contains(LatLon(48.5, 10.5)))
        assertFalse(parsed.contains(LatLon(0.0, 0.0)))
    }

    @Test fun `builtin bayern coverage includes munich`() {
        val bayern = BuiltinManualRasterSources.all.find { it.id == BuiltinManualRasterSources.BAYERN_ID }!!
        val parsed = ImageryCoverageParser.parse(bayern.coverage)
        assertNotNull(parsed)
        assertTrue(parsed.contains(LatLon(48.137, 11.575)))
        assertFalse(parsed.contains(LatLon(52.52, 13.405))) // Berlin
    }

    @Test fun `builtin bw coverage includes stuttgart`() {
        val bw = BuiltinManualRasterSources.all.find { it.id == BuiltinManualRasterSources.BW_ID }!!
        val parsed = ImageryCoverageParser.parse(bw.coverage)
        assertNotNull(parsed)
        assertTrue(parsed.contains(LatLon(48.778, 9.180)))
        assertFalse(parsed.contains(LatLon(48.137, 11.575))) // Munich
    }
}
