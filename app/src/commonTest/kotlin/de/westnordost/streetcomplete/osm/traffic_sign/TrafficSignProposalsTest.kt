package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 3.6b: the tags that follow from the signs are proposals of groups: new tags are pre-selected,
 *  conflicts and removals are not, nothing is written without a matching confirmation */
class TrafficSignProposalsTest {

    private val F = TrafficSignDirection.FORWARD
    private val B = TrafficSignDirection.BACKWARD
    private val NONE = TrafficSignValue.NONE

    private fun signs(forward: String?, backward: String? = forward) = TrafficSignWaySigns(
        forward?.let { if (it == "none") NONE else parseDE(it) }, backward?.let { if (it == "none") NONE else parseDE(it) }
    )

    private fun apply(tags: Map<String, String>, tagging: TrafficSignWayTagging, confirmed: Map<String, String>?): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        tagging.applyConfirmed(builder, confirmed)
        val m = tags.toMutableMap()
        for (c in builder.create().changes) when (c) {
            is StringMapEntryAdd -> m[c.key] = c.value
            is StringMapEntryModify -> m[c.key] = c.value
            is StringMapEntryDelete -> m.remove(c.key)
        }
        return m
    }

    private fun confirmAll(t: TrafficSignWayTagging) = t.proposals.associate { it.id to it.fingerprint }
    private fun tagsOf(vararg t: Pair<String, String>) = mapOf(*t)
    private val road = "highway" to "residential"

    /* ----------------------------------- kinds and defaults ----------------------------------- */

    @Test fun `a missing tag is a new proposal and pre-selected`() {
        val tags = tagsOf(road)
        val t = signs("DE:250").deriveWayTags(tags)
        val p = t.proposals.single()
        assertEquals("ban", p.id)
        assertEquals(ProposalKind.NEW, p.kind)
        assertEquals(listOf(TrafficSignTagChange.Set("vehicle", "no", null)), p.changes)
        assertTrue(p.isPreselected)
        assertEquals(tagsOf(road, "traffic_sign" to "DE:250", "vehicle" to "no"), apply(tags, t, null))
        // the sign itself is always written, also if all proposals are declined
        assertEquals(tagsOf(road, "traffic_sign" to "DE:250"), apply(tags, t, emptyMap()))
    }

    @Test fun `an identical tag is no proposal`() {
        val tags = tagsOf(road, "vehicle" to "no")
        val t = signs("DE:250").deriveWayTags(tags)
        assertTrue(t.proposals.isEmpty())
        assertEquals(tagsOf(road, "vehicle" to "no", "traffic_sign" to "DE:250"), apply(tags, t, null))
    }

    @Test fun `a contradicting value is a conflict and kept by default`() {
        val tags = tagsOf(road, "vehicle" to "yes")
        val t = signs("DE:250").deriveWayTags(tags)
        val p = t.proposals.single()
        assertEquals(ProposalKind.CONFLICT, p.kind)
        assertFalse(p.isPreselected)
        assertEquals(listOf(TrafficSignTagChange.Set("vehicle", "no", "yes")), p.changes)
        // default: the existing value stays
        assertEquals(tagsOf(road, "vehicle" to "yes", "traffic_sign" to "DE:250"), apply(tags, t, null))
        // explicit confirmation
        assertEquals(tagsOf(road, "vehicle" to "no", "traffic_sign" to "DE:250"), apply(tags, t, confirmAll(t)))
    }

    @Test fun `more specific permissive tags stay until the user confirms their removal`() {
        val tags = tagsOf(road, "bicycle" to "yes", "motor_vehicle" to "yes")
        val t = signs("DE:250").deriveWayTags(tags)
        assertEquals(listOf("ban", "remove:bicycle", "remove:motor_vehicle"), t.proposals.map { it.id }.sorted().let { ids -> listOf("ban") + ids.filter { it != "ban" } })
        val removal = t.proposals.first { it.id == "remove:bicycle" }
        assertEquals(ProposalKind.REMOVAL, removal.kind)
        assertEquals("ban", removal.dependsOn)
        // default: only the ban, the exceptions are shown as remaining
        val default = t.confirmedChanges(null)
        assertEquals(tagsOf(road, "bicycle" to "yes", "motor_vehicle" to "yes", "traffic_sign" to "DE:250", "vehicle" to "no"), apply(tags, t, null))
        assertEquals(setOf("bicycle", "motor_vehicle"), default.remainingExceptions.map { it.key }.toSet())
        // confirm the removal of one of them
        val confirmed = mapOf("ban" to t.proposals.first { it.id == "ban" }.fingerprint, "remove:bicycle" to removal.fingerprint)
        val result = apply(tags, t, confirmed)
        assertEquals(tagsOf(road, "motor_vehicle" to "yes", "traffic_sign" to "DE:250", "vehicle" to "no"), result)
        assertEquals(listOf("motor_vehicle"), t.confirmedChanges(confirmed).remainingExceptions.map { it.key })
    }

    @Test fun `a removal without the ban it belongs to is not done`() {
        val tags = tagsOf(road, "bicycle" to "yes")
        val t = signs("DE:250").deriveWayTags(tags)
        val removal = t.proposals.first { it.id == "remove:bicycle" }
        // only the removal confirmed, the ban declined
        val result = apply(tags, t, mapOf("remove:bicycle" to removal.fingerprint))
        assertEquals(tagsOf(road, "bicycle" to "yes", "traffic_sign" to "DE:250"), result)
        assertTrue(t.confirmedChanges(mapOf("remove:bicycle" to removal.fingerprint)).remainingExceptions.isEmpty())
    }

    @Test fun `nothing is derived if the meaning is unclear`() {
        // 267: no catalogue rule, several main signs, unknown sign, conditional tags
        for ((s, tags) in listOf(
            signs("DE:267") to tagsOf(road),
            signs("DE:267") to tagsOf(road, "oneway" to "yes"),
            signs("DE:250;260") to tagsOf(road),
            signs("DE:9999") to tagsOf(road),
            signs("DE:250") to tagsOf(road, "motor_vehicle:conditional" to "no @ (Su)"),
            signs("DE:250") to tagsOf(road, "vehicle:lanes" to "no|yes"),
            signs("DE:274-30") to tagsOf(road),
            signs("DE:250,1020-12") to tagsOf(road),
        )) {
            val t = s.deriveWayTags(tags)
            assertTrue(t.proposals.isEmpty(), "$s $tags")
            assertTrue(t.changes.all { it.key.startsWith("traffic_sign") }, "$s $tags: ${t.changes}")
        }
    }

    @Test fun `none never proposes access tags`() {
        val tags = tagsOf(road, "access" to "private")
        val t = signs("none").deriveWayTags(tags)
        assertTrue(t.proposals.isEmpty())
        assertEquals(tagsOf(road, "access" to "private", "traffic_sign" to "none"), apply(tags, t, null))
    }

    /* ---------------------------------------- path signs ---------------------------------------- */

    @Test fun `a path sign is one group, also the change of the way type`() {
        val tags = tagsOf("highway" to "path")
        val t = signs("DE:239").deriveWayTags(tags)
        val p = t.proposals.single()
        assertEquals("path", p.id)
        assertEquals(ProposalKind.NEW, p.kind)
        assertTrue(p.changesWayType)
        assertEquals("footway", apply(tags, t, null)["highway"])
        // declined: no half change
        assertEquals(tagsOf("highway" to "path", "traffic_sign" to "DE:239"), apply(tags, t, emptyMap()))
    }

    @Test fun `a path sign that contradicts existing access is a conflict`() {
        val tags = tagsOf("highway" to "path", "bicycle" to "no")
        val t = signs("DE:240").deriveWayTags(tags)
        val p = t.proposals.single()
        assertEquals(ProposalKind.CONFLICT, p.kind)
        assertEquals(tagsOf("highway" to "path", "bicycle" to "no", "traffic_sign" to "DE:240"), apply(tags, t, null))
        assertEquals("designated", apply(tags, t, confirmAll(t))["bicycle"])
    }

    /* ------------------------------------------ directions ------------------------------------------ */

    @Test fun `a sign for one direction proposes a tag for that direction only`() {
        val tags = tagsOf(road)
        val t = signs("DE:250", null).deriveWayTags(tags)
        val p = t.proposals.single()
        assertEquals("ban:forward", p.id)
        assertEquals(F, p.direction)
        assertEquals(tagsOf(road, "traffic_sign:forward" to "DE:250", "vehicle:forward" to "no"), apply(tags, t, null))
    }

    @Test fun `different signs per direction are separate groups`() {
        val t = signs("DE:250", "DE:260").deriveWayTags(tagsOf(road))
        assertEquals(setOf("ban:forward", "ban:backward"), t.proposals.map { it.id }.toSet())
        val result = apply(tagsOf(road), t, mapOf("ban:backward" to t.proposals.first { it.id == "ban:backward" }.fingerprint))
        assertEquals("DE:260", result["traffic_sign:backward"])
        assertEquals("DE:250", result["traffic_sign:forward"])
        assertNull(result["vehicle:forward"])
        assertNotNull(result.keys.firstOrNull { it.startsWith("motor_vehicle") })
    }

    @Test fun `signs in both directions with the same result give one general group`() {
        val t = signs("DE:260").deriveWayTags(tagsOf(road))
        assertEquals(listOf("ban"), t.proposals.map { it.id })
        assertEquals(tagsOf(road, "traffic_sign" to "DE:260", "motor_vehicle" to "no"), apply(tagsOf(road), t, null))
    }

    @Test fun `a unsurveyed direction keeps its tags`() {
        val tags = tagsOf(road, "traffic_sign:backward" to "DE:274-30", "bicycle:backward" to "yes")
        val t = signs("DE:250", null).deriveWayTags(tags)
        val result = apply(tags, t, null)
        assertEquals("DE:274-30", result["traffic_sign:backward"])
        assertEquals("yes", result["bicycle:backward"])
    }

    /* -------------------------------- confirmation must match what was shown -------------------------------- */

    @Test fun `a confirmation is only valid for the proposal that was shown`() {
        val shown = signs("DE:250").deriveWayTags(tagsOf(road, "vehicle" to "yes"))
        val confirmed = confirmAll(shown)
        // later the tags are different: the conflict is another one now
        val tagsNow = tagsOf(road, "vehicle" to "destination")
        val now = signs("DE:250").deriveWayTags(tagsNow)
        assertEquals(tagsOf(road, "vehicle" to "destination", "traffic_sign" to "DE:250"), apply(tagsNow, now, confirmed))
        // a conflict appeared after a new tag had been proposed
        val newShown = signs("DE:250").deriveWayTags(tagsOf(road))
        val confirmedNew = confirmAll(newShown)
        val tagsLater = tagsOf(road, "vehicle" to "yes")
        val later = signs("DE:250").deriveWayTags(tagsLater)
        assertEquals(tagsOf(road, "vehicle" to "yes", "traffic_sign" to "DE:250"), apply(tagsLater, later, confirmedNew))
        // the same state: applied
        assertEquals("no", apply(tagsLater, later, confirmAll(later))["vehicle"])
    }

    @Test fun `the proposal ids are stable, the fingerprint names old and new values`() {
        val t = signs("DE:250").deriveWayTags(tagsOf(road, "vehicle" to "yes"))
        assertEquals("S vehicle=yes>no", t.proposals.single().fingerprint)
        assertEquals("S vehicle=null>no", signs("DE:250").deriveWayTags(tagsOf(road)).proposals.single().fingerprint)
    }

    /* ------------------------------------------ consistency ------------------------------------------ */

    private val situations: List<Pair<TrafficSignWaySigns, Map<String, String>>> = listOf(
        signs("DE:250") to tagsOf(road),
        signs("DE:250") to tagsOf(road, "vehicle" to "yes", "bicycle" to "yes", "motor_vehicle" to "yes"),
        signs("DE:250") to tagsOf(road, "access" to "yes", "foot" to "yes"),
        signs("DE:250", null) to tagsOf(road, "bicycle:forward" to "yes", "motor_vehicle" to "yes"),
        signs("DE:250", "DE:260") to tagsOf(road, "motor_vehicle" to "yes", "bicycle" to "yes"),
        signs("DE:250,1020-30") to tagsOf(road, "vehicle" to "yes", "bicycle" to "yes"),
        signs("DE:260,1026-36") to tagsOf("highway" to "track", "motor_vehicle" to "yes"),
        signs("DE:239") to tagsOf("highway" to "path", "bicycle" to "no"),
        signs("DE:240") to tagsOf("highway" to "path", "foot" to "yes", "bicycle" to "yes"),
        signs("DE:241-30") to tagsOf("highway" to "footway", "segregated" to "no"),
        signs("DE:237") to tagsOf("highway" to "path", "bicycle" to "yes"),
        signs("DE:240", "DE:250") to tagsOf("highway" to "path"),
    )

    @Test fun `every subset of the proposals gives a consistent result`() {
        for ((s, tags) in situations) {
            val t = s.deriveWayTags(tags)
            val ids = t.proposals.map { it.id }
            assertTrue(ids.size <= 10)
            for (mask in 0 until (1 shl ids.size)) {
                val chosen = ids.filterIndexed { i, _ -> mask and (1 shl i) != 0 }
                val confirmed = t.proposals.filter { it.id in chosen }.associate { it.id to it.fingerprint }
                val c = t.confirmedChanges(confirmed)
                val result = apply(tags, t, confirmed)
                val label = "$tags $s chosen=$chosen"
                // the signs are always written
                for (change in t.signChanges) {
                    if (change is TrafficSignTagChange.Set) assertEquals(change.value, result[change.key], label)
                }
                val appliedIds = t.proposals.filter { p -> p.changes.all { it in c.applied } }.map { it.id }
                for (p in t.proposals) {
                    val applied = p.id in appliedIds
                    // all or nothing of a group
                    for (change in p.changes) {
                        when (change) {
                            is TrafficSignTagChange.Set -> assertEquals(if (applied) change.value else tags[change.key], result[change.key], "$label ${p.id}")
                            is TrafficSignTagChange.Remove -> assertEquals(if (applied) null else tags[change.key], result[change.key], "$label ${p.id}")
                        }
                    }
                    // no removal without the ban, no group that was not confirmed
                    if (applied) {
                        assertTrue(p.id in chosen, label)
                        p.dependsOn?.let { assertTrue(it in appliedIds, "$label ${p.id} needs $it") }
                    }
                }
                // what a ban that is done does not lift is listed
                for (p in t.proposals.filter { it.kind == ProposalKind.REMOVAL && it.family == ProposalFamily.BAN }) {
                    val listed = c.remainingExceptions.any { r -> p.changes.any { it.key == r.key } }
                    val banDone = p.dependsOn == null || p.dependsOn in appliedIds
                    assertEquals(banDone && p.id !in appliedIds, listed, "$label ${p.id}")
                }
            }
        }
    }

    @Test fun `confirming everything gives the complete derivation of deriveWayTags`() {
        for ((s, tags) in situations) {
            val t = s.deriveWayTags(tags)
            val builder = StringMapChangesBuilder(tags)
            t.applyTo(builder)
            val full = tags.toMutableMap().also { m ->
                for (c in builder.create().changes) when (c) {
                    is StringMapEntryAdd -> m[c.key] = c.value
                    is StringMapEntryModify -> m[c.key] = c.value
                    is StringMapEntryDelete -> m.remove(c.key)
                }
            }
            assertEquals(full, apply(tags, t, confirmAll(t)), "$tags $s")
        }
    }

    @Test fun `applying twice changes nothing and reversed ways mirror the result`() {
        for ((s, tags) in situations) {
            val t = s.deriveWayTags(tags)
            val once = apply(tags, t, confirmAll(t))
            val again = s.deriveWayTags(once)
            assertTrue(again.changes.isEmpty(), "$tags $s: ${again.changes}")
            assertTrue(again.proposals.isEmpty(), "$tags $s")
            // the way reversed: forward and backward swapped
            val rev = s.reversed().deriveWayTags(tags.swapDirections())
            assertEquals(once.swapDirections(), apply(tags.swapDirections(), rev, confirmAll(rev)), "$tags $s")
        }
    }

    @Test fun `quest and overlay write the same for the same confirmation`() {
        // both use confirmedChanges with the same arguments; the quest rebuilds the tagging from the tags at the time of saving
        for ((s, tags) in situations) {
            val shown = s.deriveWayTags(tags)
            val confirmed = shown.proposals.filter { it.isPreselected }.associate { it.id to it.fingerprint }
            val atSaving = s.deriveWayTags(tags)
            assertEquals(apply(tags, shown, confirmed), apply(tags, atSaving, confirmed), "$tags $s")
            assertEquals(apply(tags, shown, null), apply(tags, shown, confirmed), "$tags $s")
        }
    }

    private fun Map<String, String>.swapDirections() = mapKeys { (k, _) ->
        k.replace(":forward", ":@").replace(":backward", ":forward").replace(":@", ":backward")
    }
}
