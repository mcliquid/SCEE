package de.westnordost.streetcomplete.osm.traffic_sign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The rules that derive tags from signs (WAY_RULES) are curated, not generated from the catalogue.
 *  This checks that they agree with what the DE catalogue says for the same signs, so that rules and
 *  catalogue do not drift apart, and that no rule has been added without being noticed. */
class TrafficSignRuleCatalogueTest {

    private val catalogue = TrafficSignTestData.catalogueDE

    @Test fun `exactly the 11 reviewed rules exist`() {
        val rules = SUPPORTED_WAY_TRAFFIC_SIGNS.getValue("DE")
        assertEquals(
            setOf(
                listOf("237"), listOf("239"), listOf("239", "1022-10"), listOf("240"), listOf("241-30"), listOf("241-31"),
                listOf("250"), listOf("250", "1020-30"), listOf("260"), listOf("260", "1020-30"), listOf("260", "1026-36"),
            ),
            rules
        )
        assertEquals(setOf("DE"), SUPPORTED_WAY_TRAFFIC_SIGNS.keys)
    }

    @Test fun `every sign of a rule is in the catalogue`() {
        for (rule in SUPPORTED_WAY_TRAFFIC_SIGNS.getValue("DE")) for (osmValuePart in rule) {
            assertNotNull(catalogue.getByOsmValuePart(osmValuePart), osmValuePart)
        }
    }

    private fun recommendation(osmValuePart: String) =
        catalogue.getByOsmValuePart(osmValuePart)!!.tagRecommendations!!.single { "way" in it.geometries }

    private fun derived(value: String, tags: Map<String, String>): Map<String, String> {
        val t = TrafficSignWaySigns.both(parseDE(value)).deriveWayTags(tags)
        val result = tags.toMutableMap()
        for (c in t.changes) when (c) {
            is TrafficSignTagChange.Set -> result[c.key] = c.value
            is TrafficSignTagChange.Remove -> result.remove(c.key)
        }
        return result
    }

    @Test fun `bans give the access tag of the catalogue`() {
        for ((sign, key) in listOf("250" to "vehicle", "260" to "motor_vehicle")) {
            val catalogueTag = recommendation(sign).accessTags.single()
            assertEquals(key to "no", catalogueTag, sign)
            assertEquals("no", derived("DE:$sign", mapOf("highway" to "residential"))[key], sign)
        }
    }

    @Test fun `exceptions give the value of the additional sign of the catalogue`() {
        for ((rule, key) in listOf(
            listOf("250", "1020-30") to "vehicle", listOf("260", "1020-30") to "motor_vehicle", listOf("260", "1026-36") to "motor_vehicle",
        )) {
            val modifier = rule[1]
            val value = recommendation(modifier).modifierValue
            assertNotNull(value, modifier)
            assertEquals(value, derived("DE:${rule[0]},$modifier", mapOf("highway" to "service"))[key], rule.toString())
        }
    }

    @Test fun `path signs give the unique tags of the catalogue or have them implied by the way type`() {
        val tags = mapOf("highway" to "path")
        // tags of the catalogue that the way type already says: highway=cycleway is bicycle=designated,
        // highway=footway is foot=designated (SeparateCycleway.EXCLUSIVE / NON_DESIGNATED_ON_FOOTWAY)
        val impliedByHighway = mapOf("237" to setOf("bicycle"), "239" to setOf("foot"))
        for (sign in listOf("237", "239", "240", "241-30", "241-31")) {
            val result = derived("DE:$sign", tags)
            for ((key, value) in recommendation(sign).uniqueTags) {
                if (key in impliedByHighway[sign].orEmpty()) {
                    assertEquals(value, "designated", "$sign: $key")
                    assertTrue(key !in result || result[key] == value, "$sign: $key")
                } else {
                    assertEquals(value, result[key], "$sign: $key")
                }
            }
        }
        assertEquals("cycleway", derived("DE:237", tags)["highway"])
        assertEquals("footway", derived("DE:239", tags)["highway"])
    }

    @Test fun `deviations from the catalogue are the documented ones`() {
        // DE:240: the catalogue says highway=path, SCEE leaves footway/cycleway/path as they are because
        // all three are valid for a shared foot and cycleway (SeparateCyclewayParser)
        assertEquals(listOf("path"), recommendation("240").highwayValues)
        assertEquals("footway", derived("DE:240", mapOf("highway" to "footway"))["highway"])
        // DE:239,1022-10: the additional sign says bicycle=yes, SCEE writes bicycle:signed=yes on a footway
        val footway = derived("DE:239,1022-10", mapOf("highway" to "footway"))
        assertEquals("yes", footway["bicycle:signed"])
        assertTrue(recommendation("1022-10").uniqueTags.contains("bicycle" to "yes"))
    }

    @Test fun `signs without a rule only record the sign`() {
        for (value in listOf("DE:267", "DE:254", "DE:259", "DE:251", "DE:253", "DE:255", "DE:244.1", "DE:274-30", "DE:325.1")) {
            val t = TrafficSignWaySigns.both(parseDE(value)).deriveWayTags(mapOf("highway" to "residential"))
            assertTrue(t.proposals.isEmpty(), value)
            assertTrue(t.changes.all { it.key.startsWith("traffic_sign") }, "$value ${t.changes}")
        }
    }
}
