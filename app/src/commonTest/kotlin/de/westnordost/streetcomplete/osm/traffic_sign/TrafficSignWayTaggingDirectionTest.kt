package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.FULL
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.PARTIAL
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.TRAFFIC_SIGN_ONLY
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection.BACKWARD
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection.FORWARD
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived.Reason
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.CONTRADICTS_SIGN
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.DIFFERS_BY_DIRECTION
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.TRAFFIC_SIGN_DIRECTION_CHANGED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficSignWayTaggingDirectionTest {

    /* --------------------------- example from the requirements ------------------------------- */

    @Test fun `shared foot and cycleway forward and footway with bicycles allowed backward`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:240", "DE:239,1022-10", tags)
        assertEquals(
            mapOf(
                // both signs are compatible with highway=footway, which DE:239 requires
                "highway" to "footway",
                "foot" to "designated",
                // forward: designated (the general value is kept), backward: allowed
                "bicycle" to "designated",
                "bicycle:backward" to "yes",
                // DE:240: not segregated, DE:239: segregated not applicable
                "segregated" to "no",
                // bicycle access is signed in both directions; no bicycle:signed:forward/backward
                "bicycle:signed" to "yes",
                "traffic_sign:forward" to "DE:240",
                "traffic_sign:backward" to "DE:239,1022-10",
            ),
            r.appliedTo(tags)
        )
        assertNull(r.trafficSign)
        assertEquals("DE:240", r.trafficSignForward)
        assertEquals("DE:239,1022-10", r.trafficSignBackward)
        assertEquals("path" to "footway", r.highwayChange)
        assertEquals(FULL, r.derivation)
        assertEquals(listOf(TrafficSignTagConflict("bicycle", "designated", CONTRADICTS_SIGN, BACKWARD)), r.resolvedConflicts)
    }

    @Test fun `example on a reversed way`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:239,1022-10", "DE:240", tags)
        assertEquals(
            mapOf(
                "highway" to "footway",
                "foot" to "designated",
                "bicycle" to "designated",
                "bicycle:forward" to "yes",
                "segregated" to "no",
                "bicycle:signed" to "yes",
                "traffic_sign:forward" to "DE:239,1022-10",
                "traffic_sign:backward" to "DE:240",
            ),
            r.appliedTo(tags)
        )
        assertEquals(listOf(TrafficSignTagConflict("bicycle", "designated", CONTRADICTS_SIGN, FORWARD)), r.resolvedConflicts)
    }

    @Test fun `reversing the way and the signs gives the reversed result`() {
        val scenarios = listOf(
            Triple("DE:240", "DE:239,1022-10", mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")),
            Triple("DE:237", "DE:240", mapOf("highway" to "path", "foot" to "designated")),
            Triple("none", "DE:250", mapOf("highway" to "residential", "motor_vehicle" to "yes")),
            Triple("DE:239,1022-10", "none", mapOf("highway" to "footway", "bicycle:forward" to "no")),
            Triple("DE:260,1020-30", "DE:250", mapOf("highway" to "service", "bicycle" to "yes", "hgv:backward" to "no")),
            Triple("DE:240", "DE:241-30", mapOf("highway" to "cycleway", "segregated" to "yes")),
            Triple("DE:9999", "DE:240", mapOf("highway" to "path")),
            Triple("DE:237", "DE:239", mapOf("highway" to "path")),
        )
        for ((f, b, tags) in scenarios) {
            val result = derive(f, b, tags).appliedTo(tags)
            val reversedResult = derive(b, f, reverse(tags)).appliedTo(reverse(tags))
            assertEquals(reverse(result), reversedResult, "$f / $b on $tags")
        }
    }

    /* ----------------------------------- same in both --------------------------------------- */

    @Test fun `same signs in both directions are tagged as traffic_sign`() {
        val tags = mapOf("highway" to "path")
        val r = derive("DE:240", "DE:240", tags)
        assertEquals("DE:240", r.trafficSign)
        assertEquals(parseDE("DE:240").deriveWayTags(tags), r)
        assertEquals(
            mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no", "traffic_sign" to "DE:240"),
            r.appliedTo(tags)
        )
    }

    @Test fun `same signs in both directions with differently written values`() {
        // DE:241 is the same as DE:241-30
        assertEquals("DE:241-30", derive("DE:241", "DE:241-30", mapOf("highway" to "path")).trafficSign)
    }

    @Test fun `same signs in both directions merge existing directional access tags`() {
        val tags = mapOf("highway" to "footway", "bicycle" to "yes", "bicycle:forward" to "designated")
        val r = derive("DE:239,1022-10", "DE:239,1022-10", tags)
        assertEquals(
            mapOf("highway" to "footway", "bicycle" to "yes", "bicycle:signed" to "yes", "traffic_sign" to "DE:239,1022-10"),
            r.appliedTo(tags)
        )
        assertEquals(listOf(TrafficSignTagConflict("bicycle", "designated", CONTRADICTS_SIGN, FORWARD)), r.resolvedConflicts)
    }

    @Test fun `same signs in both directions resolve contradicting directional access`() {
        val tags = mapOf("highway" to "path", "bicycle:backward" to "no")
        val r = derive("DE:240", "DE:240", tags)
        val result = r.appliedTo(tags)
        assertEquals("designated", result["bicycle"])
        assertNull(result["bicycle:backward"])
        assertEquals(listOf(TrafficSignTagConflict("bicycle", "no", CONTRADICTS_SIGN, BACKWARD)), r.resolvedConflicts)
    }

    @Test fun `directional traffic signs replace traffic_sign`() {
        val tags = mapOf("highway" to "path", "traffic_sign" to "DE:240")
        val r = derive("DE:240", "none", tags)
        val result = r.appliedTo(tags)
        assertNull(result["traffic_sign"])
        assertEquals("DE:240", result["traffic_sign:forward"])
        assertEquals("none", result["traffic_sign:backward"])
        assertTrue(TrafficSignTagConflict("traffic_sign", "DE:240", TRAFFIC_SIGN_DIRECTION_CHANGED) in r.resolvedConflicts)
    }

    /* ------------------------------------------ none ----------------------------------------- */

    @Test fun `none in both directions only sets traffic_sign`() {
        val tags = mapOf(
            "highway" to "path", "bicycle" to "designated", "bicycle:forward" to "no", "vehicle:backward" to "no",
            "traffic_sign:forward" to "DE:240", "traffic_sign:backward" to "DE:239",
        )
        val r = derive("none", "none", tags)
        assertEquals(
            tags - "traffic_sign:forward" - "traffic_sign:backward" + ("traffic_sign" to "none"),
            r.appliedTo(tags)
        )
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation)
        assertEquals(emptyList(), r.notDerived)
    }

    @Test fun `none in one direction does not change access tags of that direction`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle" to "yes", "bicycle" to "yes")
        val r = derive("none", "DE:250", tags)
        assertEquals(
            mapOf(
                "highway" to "residential",
                // still valid in forward direction, so kept as is
                "motor_vehicle" to "yes",
                "bicycle" to "yes",
                // the ban backward: inherited values are written explicitly
                "vehicle:backward" to "no",
                "motor_vehicle:backward" to "no",
                "bicycle:backward" to "no",
                "traffic_sign:forward" to "none",
                "traffic_sign:backward" to "DE:250",
            ),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
        // only additions, nothing that existed is changed
        assertTrue(r.changes.all { it is TrafficSignTagChange.Set && it.previousValue == null })
    }

    @Test fun `none in one direction keeps existing directional access of that direction`() {
        val tags = mapOf("highway" to "footway", "bicycle:forward" to "no")
        val r = derive("none", "DE:239,1022-10", tags)
        val result = r.appliedTo(tags)
        assertEquals("no", result["bicycle:forward"])
        assertEquals("yes", result["bicycle:backward"])
        assertNull(result["bicycle"])
    }

    @Test fun `none in one direction prevents re-classification`() {
        // highway=path -> footway would change the implied access for the direction without signs
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:239,1022-10", "none", tags)
        assertEquals(
            tags + ("traffic_sign:forward" to "DE:239,1022-10") + ("traffic_sign:backward" to "none"),
            r.appliedTo(tags)
        )
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation)
        assertEquals(listOf(TrafficSignNotDerived(Reason.HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS, "footway", FORWARD)), r.notDerived)
    }

    @Test fun `none in one direction without re-classification`() {
        val tags = mapOf("highway" to "footway")
        val r = derive("DE:239,1022-10", "none", tags)
        assertEquals(
            mapOf(
                "highway" to "footway",
                "bicycle:forward" to "yes",
                "bicycle:signed" to "yes",
                "traffic_sign:forward" to "DE:239,1022-10",
                "traffic_sign:backward" to "none",
            ),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
    }

    /* ------------------------------------ classification ------------------------------------ */

    @Test fun `conflicting classification is not derived`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:237", "DE:239", tags)
        assertEquals(tags + ("traffic_sign:forward" to "DE:237") + ("traffic_sign:backward" to "DE:239"), r.appliedTo(tags))
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation)
        assertEquals(listOf(TrafficSignNotDerived(Reason.CONFLICTING_HIGHWAY_CLASSIFICATION, "cycleway,footway")), r.notDerived)
        assertNull(r.highwayChange)
    }

    @Test fun `exclusive cycleway one way and shared foot and cycleway the other`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:237", "DE:240", tags)
        assertEquals(
            mapOf(
                "highway" to "cycleway",
                "bicycle" to "designated",
                // pedestrians only backward. foot is not inherited from anything, so the general
                // value is moved to the backward key
                "foot:backward" to "designated",
                "segregated" to "no",
                "traffic_sign:forward" to "DE:237",
                "traffic_sign:backward" to "DE:240",
            ),
            r.appliedTo(tags)
        )
        assertEquals("path" to "cycleway", r.highwayChange)
    }

    @Test fun `re-classification together with access ban is not done`() {
        val tags = mapOf("highway" to "path")
        val r = derive("DE:239", "DE:250", tags)
        assertEquals(listOf(TrafficSignNotDerived(Reason.HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS, "footway", FORWARD)), r.notDerived)
        val result = r.appliedTo(tags)
        assertEquals("path", result["highway"])
        assertEquals("no", result["vehicle:backward"])
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `shared foot and cycleway with access ban in the other direction`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated")
        val r = derive("DE:240", "DE:250", tags)
        assertEquals(
            mapOf(
                "highway" to "path",
                // forward still designated, so kept. The ban backward also applies to bicycles
                "bicycle" to "designated",
                "bicycle:backward" to "no",
                // the ban does not concern pedestrians: backward, foot is still implied by highway=path
                "foot:forward" to "designated",
                "segregated" to "no",
                "vehicle:backward" to "no",
                "traffic_sign:forward" to "DE:240",
                "traffic_sign:backward" to "DE:250",
            ),
            r.appliedTo(tags)
        )
    }

    /* ---------------------------------- non-directional keys -------------------------------- */

    @Test fun `properties that can not be tagged per direction must agree`() {
        val tags = mapOf("highway" to "path")
        val r = derive("DE:240", "DE:241-30", tags)
        val result = r.appliedTo(tags)
        assertNull(result["segregated"])
        assertEquals(listOf(TrafficSignTagConflict("segregated", "no", DIFFERS_BY_DIRECTION)), r.unresolvedConflicts)
        assertEquals(PARTIAL, r.derivation)
        // all else is derived
        assertEquals("designated", result["bicycle"])
        assertEquals("designated", result["foot"])
    }

    @Test fun `properties that can not be tagged per direction keep their existing value if they disagree`() {
        val tags = mapOf("highway" to "path", "segregated" to "yes")
        val r = derive("DE:240", "DE:241-30", tags)
        assertEquals("yes", r.appliedTo(tags)["segregated"])
        assertEquals(listOf(TrafficSignTagConflict("segregated", "yes", DIFFERS_BY_DIRECTION)), r.unresolvedConflicts)
    }

    @Test fun `no directional highway, segregated or bicycle signed keys are created`() {
        val signs = listOf("none", "DE:237", "DE:239", "DE:239,1022-10", "DE:240", "DE:241-30", "DE:250", "DE:260,1026-36", "DE:9999")
        val tagsList = listOf(
            mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated"),
            mapOf("highway" to "footway", "segregated" to "yes", "bicycle:signed" to "no"),
            mapOf("highway" to "cycleway", "bicycle" to "no"),
            mapOf("highway" to "track", "motor_vehicle" to "yes"),
        )
        val forbidden = Regex("^(highway|segregated|bicycle:signed)(:.*)?:(forward|backward)(:.*)?$|^bicycle:(forward|backward):signed$")
        for (f in signs) for (b in signs) for (tags in tagsList) {
            val result = derive(f, b, tags).appliedTo(tags)
            for (key in result.keys) assertFalse(forbidden.matches(key), "$key for $f / $b on $tags")
        }
    }

    /* --------------------------------- failed derivation ------------------------------------ */

    @Test fun `signs are stored also if nothing can be derived in one direction`() {
        val tags = mapOf("highway" to "path")
        val r = derive("DE:9999", "DE:240", tags)
        val result = r.appliedTo(tags)
        assertEquals("DE:9999", result["traffic_sign:forward"])
        assertEquals("DE:240", result["traffic_sign:backward"])
        assertEquals("designated", result["bicycle:backward"])
        assertEquals("designated", result["foot:backward"])
        assertNull(result["bicycle"])
        assertEquals(listOf(TrafficSignNotDerived(Reason.UNKNOWN_SIGN, "9999", FORWARD)), r.notDerived)
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `signs are stored also if nothing can be derived in both directions`() {
        val tags = mapOf("highway" to "residential")
        val r = derive("AT:52", "DE:274-30", tags)
        assertEquals(tags + ("traffic_sign:forward" to "AT:52") + ("traffic_sign:backward" to "DE:274-30"), r.appliedTo(tags))
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation)
        assertEquals(setOf(FORWARD, BACKWARD), r.notDerived.map { it.direction }.toSet())
    }

    @Test fun `conditional access in one direction only prevents derivation in that direction`() {
        val tags = mapOf("highway" to "residential", "bicycle:forward:conditional" to "yes @ (Mo-Fr)")
        val r = derive("DE:250", "DE:250", tags)
        assertEquals(listOf(TrafficSignNotDerived(Reason.CONDITIONAL_TAGS_PRESENT, "bicycle:conditional", FORWARD)), r.notDerived)
        val result = r.appliedTo(tags)
        assertEquals("no", result["vehicle:backward"])
        assertEquals("yes @ (Mo-Fr)", result["bicycle:forward:conditional"])
        assertEquals(PARTIAL, r.derivation)
    }

    /* ------------------------------------- no changes --------------------------------------- */

    @Test fun `already correctly tagged ways are not changed`() {
        val tags = mapOf(
            "highway" to "footway", "foot" to "designated", "bicycle" to "designated", "bicycle:backward" to "yes",
            "segregated" to "no", "bicycle:signed" to "yes",
            "traffic_sign:forward" to "DE:240", "traffic_sign:backward" to "DE:239,1022-10",
        )
        val r = derive("DE:240", "DE:239,1022-10", tags)
        assertEquals(emptyList(), r.changes)
        assertEquals(FULL, r.derivation)
    }

    @Test fun `input tags are not modified`() {
        val tags = mutableMapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "bicycle:forward" to "no")
        val copy = tags.toMap()
        derive("DE:240", "DE:239,1022-10", tags)
        assertEquals(copy, tags)
    }

    /* --------------------------------------- parsing ---------------------------------------- */

    @Test fun `parse existing signs per direction`() {
        val catalogue = TrafficSignTestData.catalogueDE
        assertEquals(null to null, parseTrafficSignWaySigns(mapOf("highway" to "path"), catalogue))
        val (f1, b1) = parseTrafficSignWaySigns(mapOf("traffic_sign" to "DE:240"), catalogue)
        assertEquals("DE:240", f1!!.toOsmValue())
        assertEquals("DE:240", b1!!.toOsmValue())
        val (f2, b2) = parseTrafficSignWaySigns(mapOf("traffic_sign:forward" to "DE:240", "traffic_sign:backward" to "none"), catalogue)
        assertEquals("DE:240", f2!!.toOsmValue())
        assertTrue(b2!!.isNone)
        val (f3, b3) = parseTrafficSignWaySigns(mapOf("traffic_sign:backward" to "DE:239"), catalogue)
        assertNull(f3)
        assertEquals("DE:239", b3!!.toOsmValue())
    }

    @Test fun `reversed signs`() {
        val signs = TrafficSignWaySigns(parseDE("DE:240"), parseDE("DE:239"))
        assertEquals(TrafficSignWaySigns(parseDE("DE:239"), parseDE("DE:240")), signs.reversed())
        assertFalse(signs.isSameInBothDirections)
        assertTrue(TrafficSignWaySigns.both(parseDE("DE:240")).isSameInBothDirections)
    }

    /* ---------------------------------------- helpers --------------------------------------- */

    private fun derive(forward: String, backward: String, tags: Map<String, String>) =
        TrafficSignWaySigns(parseDE(forward), parseDE(backward)).deriveWayTags(tags)

    private fun TrafficSignWayTagging.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.entries.associate { it.key to it.value }
    }

    /** tags of the way if it was reversed: swaps forward and backward in all keys */
    private fun reverse(tags: Map<String, String>): Map<String, String> = tags.mapKeys { (key, _) ->
        key.split(':').joinToString(":") {
            when (it) {
                "forward" -> "backward"
                "backward" -> "forward"
                else -> it
            }
        }
    }
}
