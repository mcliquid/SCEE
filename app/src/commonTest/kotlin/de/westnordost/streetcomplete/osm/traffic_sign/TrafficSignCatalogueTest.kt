package de.westnordost.streetcomplete.osm.traffic_sign

import androidx.compose.ui.unit.Density
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData.RESOURCES_DIR
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData.catalogueDE
import org.jetbrains.compose.resources.decodeToImageVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficSignCatalogueTest {

    @Test fun `bundled catalogue has the pinned version`() {
        assertEquals("DE", catalogueDE.country)
        assertEquals("0.8.0", catalogueDE.version)
        val versionFile = TrafficSignTestData.readText("$RESOURCES_DIR/files/traffic_signs/VERSION")
        assertTrue(versionFile.startsWith("@osm-traffic-signs/converter@0.8.0\n"))
    }

    @Test fun `bundled catalogue is complete`() {
        assertEquals(290, catalogueDE.signs.size)
        val byKind = catalogueDE.signs.groupingBy { it.kind }.eachCount()
        assertEquals(213, byKind[TrafficSignKind.TRAFFIC_SIGN])
        assertEquals(31, byKind[TrafficSignKind.EXCEPTION_MODIFIER])
        assertEquals(46, byKind[TrafficSignKind.CONDITION_MODIFIER])
        assertEquals(45, catalogueDE.relations.size)
    }

    @Test fun `osm value parts are unique`() {
        val duplicates = catalogueDE.signs.groupBy { it.osmValuePart }.filterValues { it.size > 1 }.keys
        assertEquals(emptySet(), duplicates)
    }

    @Test fun `every sign is found by its own osm value part`() {
        for (sign in catalogueDE.signs) {
            assertEquals(sign, catalogueDE.resolve(sign.osmValuePart)?.sign, sign.osmValuePart)
        }
    }

    @Test fun `every redirect leads to a sign`() {
        for (sign in catalogueDE.signs) {
            for (redirect in sign.redirects) {
                assertNotNull(catalogueDE.resolve(redirect.from), "${redirect.from} -> ${redirect.to}")
            }
        }
    }

    @Test fun `every sign with variable value has a default value`() {
        for (sign in catalogueDE.signs.filter { it.hasVariableValue }) {
            assertNotNull(sign.defaultValue, sign.osmValuePart)
        }
    }

    @Test fun `relations refer to signs`() {
        for (relation in catalogueDE.relations) {
            relation.start?.let { assertNotNull(catalogueDE.resolve(it), it) }
            relation.end?.let { assertNotNull(catalogueDE.resolve(it), it) }
        }
    }

    @Test fun `every sign has an image that can be decoded`() {
        val density = Density(1f)
        for (sign in catalogueDE.signs) {
            val bytes = TrafficSignTestData.readBytesOrNull("$RESOURCES_DIR/${sign.imagePath("DE")}")
            assertNotNull(bytes, "image of ${sign.osmValuePart}")
            val vector = bytes.decodeToImageVector(density)
            assertTrue(vector.defaultWidth.value > 0 && vector.defaultHeight.value > 0, sign.osmValuePart)
        }
    }

    @Test fun `image names`() {
        assertEquals("DE_274_30", trafficSignImageName("DE", "274-30"))
        assertEquals("DE_274__47__", trafficSignImageName("DE", "274[47]"))
        assertEquals("DE_274_1_20", trafficSignImageName("DE", "274.1-20"))
        assertEquals("DE__Kfz_Verkehr_frei_", trafficSignImageName("DE", "\"Kfz-Verkehr frei\""))
        assertEquals("DE_262__5_5__", trafficSignImageName("DE", "262[5.5]"))
    }

    @Test fun `resolve direct`() {
        val r = catalogueDE.resolve("274-30")!!
        assertEquals("274-30", r.osmValuePart)
        assertNull(r.value)
        assertEquals("274-30", r.sign.osmValuePart)
    }

    @Test fun `resolve redirects`() {
        assertEquals("274-30", catalogueDE.resolve("274[30]")!!.osmValuePart)
        assertEquals("241-30", catalogueDE.resolve("241")!!.osmValuePart)
        assertEquals("242.1", catalogueDE.resolve("242")!!.osmValuePart)
        assertEquals("274.1-20", catalogueDE.resolve("274.1[20]")!!.osmValuePart)
        assertEquals("\"Kleinkrafträder frei\"", catalogueDE.resolve("Kleinkrafträder frei")!!.osmValuePart)
        assertEquals("\"Kfz-Verkehr frei\"", catalogueDE.resolve("KFZ frei")!!.osmValuePart)
    }

    @Test fun `resolve redirects ignores case`() {
        assertEquals("\"Kfz-Verkehr frei\"", catalogueDE.resolve("kfz frei")!!.osmValuePart)
    }

    @Test fun `resolve variable value`() {
        val r = catalogueDE.resolve("274[33]")!!
        assertEquals("274[33]", r.osmValuePart)
        assertEquals("33", r.value)
        assertEquals("274[47]", r.sign.osmValuePart)
        // does not change the catalogue
        assertEquals("47", catalogueDE.getByOsmValuePart("274[47]")!!.defaultValue)
    }

    @Test fun `resolve variable value of sign with explicit variants`() {
        // 274.1 exists without and with variable value
        assertEquals("274.1", catalogueDE.resolve("274.1")!!.sign.osmValuePart)
        val r = catalogueDE.resolve("274.1[25]")!!
        assertEquals("274.1[47]", r.sign.osmValuePart)
        assertEquals("25", r.value)
    }

    @Test fun `resolve dash value`() {
        val r = catalogueDE.resolve("262-7.5")
        // only for signs with redirectDashValues
        if (catalogueDE.getBySignId("262").any { it.redirectDashValues }) {
            assertEquals("262[7.5]", r!!.osmValuePart)
            assertEquals("7.5", r.value)
        }
        assertEquals("262[7.5]", catalogueDE.resolve("262[7.5]")!!.osmValuePart)
    }

    @Test fun `resolve variable sign without value`() {
        val r = catalogueDE.resolve("274")!!
        assertEquals("274", r.osmValuePart)
        assertNull(r.value)
        assertTrue(r.sign.hasVariableValue)
    }

    @Test fun `resolve unknown`() {
        assertNull(catalogueDE.resolve("9999"))
        assertNull(catalogueDE.resolve("city_limit"))
        assertNull(catalogueDE.resolve(""))
    }

    @Test fun `parse invalid catalogue`() {
        assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse("{}") }
        assertFailsWith<IllegalArgumentException> { TrafficSignCatalogue.parse("not json") }
        assertFailsWith<IllegalArgumentException> {
            TrafficSignCatalogue.parse(catalogueJson(kind = "something_new"))
        }
        assertFailsWith<IllegalArgumentException> {
            TrafficSignCatalogue.parse(catalogueJson(tagRecommendations = "\"something\""))
        }
    }

    @Test fun `parse minimal catalogue and ignores unknown fields`() {
        val catalogue = TrafficSignCatalogue.parse(catalogueJson())
        val sign = catalogue.getByOsmValuePart("1")!!
        assertEquals("Eins", sign.descriptiveName)
        assertNull(sign.tagRecommendations)
        assertEquals(TrafficSignKind.TRAFFIC_SIGN, sign.kind)
    }

    @Test fun `catalogue provider`() {
        val catalogues = TrafficSignCatalogues { country -> if (country == "DE") catalogueJson() else null }
        assertNotNull(catalogues.get("DE"))
        assertNull(catalogues.get("AT"))
        assertNull(catalogues.get("FR"))
    }

    @Test fun `catalogue provider with broken catalogue`() {
        val catalogues = TrafficSignCatalogues { "broken" }
        assertNull(catalogues.get("DE"))
    }

    private fun catalogueJson(kind: String = "traffic_sign", tagRecommendations: String = "\"none\"") = """
        {
          "country": "DE", "version": "0.0.0", "unknownField": 1,
          "signs": [{
            "osmValuePart": "1", "signId": "1", "name": "Zeichen 1", "descriptiveName": "Eins",
            "kind": "$kind", "catalogue": { "signCategory": "traffic_sign", "focus": {} },
            "tagRecommendationsByGeometry": $tagRecommendations, "image": "missing", "other": [1, 2]
          }]
        }
    """.trimIndent()
}
