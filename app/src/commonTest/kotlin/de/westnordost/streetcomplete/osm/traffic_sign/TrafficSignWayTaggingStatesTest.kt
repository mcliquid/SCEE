package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.FULL
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection.BACKWARD
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection.FORWARD
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The three states per direction (signed / none / not surveyed), partial edits and invariants */
class TrafficSignWayTaggingStatesTest {

    /* ------------------------------------- not surveyed ------------------------------------- */

    @Test fun `nothing surveyed changes nothing`() {
        for (tags in TAGS) {
            assertEquals(emptyList(), derive(null, null, tags).changes, tags.toString())
        }
    }

    @Test fun `only forward surveyed keeps general traffic_sign for backward`() {
        val tags = mapOf("highway" to "footway", "bicycle" to "designated", "segregated" to "no", "traffic_sign" to "DE:240")
        val r = derive("DE:239", null, tags)
        assertEquals(
            mapOf(
                "highway" to "footway",
                // backward still a shared foot- and cycleway
                "bicycle:backward" to "designated",
                "segregated" to "no",
                "traffic_sign:forward" to "DE:239",
                "traffic_sign:backward" to "DE:240",
            ),
            r.appliedTo(tags)
        )
        assertEquals("DE:239", r.trafficSignForward)
        assertEquals("DE:240", r.trafficSignBackward)
        assertNull(r.trafficSign)
    }

    @Test fun `only backward surveyed keeps directional traffic_sign of forward`() {
        val tags = mapOf("highway" to "residential", "traffic_sign:forward" to "DE:274-30")
        val r = derive(null, "DE:250", tags)
        assertEquals(
            mapOf("highway" to "residential", "vehicle:backward" to "no", "traffic_sign:forward" to "DE:274-30", "traffic_sign:backward" to "DE:250"),
            r.appliedTo(tags)
        )
    }

    @Test fun `only one direction surveyed with nothing tagged before`() {
        val tags = mapOf("highway" to "residential")
        val result = derive(null, "DE:250", tags).appliedTo(tags)
        assertEquals("DE:250", result["traffic_sign:backward"])
        assertNull(result["traffic_sign:forward"])
        assertNull(result["traffic_sign"])
    }

    @Test fun `surveyed value equal to the not surveyed one is tagged as traffic_sign`() {
        val tags = mapOf("highway" to "footway", "traffic_sign:forward" to "DE:239", "traffic_sign:backward" to "DE:240")
        val r = derive(null, "DE:239", tags)
        assertEquals("DE:239", r.appliedTo(tags)["traffic_sign"])
        assertNull(r.appliedTo(tags)["traffic_sign:forward"])
        assertEquals("DE:239", r.trafficSign)
    }

    @Test fun `mixed general and directional traffic_sign with partial edit`() {
        // traffic_sign applies forward only, because backward has its own value
        val tags = mapOf("highway" to "path", "traffic_sign" to "DE:240", "traffic_sign:backward" to "DE:239")
        val r = derive(null, "DE:241-30", tags)
        val result = r.appliedTo(tags)
        assertEquals("DE:240", result["traffic_sign:forward"])
        assertEquals("DE:241-30", result["traffic_sign:backward"])
        assertNull(result["traffic_sign"])
        // traffic_sign=DE:240 still applies to forward: no conflict, only a different notation
        assertTrue(r.resolvedConflicts.none { it.key == "traffic_sign" })
    }

