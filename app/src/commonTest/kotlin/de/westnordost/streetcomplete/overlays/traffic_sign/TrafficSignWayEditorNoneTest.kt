package de.westnordost.streetcomplete.overlays.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignValue
import de.westnordost.streetcomplete.osm.traffic_sign.WaySignSelection
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.parseDE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The direct "no signs" button of the direction rows, and the icon of the quest. The button calls
 *  [TrafficSignWayEditorState.set] with [DirectionSelection.None], like the direction dialog does,
 *  nothing is saved before the user presses OK (which asks for the confirmations). */
class TrafficSignWayEditorNoneTest {

    private val catalogue = TrafficSignTestData.catalogueDE
    private val F = TrafficSignDirection.FORWARD
    private val B = TrafficSignDirection.BACKWARD

    /** like rememberTrafficSignWayEditorState, without compose */
    private class Editor(val tags: Map<String, String>, val catalogue: de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue) {
        val original = WaySignSelection.of(tags, catalogue)
        var forward = original.forward.encode()
        var backward = original.backward.encode()

        val state: TrafficSignWayEditorState get() {
            val selection = WaySignSelection(
                DirectionSelection.decode(forward, catalogue),
                DirectionSelection.decode(backward, catalogue),
            )
            return TrafficSignWayEditorState(
                catalogue, original, selection, selection.toWaySigns(original).deriveWayTags(tags),
                selection.replacesSignsWithNone(tags, catalogue), tags,
            ) { direction, encoded ->
                when (direction) {
                    TrafficSignDirection.FORWARD -> forward = encoded
                    TrafficSignDirection.BACKWARD -> backward = encoded
                    null -> { forward = encoded; backward = encoded }
                }
            }
        }

        fun pressNone(direction: TrafficSignDirection) = state.set(direction, DirectionSelection.None)

        fun result(): Map<String, String> {
            val builder = StringMapChangesBuilder(tags)
            state.tagging.applyTo(builder)
            val m = tags.toMutableMap()
            for (c in builder.create().changes) {
                when (c) {
                    is StringMapEntryAdd -> m[c.key] = c.value
                    is StringMapEntryModify -> m[c.key] = c.value
                    is StringMapEntryDelete -> m.remove(c.key)
                }
            }
            return m
        }
    }

    private fun editor(vararg tags: Pair<String, String>) = Editor(mapOf(*tags), catalogue)
    private val road = "highway" to "track"

    @Test fun `none for one direction only changes that direction`() {
        val e = editor(road, "traffic_sign:backward" to "DE:250")
        e.pressNone(F)
        val s = e.state
        assertEquals(DirectionSelection.None, s.selection.forward)
        assertEquals(e.original.backward, s.selection.backward)
        assertEquals(TrafficSignValue.NONE, s.waySigns.forward)
        assertNull(s.waySigns.backward)
        assertEquals(mapOf(road, "traffic_sign:forward" to "none", "traffic_sign:backward" to "DE:250"), e.result())
        assertTrue(s.isEdited)
        assertTrue(s.hasChanges)
    }

    @Test fun `the other direction keeps its original value, also a general or unreadable one`() {
        for (raw in listOf("DE:274[30]", "de:250", "yes", "AT:52", "DE:250;DE:274-30")) {
            val e = editor(road, "traffic_sign" to raw)
            e.pressNone(B)
            val r = e.result()
            assertEquals(raw, r["traffic_sign:forward"], raw)
            assertEquals("none", r["traffic_sign:backward"], raw)
            assertNull(r["traffic_sign"], raw)
        }
    }

    @Test fun `both directions can be set one after the other`() {
        val e = editor(road)
        e.pressNone(F)
        e.pressNone(B)
        assertEquals(mapOf(road, "traffic_sign" to "none"), e.result())
    }

    @Test fun `existing none and no are shown as none and do not change`() {
        for (existing in listOf("none", "no")) {
            val e = editor(road, "traffic_sign" to existing)
            assertEquals(DirectionSelection.None, e.state.selection.forward, existing)
            assertEquals(DirectionSelection.None, e.state.selection.backward, existing)
            assertFalse(e.state.isEdited)
            e.pressNone(F)
            assertFalse(e.state.hasChanges, existing)
            assertEquals(e.tags, e.result(), existing)
        }
    }

    @Test fun `pressing none is idempotent`() {
        val e = editor(road, "traffic_sign:forward" to "DE:250")
        e.pressNone(B)
        val once = e.result()
        e.pressNone(B)
        assertEquals(once, e.result())
        // applying the result again changes nothing
        val again = Editor(once, catalogue)
        again.pressNone(B)
        assertFalse(again.state.hasChanges)
    }

    /* ----------------------------------- confirmations stay in place ----------------------------------- */

