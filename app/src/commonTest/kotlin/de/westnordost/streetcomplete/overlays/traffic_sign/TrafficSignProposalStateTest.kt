package de.westnordost.streetcomplete.overlays.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.ProposalKind
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.osm.traffic_sign.WaySignSelection
import de.westnordost.streetcomplete.osm.traffic_sign.applyConfirmed
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.parseDE
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.traffic_sign.AddTrafficSignType
import de.westnordost.streetcomplete.quests.traffic_sign.TrafficSignAnswer
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs3
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 3.6c: what the editor writes is what the user confirmed, in the overlay and in the quest */
class TrafficSignProposalStateTest {

    private val catalogue = TrafficSignTestData.catalogueDE
    private val F = TrafficSignDirection.FORWARD
    private val B = TrafficSignDirection.BACKWARD
    private val road = "highway" to "residential"

    /** like rememberTrafficSignWayEditorState, without compose */
    private class Editor(var tags: Map<String, String>, val catalogue: TrafficSignCatalogue) {
        val original = WaySignSelection.of(tags, catalogue)
        var forward = original.forward.encode()
        var backward = original.backward.encode()
        var choices = listOf<String>()

        val state: TrafficSignWayEditorState get() {
            val selection = WaySignSelection(DirectionSelection.decode(forward, catalogue), DirectionSelection.decode(backward, catalogue))
            return TrafficSignWayEditorState(
                catalogue, original, selection, selection.toWaySigns(original).deriveWayTags(tags),
                selection.replacesSignsWithNone(tags, catalogue), tags, choices, { choices = it },
            ) { direction, encoded ->
                when (direction) {
                    TrafficSignDirection.FORWARD -> forward = encoded
                    TrafficSignDirection.BACKWARD -> backward = encoded
                    null -> { forward = encoded; backward = encoded }
                }
            }
        }

        fun sign(value: String, direction: TrafficSignDirection? = null) = state.set(direction, DirectionSelection.Signed(parseDE(value)))

        /** what the overlay writes on OK */
        fun result(): Map<String, String> {
            val builder = StringMapChangesBuilder(tags)
            val s = state
            s.tagging.applyConfirmed(builder, s.confirmed)
            return apply(tags, builder)
        }
    }

    companion object {
        fun apply(tags: Map<String, String>, builder: StringMapChangesBuilder): Map<String, String> {
            val m = tags.toMutableMap()
            for (c in builder.create().changes) when (c) {
                is StringMapEntryAdd -> m[c.key] = c.value
                is StringMapEntryModify -> m[c.key] = c.value
                is StringMapEntryDelete -> m.remove(c.key)
            }
            return m
        }
    }

    private fun editor(vararg tags: Pair<String, String>) = Editor(mapOf(*tags), catalogue)