    @Test fun `not surveyed direction prevents re-classification`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:239,1022-10", null, tags)
        assertEquals(tags + ("traffic_sign:forward" to "DE:239,1022-10"), r.appliedTo(tags))
        assertEquals(listOf(TrafficSignNotDerived(Reason.HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS, "footway", FORWARD)), r.notDerived)
    }

    @Test fun `not surveyed never becomes none`() {
        for (tags in TAGS) {
            val result = derive("DE:240", null, tags).appliedTo(tags)
            assertFalse("none" == result["traffic_sign:backward"] && tags["traffic_sign:backward"] != "none", tags.toString())
            assertFalse("none" == result["traffic_sign"] && tags["traffic_sign"] != "none", tags.toString())
        }
    }

    @Test fun `not surveyed direction keeps its traffic sign and access`() {
        for (tags in TAGS) for (signs in SIGNS) {
            for ((f, b, untouched) in listOf(Triple(signs, null, BACKWARD), Triple(null, signs, FORWARD))) {
                val result = derive(f, b, tags).appliedTo(tags)
                assertEquals(trafficSignOf(tags, untouched), trafficSignOf(result, untouched), "$f / $b on $tags")
                for (key in ACCESS_KEYS) {
                    assertEquals(effectiveAccess(tags, untouched, key), effectiveAccess(result, untouched, key), "$key: $f / $b on $tags")
                }
                assertEquals(tags["highway"], result["highway"], "$f / $b on $tags")
            }
        }
    }

    /* ----------------------------------------- none ----------------------------------------- */

    @Test fun `none does not change access tags`() {
        for (tags in TAGS) {
            for ((f, b) in listOf("none" to "none", "none" to null, null to "none")) {
                val result = derive(f, b, tags).appliedTo(tags)
                assertEquals(withoutTrafficSigns(tags), withoutTrafficSigns(result), "$f / $b on $tags")
            }
        }
    }

    @Test fun `none in one direction does not change access of that direction`() {
        for (tags in TAGS) for (signs in SIGNS) {
            for ((f, b, noneDir) in listOf(Triple("none", signs, FORWARD), Triple(signs, "none", BACKWARD))) {
                val r = derive(f, b, tags)
                // re-classification is not allowed if a direction has no signs, so the implied
                // access stays the same as well
                if (r.highwayChange != null) error("highway changed for $f / $b on $tags")
                val result = r.appliedTo(tags)
                for (key in ACCESS_KEYS) {
                    assertEquals(effectiveAccess(tags, noneDir, key), effectiveAccess(result, noneDir, key), "$key: $f / $b on $tags")
                }
                assertEquals("none", trafficSignOf(result, noneDir))
            }
        }
    }

    @Test fun `none in one direction with traffic_sign before`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "traffic_sign" to "DE:240")
        val result = derive("none", null, tags).appliedTo(tags)
        assertEquals(mapOf("highway" to "path", "bicycle" to "designated", "traffic_sign:forward" to "none", "traffic_sign:backward" to "DE:240"), result)
    }

    /* ---------------------------- access inheritance (example) ------------------------------ */

    @Test fun `ban in one direction with more specific general access`() {
        // motor_vehicle=yes is more specific than vehicle:backward=no. Which one wins is not
        // unambiguous (mode or direction), so motor_vehicle:backward=no is required as well
        val tags = mapOf("highway" to "residential", "motor_vehicle" to "yes")
        val r = derive("none", "DE:250", tags)
        assertEquals(
            mapOf(
                "highway" to "residential", "motor_vehicle" to "yes",
                "vehicle:backward" to "no", "motor_vehicle:backward" to "no",
                "traffic_sign:forward" to "none", "traffic_sign:backward" to "DE:250",
            ),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
    }

    @Test fun `ban in one direction without more specific access`() {
        val tags = mapOf("highway" to "residential")
        val result = derive("none", "DE:250", tags).appliedTo(tags)
        assertEquals(mapOf("highway" to "residential", "vehicle:backward" to "no", "traffic_sign:forward" to "none", "traffic_sign:backward" to "DE:250"), result)
    }

    @Test fun `ban in one direction that is already tagged generally`() {
        val tags = mapOf("highway" to "residential", "vehicle" to "no")
        val result = derive("none", "DE:250", tags).appliedTo(tags)
        assertEquals(tags + ("traffic_sign:forward" to "none") + ("traffic_sign:backward" to "DE:250"), result)
    }

    @Test fun `ban in one direction keeps more restrictive and independent access`() {
        val tags = mapOf("highway" to "residential", "hgv" to "no", "foot" to "yes", "horse" to "no")
        val result = derive("none", "DE:250", tags).appliedTo(tags)
        assertEquals("no", result["hgv"])
        assertEquals("yes", result["foot"])
        assertEquals("no", result["horse"])
        assertNull(result["hgv:backward"])
    }

    @Test fun `ban in one direction with exception in the other direction tagged`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle:forward" to "destination")
        val result = derive("DE:250", "none", tags).appliedTo(tags)
        // forward: the more permissive directional exception is removed and inherits the ban
        assertEquals("no", result["vehicle:forward"])
        assertNull(result["motor_vehicle:forward"])
    }

    @Test fun `ban with existing conditional access is not derived in that direction`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle:backward:conditional" to "no @ (Mo-Fr 07:00-09:00)")
        val r = derive("none", "DE:250", tags)
        assertEquals(tags + ("traffic_sign:forward" to "none") + ("traffic_sign:backward" to "DE:250"), r.appliedTo(tags))
        assertEquals(listOf(Reason.CONDITIONAL_TAGS_PRESENT), r.notDerived.map { it.reason })
    }

    /* --------------------------------------- invariants ------------------------------------- */

    @Test fun `applying the same selection again changes nothing`() {
        for (tags in TAGS) for (f in SIGNS_AND_STATES) for (b in SIGNS_AND_STATES) {
            val once = derive(f, b, tags).appliedTo(tags)
            val twice = derive(f, b, once).changes
            assertEquals(emptyList(), twice, "$f / $b on $tags -> $once")
        }
    }

    @Test fun `reversing the way and the signs gives the reversed result`() {
        for (tags in TAGS) for (f in SIGNS_AND_STATES) for (b in SIGNS_AND_STATES) {
            val result = derive(f, b, tags).appliedTo(tags)
            val reversedResult = derive(b, f, reverse(tags)).appliedTo(reverse(tags))
            assertEquals(reverse(result), reversedResult, "$f / $b on $tags")
        }
    }

    @Test fun `no forbidden keys`() {
        val forbidden = Regex("^(highway|segregated|bicycle:signed)(:.*)?:(forward|backward)(:.*)?$|^bicycle:(forward|backward):signed$")
        for (tags in TAGS) for (f in SIGNS_AND_STATES) for (b in SIGNS_AND_STATES) {
            for (key in derive(f, b, tags).appliedTo(tags).keys) assertFalse(forbidden.matches(key), "$key: $f / $b on $tags")
        }
    }

    @Test fun `signs are always stored`() {
        for (tags in TAGS) for (f in SIGNS_AND_STATES) for (b in SIGNS_AND_STATES) {
            val result = derive(f, b, tags).appliedTo(tags)
            if (f != null) assertEquals(parseDE(f).toOsmValue(), trafficSignOf(result, FORWARD), "$f / $b on $tags")
            if (b != null) assertEquals(parseDE(b).toOsmValue(), trafficSignOf(result, BACKWARD), "$f / $b on $tags")
        }
    }

    @Test fun `re-classification does not silently change access`() {
        // highway is only changed if both directions are foot- or cycleway signs, which then
        // determine bicycle and foot explicitly or by the new highway, as the signs say
        for (tags in TAGS) for (f in SIGNS_AND_STATES) for (b in SIGNS_AND_STATES) {
            val r = derive(f, b, tags)
            if (r.highwayChange == null) continue
            val pathSigns = setOf("DE:237", "DE:239", "DE:239,1022-10", "DE:240", "DE:241-30")
            assertTrue(f in pathSigns && b in pathSigns, "$f / $b on $tags")
        }
    }

    /* ---------------------------------------- helpers --------------------------------------- */

    private fun derive(forward: String?, backward: String?, tags: Map<String, String>) =
        TrafficSignWaySigns(forward?.let { parseDE(it) }, backward?.let { parseDE(it) }).deriveWayTags(tags)

    private fun TrafficSignWayTagging.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.entries.associate { it.key to it.value }
    }

    private fun trafficSignOf(tags: Map<String, String>, dir: TrafficSignDirection): String? =
        tags["traffic_sign:${dir.osmSuffix}"] ?: tags["traffic_sign"]

    private fun withoutTrafficSigns(tags: Map<String, String>) = tags.filterKeys { !it.startsWith("traffic_sign") }

    /** access for [key] in [dir], following direction and the access hierarchy. Directional values
     *  are regarded as more specific than more specific modes, see test about it above */
    private fun effectiveAccess(tags: Map<String, String>, dir: TrafficSignDirection, key: String): String? {
        val chain = HIERARCHY.getValue(key)
        for (k in chain) {
            tags["$k:${dir.osmSuffix}"]?.let { return it }
            tags[k]?.let { return it }
        }
        return null
    }

    private fun reverse(tags: Map<String, String>): Map<String, String> = tags.mapKeys { (key, _) ->
        key.split(':').joinToString(":") {
            when (it) {
                "forward" -> "backward"
                "backward" -> "forward"
                else -> it
            }
        }
    }

    companion object {
        private val HIERARCHY = mapOf(
            "access" to listOf("access"),
            "foot" to listOf("foot", "access"),
            "vehicle" to listOf("vehicle", "access"),
            "bicycle" to listOf("bicycle", "vehicle", "access"),
            "motor_vehicle" to listOf("motor_vehicle", "vehicle", "access"),
            "motorcar" to listOf("motorcar", "motor_vehicle", "vehicle", "access"),
            "hgv" to listOf("hgv", "motor_vehicle", "vehicle", "access"),
        )
        private val ACCESS_KEYS = HIERARCHY.keys

        private val SIGNS = listOf("DE:237", "DE:239", "DE:239,1022-10", "DE:240", "DE:241-30", "DE:250", "DE:250,1020-30", "DE:260", "DE:260,1026-36", "DE:9999", "DE:274-30")
        private val SIGNS_AND_STATES = listOf(null, "none") + SIGNS

        private val TAGS = listOf(
            mapOf("highway" to "path"),
            mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no", "traffic_sign" to "DE:240"),
            mapOf("highway" to "footway", "footway" to "sidewalk", "bicycle" to "yes", "bicycle:signed" to "yes"),
            mapOf("highway" to "cycleway", "foot" to "yes", "bicycle:backward" to "no", "traffic_sign:forward" to "DE:237"),
            mapOf("highway" to "residential", "motor_vehicle" to "yes", "hgv" to "no"),
            mapOf("highway" to "residential", "vehicle" to "no", "bicycle:forward" to "yes", "traffic_sign" to "DE:250", "traffic_sign:backward" to "none"),
            mapOf("highway" to "track", "motor_vehicle" to "agricultural", "motorcar:backward" to "destination"),
            mapOf("highway" to "service", "access" to "private"),
        )
    }
}
