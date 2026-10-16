package de.westnordost.streetcomplete.data.imagery.eli

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EliIndexParserTest {

    private val squareFeature = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "properties": {
                "id": "Test-Best",
                "name": "Test Best",
                "type": "tms",
                "category": "photo",
                "best": true,
                "url": "https://example.com/{zoom}/{x}/{y}.png",
                "max_zoom": 20,
                "end_date": "2024"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [[
                  [0.0, 0.0], [2.0, 0.0], [2.0, 2.0], [0.0, 2.0], [0.0, 0.0]
                ]]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "id": "Test-Hole",
                "name": "Test Hole",
                "type": "tms",
                "category": "photo",
                "url": "https://example.com/{z}/{x}/{y}.png"
              },
              "geometry": {
                "type": "Polygon",
                "coordinates": [
                  [[0.0, 0.0], [4.0, 0.0], [4.0, 4.0], [0.0, 4.0], [0.0, 0.0]],
                  [[1.0, 1.0], [1.0, 3.0], [3.0, 3.0], [3.0, 1.0], [1.0, 1.0]]
                ]
              }
            },
            {
              "type": "Feature",
              "properties": {
                "id": "EsriWorldImagery",
                "name": "Esri",
                "type": "tms",
                "category": "photo",
                "url": "https://server.arcgisonline.com/tile/{zoom}/{y}/{x}"
              },
              "geometry": null
            },
            {
              "type": "Feature",
              "properties": {
                "id": "Overlay-Skip",
                "name": "Overlay",
                "type": "tms",
                "category": "photo",
                "overlay": true,
                "url": "https://example.com/{z}/{x}/{y}.png"
              },
              "geometry": null
            },
            {
              "type": "Feature",
              "properties": {
                "id": "Bing-Skip",
                "name": "Bing",
                "type": "bing",
                "category": "photo",
                "url": "https://www.bing.com/maps"
              },
              "geometry": null
            }
          ]
        }
    """.trimIndent()

    @Test fun `parse and filter supported`() {
        val layers = EliIndexParser.parse(squareFeature)
        assertEquals(5, layers.size)
        val index = EliIndex(layers)
        assertEquals(3, index.allSupported.size) // overlay + bing filtered
        assertNull(index.findById("Overlay-Skip"))
        assertNull(index.findById("Bing-Skip"))
    }

    @Test fun `point in polygon and hole`() {
        val index = EliIndex(EliIndexParser.parse(squareFeature))
        assertTrue(index.covering(LatLon(1.0, 1.0)).any { it.id == "Test-Best" })
        assertFalse(index.covering(LatLon(1.0, 1.0)).any { it.id == "Test-Hole" }) // in hole
        assertTrue(index.covering(LatLon(0.5, 0.5)).any { it.id == "Test-Hole" })
        assertTrue(index.covering(LatLon(10.0, 10.0)).any { it.id == "EsriWorldImagery" })
    }

    @Test fun `best at prefers best flag`() {
        val index = EliIndex(EliIndexParser.parse(squareFeature))
        assertEquals("Test-Best", index.bestAt(LatLon(1.0, 1.0))?.id)
        assertNull(index.bestAt(LatLon(50.0, 50.0))) // only worldwide Esri, not best
    }

    @Test fun `multipolygon coverage`() {
        val geo = """
        {"type":"FeatureCollection","features":[{
          "type":"Feature",
          "properties":{"id":"MP","name":"MP","type":"tms","category":"photo",
            "url":"https://ex.com/{z}/{x}/{y}.png"},
          "geometry":{"type":"MultiPolygon","coordinates":[
            [[[0,0],[1,0],[1,1],[0,1],[0,0]]],
            [[[5,5],[6,5],[6,6],[5,6],[5,5]]]
          ]}
        }]}
        """.trimIndent()
        val index = EliIndex(EliIndexParser.parse(geo))
        assertTrue(index.covering(LatLon(0.5, 0.5)).any { it.id == "MP" })
        assertTrue(index.covering(LatLon(5.5, 5.5)).any { it.id == "MP" })
        assertFalse(index.covering(LatLon(3.0, 3.0)).any { it.id == "MP" })
    }
}
