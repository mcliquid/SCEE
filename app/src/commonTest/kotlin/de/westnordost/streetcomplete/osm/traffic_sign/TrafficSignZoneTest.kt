package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** DE:274.1 (Tempo 30-Zone): maxspeed=30, zone:maxspeed=DE:30; a proposal that is not pre-selected */
class TrafficSignZoneTest {
    private val road = mapOf("highway" to "residential")

    private fun derive(tags: Map<String, String>, f: String? = "DE:274.1", b: String? = f): TrafficSignWayTagging =
        TrafficSignWaySigns(f?.let { parseDE(it) }, b?.let { parseDE(it) }).deriveWayTags(tags)

    private fun TrafficSignWayTagging.result(tags: Map<String, String>, confirmed: Map<String, String>? = null): Map<String, String> {
        val m = tags.toMutableMap()
        for (c in confirmedChanges(confirmed).applied) when (c) {
            is TrafficSignTagChange.Set -> m[c.key] = c.value
            is TrafficSignTagChange.Remove -> m.remove(c.key)
        }
        return m
    }

    @Test fun `zone 30 is proposed, not pre-selected`() {
        val t = derive(road)
        val p = t.proposals.single()
        assertEquals("zone", p.id)
        assertEquals(ProposalFamily.ZONE, p.family)
        assertFalse(p.isPreselected)
        // default: only traffic_sign
        assertEquals(road + ("traffic_sign" to "DE:274.1"), t.result(road))
        // confirmed
        assertEquals(
            road + mapOf("traffic_sign" to "DE:274.1", "maxspeed" to "30", "zone:maxspeed" to "DE:30"),
            t.result(road, mapOf(p.id to p.fingerprint)),
        )
        assertEquals(TrafficSignDerivation.FULL, t.derivation)
    }

    @Test fun `existing different maxspeed is a conflict that needs confirmation`() {
        val tags = road + ("maxspeed" to "50")
        val p = derive(tags).proposals.single()
        assertEquals(ProposalKind.CONFLICT, p.kind)
        assertTrue(derive(tags).resolvedConflicts.any { it.key == "maxspeed" && it.value == "50" })
    }

    @Test fun `existing zone tags are accepted, other types are reported and not touched`() {
        val ok = road + mapOf("maxspeed" to "30", "maxspeed:type" to "DE:zone30")
        assertTrue(derive(ok).unresolvedConflicts.isEmpty())
        val other = road + ("maxspeed:type" to "DE:urban")
        val t = derive(other)
        assertEquals("maxspeed:type", t.unresolvedConflicts.single().key)
        assertFalse(t.changes.any { it.key == "maxspeed:type" })
    }

    @Test fun `a zone sign in one direction only is not derived`() {
        val t = derive(road, "DE:274.1", null)
        assertTrue(t.proposals.isEmpty())
        assertTrue(t.notDerived.any { it.reason == Reason.ZONE_NOT_IN_BOTH_DIRECTIONS })
        assertEquals("DE:274.1", t.trafficSignForward)
        val mixed = derive(road, "DE:274.1", "DE:250")
        assertFalse(mixed.proposals.any { it.family == ProposalFamily.ZONE })
    }

    @Test fun `different values per direction do not leave maxspeed per direction`() {
        val t = derive(road + ("maxspeed:forward" to "50"))
        assertEquals("maxspeed:forward", t.unresolvedConflicts.single().key)
    }

    @Test fun `conditional speed tags and paths are not derived`() {
        assertTrue(derive(road + ("maxspeed:conditional" to "30 @ (22:00-06:00)")).notDerived.any { it.reason == Reason.CONDITIONAL_TAGS_PRESENT })
        assertTrue(derive(mapOf("highway" to "footway")).notDerived.any { it.reason == Reason.UNSUPPORTED_HIGHWAY })
    }

    @Test fun `other signs are unaffected`() {
        val t = derive(road, "DE:250")
        assertTrue(t.proposals.none { it.family == ProposalFamily.ZONE })
        assertEquals(setOf("vehicle"), t.changes.map { it.key }.toSet() - "traffic_sign")
    }
}