    @BeforeTest fun setUp() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = mockPrefs3()
    }

    /* ------------------------------------------ defaults ------------------------------------------ */

    @Test fun `new tags are pre-selected, shown and need no extra dialog`() {
        val e = editor(road)
        e.sign("DE:250")
        val s = e.state
        val p = s.proposals.single()
        assertEquals(ProposalKind.NEW, p.kind)
        assertTrue(s.isAccepted(p))
        assertEquals(mapOf("ban" to p.fingerprint), s.confirmed)
        assertTrue(s.hasChanges)
        assertFalse(s.needsConfirmation)
        assertEquals(mapOf(road, "traffic_sign" to "DE:250", "vehicle" to "no"), e.result())
    }

    @Test fun `a new tag can be declined, the sign is still saved`() {
        val e = editor(road)
        e.sign("DE:250")
        val p = e.state.proposals.single()
        e.state.setProposalAccepted(p, false)
        assertFalse(e.state.isAccepted(p))
        assertTrue(e.state.hasChanges)
        assertEquals(mapOf(road, "traffic_sign" to "DE:250"), e.result())
        // and selected again
        e.state.setProposalAccepted(p, true)
        assertEquals("no", e.result()["vehicle"])
    }

    @Test fun `all proposals declined and nothing else to save is no change`() {
        val e = editor(road, "traffic_sign" to "DE:250")
        // the sign is the one that is tagged; setting it again changes nothing
        e.sign("DE:250")
        assertFalse(e.state.hasChanges)
    }

    @Test fun `a conflict is not pre-selected and needs the dialog once selected`() {
        val e = editor(road, "vehicle" to "yes")
        e.sign("DE:250")
        val p = e.state.proposals.single()
        assertEquals(ProposalKind.CONFLICT, p.kind)
        assertFalse(e.state.isAccepted(p))
        assertFalse(e.state.needsConfirmation)
        assertEquals(mapOf(road, "vehicle" to "yes", "traffic_sign" to "DE:250"), e.result())
        e.state.setProposalAccepted(p, true)
        assertTrue(e.state.needsConfirmation)
        assertEquals(mapOf(road, "vehicle" to "no", "traffic_sign" to "DE:250"), e.result())
        assertEquals(listOf(p), e.state.appliedProposals)
    }

    @Test fun `a removal is only possible with its ban and is listed otherwise`() {
        val e = editor(road, "bicycle" to "yes")
        e.sign("DE:250")
        val s = e.state
        assertEquals(setOf("ban", "remove:bicycle"), s.proposals.map { it.id }.toSet())
        assertEquals(listOf("bicycle"), s.confirmedChanges.remainingExceptions.map { it.key })
        assertEquals(mapOf(road, "bicycle" to "yes", "traffic_sign" to "DE:250", "vehicle" to "no"), e.result())
        val removal = s.proposals.first { it.id == "remove:bicycle" }
        e.state.setProposalAccepted(removal, true)
        assertEquals(mapOf(road, "traffic_sign" to "DE:250", "vehicle" to "no"), e.result())
        assertTrue(e.state.needsConfirmation)
        assertTrue(e.state.confirmedChanges.remainingExceptions.isEmpty())
        // without the ban the removal is not done and not effective
        e.state.setProposalAccepted(s.proposals.first { it.id == "ban" }, false)
        assertEquals(mapOf(road, "bicycle" to "yes", "traffic_sign" to "DE:250"), e.result())
        assertFalse(e.state.isEffective(removal))
    }

    @Test fun `a change of the way type needs the dialog`() {
        val e = editor("highway" to "path")
        e.sign("DE:239")
        assertTrue(e.state.proposals.single().changesWayType)
        assertTrue(e.state.needsConfirmation)
        assertEquals("footway", e.result()["highway"])
        // declined: the way type stays, the dialog is not needed for the plain sign
        e.state.setProposalAccepted(e.state.proposals.single(), false)
        assertFalse(e.state.needsConfirmation)
        assertEquals("path", e.result()["highway"])
    }

    @Test fun `signs without a rule give no proposal`() {
        for (value in listOf("DE:267", "DE:274-30", "DE:250;260")) {
            val e = editor(road, "oneway" to "yes")
            e.sign(value)
            assertTrue(e.state.proposals.isEmpty(), value)
            assertTrue(e.result().filterKeys { !it.startsWith("traffic_sign") } == mapOf(road, "oneway" to "yes"), value)
        }
    }

    /* ---------------------------- choices are only valid for what was shown ---------------------------- */

    @Test fun `a choice does not apply to a changed proposal`() {
        val e = editor(road, "vehicle" to "yes")
        e.sign("DE:250")
        e.state.setProposalAccepted(e.state.proposals.single(), true)
        assertEquals("no", e.result()["vehicle"])
        // the tag changes while the form is open: the conflict is a different one
        e.tags = mapOf(road, "vehicle" to "destination")
        val s = e.state
        assertFalse(s.isAccepted(s.proposals.single()))
        assertEquals("destination", e.result()["vehicle"])
        // and a conflict that did not exist when a new tag was pre-selected is not accepted
        val f = editor(road)
        f.sign("DE:250")
        assertTrue(f.state.isAccepted(f.state.proposals.single()))
        f.tags = mapOf(road, "vehicle" to "yes")
        assertFalse(f.state.isAccepted(f.state.proposals.single()))
        assertEquals("yes", f.result()["vehicle"])
    }

    /* ------------------------------------- quest = overlay ------------------------------------- */

    private val situations = listOf(
        arrayOf(road) to "DE:250",
        arrayOf(road, "vehicle" to "yes") to "DE:250",
        arrayOf(road, "bicycle" to "yes", "motor_vehicle" to "yes") to "DE:250",
        arrayOf(road, "access" to "yes") to "DE:260",
        arrayOf("highway" to "service") to "DE:250,1020-30",
        arrayOf("highway" to "track", "motor_vehicle" to "yes") to "DE:260,1026-36",
        arrayOf("highway" to "path") to "DE:239",
        arrayOf("highway" to "path", "bicycle" to "no") to "DE:240",
        arrayOf("highway" to "path") to "DE:237",
        arrayOf(road, "oneway" to "yes") to "DE:267",
    )

    private fun questResult(tags: Map<String, String>, answer: TrafficSignAnswer) =
        AddTrafficSignType { null }.answerAppliedTo(answer, tags).let { changes ->
            val m = tags.toMutableMap()
            for (c in changes) when (c) {
                is StringMapEntryAdd -> m[c.key] = c.value
                is StringMapEntryModify -> m[c.key] = c.value
                is StringMapEntryDelete -> m.remove(c.key)
            }
            m
        }

    @Test fun `the quest writes what the overlay writes for the same confirmation`() {
        for ((tags, value) in situations) {
            val e = Editor(mapOf(*tags), catalogue)
            e.sign(value)
            val answer = TrafficSignAnswer(e.state.waySigns, e.state.confirmed)
            assertEquals(e.result(), questResult(e.tags, answer), "${e.tags} $value")
            // also with every conflict and removal selected
            for (p in e.state.proposals) e.state.setProposalAccepted(p, true)
            val all = TrafficSignAnswer(e.state.waySigns, e.state.confirmed)
            assertEquals(e.result(), questResult(e.tags, all), "${e.tags} $value (all)")
            // declining everything
            for (p in e.state.proposals) e.state.setProposalAccepted(p, false)
            val none = TrafficSignAnswer(e.state.waySigns, e.state.confirmed)
            assertEquals(e.result(), questResult(e.tags, none), "${e.tags} $value (none)")
            assertTrue(e.result().filterKeys { !it.startsWith("traffic_sign") } == e.tags.filterKeys { !it.startsWith("traffic_sign") }, "${e.tags} $value (none)")
        }
    }

    @Test fun `the quest does not write a conflict that appeared after the confirmation`() {
        val shown = editor(road)
        shown.sign("DE:250")
        val answer = TrafficSignAnswer(shown.state.waySigns, shown.state.confirmed)
        // somebody else tagged vehicle=yes in the meantime
        val now = mapOf(road, "vehicle" to "yes")
        assertEquals(mapOf(road, "vehicle" to "yes", "traffic_sign" to "DE:250"), questResult(now, answer))
        // the same for a value that changed under a confirmed conflict
        val conflict = editor(road, "vehicle" to "yes")
        conflict.sign("DE:250")
        conflict.state.setProposalAccepted(conflict.state.proposals.single(), true)
        val confirmed = TrafficSignAnswer(conflict.state.waySigns, conflict.state.confirmed)
        assertEquals("no", questResult(mapOf(road, "vehicle" to "yes"), confirmed)["vehicle"])
        assertEquals("private", questResult(mapOf(road, "vehicle" to "private"), confirmed)["vehicle"])
    }

    @Test fun `the default answer of the quest only adds tags`() {
        val tags = mapOf(road, "vehicle" to "yes", "bicycle" to "yes")
        val changes = AddTrafficSignType { null }.answerAppliedTo(TrafficSignAnswer(editor().state.let { catalogueSigns("DE:250") }), tags)
        assertTrue(changes.none { it is StringMapEntryDelete }, changes.toString())
        assertTrue(changes.none { it is StringMapEntryModify && it.key != "traffic_sign" }, changes.toString())
    }

    private fun catalogueSigns(v: String) = de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns.both(parseDE(v))

    /* --------------------------------------- idempotence --------------------------------------- */

    @Test fun `saving the same selection again changes nothing`() {
        for ((tags, value) in situations) {
            val e = Editor(mapOf(*tags), catalogue)
            e.sign(value)
            for (p in e.state.proposals) e.state.setProposalAccepted(p, true)
            val first = e.result()
            val again = Editor(first, catalogue)
            again.sign(value)
            assertFalse(again.state.hasChanges, "$tags $value")
            assertTrue(again.state.proposals.isEmpty(), "$tags $value")
        }
    }

    @Test fun `opening and confirming without a change writes nothing`() {
        for ((tags, _) in situations) {
            val e = Editor(mapOf(*tags), catalogue)
            assertFalse(e.state.isEdited)
            assertFalse(e.state.hasChanges)
            assertEquals(e.tags, e.result())
        }
    }

    @Test fun `a direction that is not edited keeps its tags and access`() {
        val e = editor(road, "traffic_sign:backward" to "DE:274-30", "bicycle:backward" to "yes")
        e.sign("DE:250", F)
        val r = e.result()
        assertEquals("DE:274-30", r["traffic_sign:backward"])
        assertEquals("yes", r["bicycle:backward"])
        assertEquals("no", r["vehicle:forward"])
        assertNull(r["vehicle:backward"])
        assertNotNull(e.state.proposals.single { it.direction == F })
    }
}
