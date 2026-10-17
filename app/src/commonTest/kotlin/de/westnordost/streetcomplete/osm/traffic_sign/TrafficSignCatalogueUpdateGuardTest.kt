package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData.RESOURCES_DIR
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Guards for the update of the catalogue (./gradlew :app:updateTrafficSigns, see
 *  app/traffic-signs-update/README.md): everything that depends on the version has to be updated
 *  together and by hand where it is a decision, and a new or changed catalogue must not change the
 *  tags that are derived from signs. */
class TrafficSignCatalogueUpdateGuardTest {

    private val json = TrafficSignTestData.readText("$RESOURCES_DIR/files/traffic_signs/DE.json")
    private val catalogue = TrafficSignTestData.catalogueDE

    /* ------------------------------------- everything agrees on the version ------------------------------------- */

    private val versionFile = TrafficSignTestData.readText("$RESOURCES_DIR/files/traffic_signs/VERSION").lines()

    @Test fun `the version of the catalogue, the version file, the build pin, the parity reference and the tooling agree`() {
        val version = catalogue.version
        assertEquals("@osm-traffic-signs/converter@$version", versionFile[0])
        val sha = versionFile[1].removePrefix("sha256 ")
        assertEquals("source https://registry.npmjs.org/@osm-traffic-signs/converter/-/converter-$version.tgz", versionFile[2])
        val gradle = TrafficSignTestData.readText("build.gradle.kts")
        assertEquals(version, Regex("val trafficSignsVersion = \"([^\"]+)\"").find(gradle)!!.groupValues[1], "pin in build.gradle.kts")
        assertEquals(sha, Regex("val trafficSignsSha256 = \"([^\"]+)\"").find(gradle)!!.groupValues[1], "hash in build.gradle.kts")
        // the reference of the converter parity is made with the same converter version
        val reference = TrafficSignTestData.readText("../tools/traffic-sign-parity/reference.jsonl").lineSequence().first()
        assertTrue("\"converter\":\"@osm-traffic-signs/converter@$version\"" in reference, "parity reference: $reference")
        val tooling = TrafficSignTestData.readText("../tools/traffic-sign-parity/package.json")
        assertTrue("\"@osm-traffic-signs/converter\": \"$version\"" in tooling, "tools/traffic-sign-parity/package.json")
    }

    @Test fun `the signs of the rules file are exactly the signs of the curated rules`() {
        val listed = TrafficSignTestData.readText("traffic-signs-update/rule-signs.txt").lines()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val used = SUPPORTED_WAY_TRAFFIC_SIGNS.getValue("DE").flatten().toSet()
        assertEquals(used, listed, "traffic-signs-update/rule-signs.txt must list the signs of WAY_RULES")
        for (id in listed) assertNotNull(catalogue.getByOsmValuePart(id), id)
    }

    /* ---------------------- a new or changed catalogue does not change the derived tags ---------------------- */

    private fun parsed(modifiedJson: String) = TrafficSignCatalogue.parse(modifiedJson)

    private fun signsJson(sign: String) = json.replaceFirst("\"signs\": [", "\"signs\": [$sign,")

    private val newSign = """{"osmValuePart": "9998", "signId": "9998", "name": "Zeichen 9998", "descriptiveName": "Neues Verbot", "kind": "traffic_sign",
        "catalogue": {"signCategory": "traffic_sign"}, "tagRecommendationsByGeometry": [{"geometries": ["way"], "accessTags": [{"key": "vehicle", "value": "no"}]}], "image": "x"}"""

    private fun tagsAfter(value: TrafficSignValue, tags: Map<String, String>) = value.deriveWayTags(tags).changes.associate { it.key to it.javaClass.simpleName }

    @Test fun `a new sign with access recommendations is recorded but derives no tags`() {
        // the format of the JSON has to have a first sign for the replace above
        assertTrue("\"signs\": [" in json, "format of DE.json")
        val c = parsed(signsJson(newSign))
        assertEquals(catalogue.signs.size + 1, c.signs.size)
        val value = parseTrafficSignValue("DE:9998", c)
        assertTrue(value.isFullyRecognized)
        val t = value.deriveWayTags(mapOf("highway" to "residential"))
        assertTrue(t.proposals.isEmpty())
        assertEquals(setOf("traffic_sign"), t.changes.map { it.key }.toSet())
        assertEquals("DE:9998", t.changes.single().let { (it as TrafficSignTagChange.Set).value })
    }

    @Test fun `changed recommendations of the signs of the rules do not change the rules`() {
        val changed = json.replace(Regex("""\"key\"\s*:\s*\"vehicle\"\s*,\s*\"value\"\s*:\s*\"no\""""), "\"key\": \"vehicle\", \"value\": \"destination\"")
        assertTrue(changed != json)
        val c = parsed(changed)
        for (value in listOf("DE:250", "DE:260", "DE:250,1020-30", "DE:239", "DE:240", "DE:237")) {
            val before = parseTrafficSignValue(value, catalogue).deriveWayTags(mapOf("highway" to "path"))
            val after = parseTrafficSignValue(value, c).deriveWayTags(mapOf("highway" to "path"))
            assertEquals(before.changes, after.changes, value)
        }
    }

    @Test fun `a removed sign stays as an unknown fragment and is kept`() {
        val root = Json.parseToJsonElement(json).jsonObject
        val signs = root.getValue("signs").jsonArray.filter { it.jsonObject.getValue("osmValuePart").jsonPrimitive.content != "1020-12" }
        assertEquals(catalogue.signs.size - 1, signs.size)
        val without = TrafficSignCatalogue.parse(JsonObject(root + ("signs" to JsonArray(signs))).toString())
        assertNull(without.getByOsmValuePart("1020-12"))
        val value = parseTrafficSignValue("DE:250,1020-12;274-30", without)
        assertEquals("DE:250,1020-12;274-30", value.toOsmValue())
        assertTrue(value.parts.any { !it.isRecognized && it.text == "1020-12" })
        // unknown parts give no rule: only the sign is recorded
        assertTrue(value.deriveWayTags(mapOf("highway" to "residential")).proposals.isEmpty())
    }

    @Test fun `a catalogue with a changed structure is read, unknown fields are ignored, a changed kind is rejected per sign`() {
        val extra = json.replaceFirst("\"signs\": [", "\"newTopLevel\": 1, \"signs\": [")
        assertEquals(catalogue.signs.size, parsed(extra).signs.size)
        val result = TrafficSignCatalogue.parseLenient(json.replaceFirst("\"kind\": \"traffic_sign\"", "\"kind\": \"brand_new_kind\""))
        assertEquals(catalogue.signs.size - 1, result.catalogue!!.signs.size)
        assertEquals(1, result.problems.size)
    }

    @Test fun `the checks of the update task are covered by the validation of the app`() {
        // the bundled catalogue passes the same rules the update task applies before it writes anything
        val problems = TrafficSignCatalogue.validate(json) { sign ->
            TrafficSignTestData.readBytesOrNull("$RESOURCES_DIR/${sign.imagePath("DE")}") != null
        }
        assertEquals(emptyList(), problems)
    }
}