    @Test fun `access warning for the direct none path`() {
        val tags = arrayOf("highway" to "residential", "motor_vehicle:forward" to "destination", "bicycle" to "designated")
        val e = editor(*tags)
        e.pressNone(F)
        val w = assertNotNull(e.state.noneAccessWarning)
        assertEquals(listOf(F), w.directions)
        assertEquals(mapOf("bicycle" to "designated", "motor_vehicle:forward" to "destination"), w.tags)
        assertTrue(e.state.needsConfirmation)
        // for the other direction the forward only tag is not mentioned
        val b = editor(*tags)
        b.pressNone(B)
        assertEquals(mapOf("bicycle" to "designated"), b.state.noneAccessWarning!!.tags)
        // nothing was written by pressing the button
        assertEquals(mapOf(*tags), e.tags)
    }

    @Test fun `no access warning without relevant tags, direct none is then saved without extra dialog`() {
        val e = editor(road, "access" to "yes", "foot" to "yes")
        e.pressNone(F)
        assertNull(e.state.noneAccessWarning)
        assertFalse(e.state.needsConfirmation)
        assertTrue(e.state.hasChanges)
    }

    @Test fun `replacing existing signs by none still asks`() {
        val e = editor(road, "traffic_sign" to "DE:250")
        e.pressNone(F)
        assertTrue(e.state.needsNoneConfirmation)
        assertTrue(e.state.needsConfirmation)
    }

    @Test fun `replacing an unreadable value by none still shows the old value`() {
        val e = editor(road, "traffic_sign:forward" to "DE:250;AT:52")
        e.pressNone(F)
        assertEquals(listOf(F to "DE:250;AT:52"), e.state.replacedUnreadable)
        assertTrue(e.state.needsConfirmation)
    }

    @Test fun `existing none on one direction does not warn when the other one gets none`() {
        val e = editor("highway" to "residential", "access" to "private", "traffic_sign:forward" to "none")
        e.pressNone(B)
        assertEquals(listOf(B), e.state.noneAccessWarning!!.directions)
    }

    @Test fun `the button and the dialog give the same selection`() {
        val viaButton = editor(road, "traffic_sign" to "DE:250")
        viaButton.pressNone(F)
        // the dialog confirms its selection with state.set(direction, newSelection)
        val viaDialog = editor(road, "traffic_sign" to "DE:250")
        viaDialog.state.set(F, DirectionSelection.None)
        assertEquals(viaDialog.forward, viaButton.forward)
        assertEquals(viaDialog.backward, viaButton.backward)
        assertEquals(viaDialog.result(), viaButton.result())
    }

    /* ----------------------------------- preview texts ----------------------------------- */

    @Test fun `the preview only has content if there are consequences to tell`() {
        val plain = mapOf("highway" to "residential")
        assertFalse(TrafficSignWaySigns2.both("DE:250").deriveWayTags(plain).toPreviewModel().hasContent)
        val path = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val model = TrafficSignWaySigns2.of("DE:240", "DE:239,1022-10").deriveWayTags(path).toPreviewModel()
        assertTrue(model.hasContent)
        assertNotNull(model.highwayChange)
    }

    private object TrafficSignWaySigns2 {
        fun both(v: String) = de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns.both(parseDE(v))
        fun of(f: String, b: String) = de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns(parseDE(f), parseDE(b))
    }

    /* ------------------------------------------- icon ------------------------------------------- */

    @Test fun `the quest icon drawable and its svg source are the same drawing`() {
        val svg = TrafficSignTestData.readText("../res/graphics/quest/traffic_sign.svg")
        val xml = TrafficSignTestData.readText("src/commonMain/composeResources/drawable/quest_traffic_sign.xml")
        val svgPaths = Regex("<path d=\"([^\"]+)\" fill=\"([^\"]+)\"").findAll(svg).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val xmlPaths = Regex("android:pathData=\"([^\"]+)\"\\s+android:fillColor=\"([^\"]+)\"").findAll(xml).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertEquals(4, svgPaths.size)
        assertEquals(svgPaths, xmlPaths)
        assertTrue(svg.contains("viewBox=\"0 0 128 128\""))
        assertTrue(xml.contains("android:viewportWidth=\"128\"") && xml.contains("android:viewportHeight=\"128\""))
        // disc, pole, sign (red, white); no black arrow
        assertEquals(listOf("#fd5", "#5f6368", "#c1121c", "#ffffff"), svgPaths.map { it.second })
        // sign 250 fits completely into the disc and the pole starts below the sign: sign centre (64,52) r 40, pole 88..116
        assertTrue(svgPaths[2].first.startsWith("M64,52m-40,0a40,40"))
        assertTrue(svgPaths[1].first.startsWith("M59,88h10v28"))
    }
}
