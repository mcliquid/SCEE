package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.overlays.traffic_sign.trafficSignWayStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Regression tests for the follow-up fixes (yes placeholder, orphan additional signs, empty
 *  directional values, equivalent spellings, unreadable values) */
class TrafficSignFollowUpTest {

    private val catalogue = TrafficSignTestData.catalogueDE
    private val road = "highway" to "residential"

    private fun part(osmValuePart: String) = TrafficSignPart.of(catalogue.getByOsmValuePart(osmValuePart)!!)

    private fun apply(tags: Map<String, String>, tagging: TrafficSignWayTagging): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        tagging.applyTo(builder)
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

    /** what the editor does: selection of the user -> engine -> new tags */
    private fun edit(tags: Map<String, String>, vararg edits: Pair<TrafficSignDirection, DirectionSelection>): Map<String, String> {
        val original = WaySignSelection.of(tags, catalogue)
        val selection = edits.fold(original) { s, (d, sel) -> s.with(d, sel) }
        return apply(tags, selection.toWaySigns(original).deriveWayTags(tags))
    }

    private fun signed(v: String) = DirectionSelection.Signed(parseDE(v))
    private val F = TrafficSignDirection.FORWARD
    private val B = TrafficSignDirection.BACKWARD

    /* ------------------------------------------ F1 ------------------------------------------ */

    @Test fun `adding the first main sign replaces the yes placeholder`() {
        val groups = parseDE("yes").groups
        assertEquals("DE:267", groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:274-30", parseDE("YES").groups.withMainSign(part("274-30")).toValueOrNull("DE")!!.toOsmValue())
    }

    @Test fun `other named values are kept when a sign is added`() {
        for (named in listOf("city_limit", "maxspeed", "variable", "hazard", "signals", "destination")) {
            val result = parseDE(named).groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue()
            assertEquals("$named;DE:267", result)
        }
    }

    @Test fun `placeholder is a group of its own, other groups are untouched`() {
        val groups = parseDE("DE:250;yes").groups
        assertEquals(2, groups.size)
        assertEquals("DE:250;267", groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
        // the other groups stay as they are
        assertEquals("DE:250,1020-30;267", parseDE("DE:250,1020-30;yes").groups.withMainSign(part("267"))
            .toValueOrNull("DE")!!.toOsmValue())
    }

    @Test fun `additional sign for a yes placeholder leaves the sign without main sign`() {
        val withModifier = parseDE("yes").groups.withModifier(0, part("1022-10"))
        assertEquals("DE:1022-10", withModifier.toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:267,1022-10", withModifier.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
    }

    @Test fun `editor path - yes in both directions is replaced by the new sign`() {
        val tags = mapOf(road, "traffic_sign" to "yes")
        val groups = (WaySignSelection.of(tags, catalogue).forward as DirectionSelection.Signed).value.groups
            .withMainSign(part("274-30"))
        val sel = DirectionSelection.Signed(groups.toValueOrNull("DE")!!)
        val result = edit(tags, F to sel, B to sel)
        assertEquals(mapOf(road, "traffic_sign" to "DE:274-30"), result)
    }

    @Test fun `editor path - yes in one direction replaced, the other one keeps yes`() {
        val tags = mapOf(road, "traffic_sign" to "yes")
        val groups = parseDE("yes").groups.withMainSign(part("274-30"))
        val result = edit(tags, F to DirectionSelection.Signed(groups.toValueOrNull("DE")!!))
        assertEquals("DE:274-30", result["traffic_sign:forward"])
        assertEquals("yes", result["traffic_sign:backward"])
        assertNull(result["traffic_sign"])
        assertFalse(result.values.any { it.startsWith("yes;") || it.contains(";yes") })
    }

    @Test fun `unchanged direction keeps its original yes`() {
        val tags = mapOf(road, "traffic_sign:forward" to "yes", "traffic_sign:backward" to "DE:239")
        assertEquals(tags, edit(tags))
        val result = edit(tags, B to signed("DE:240"))
        assertEquals("yes", result["traffic_sign:forward"])
        assertEquals("DE:240", result["traffic_sign:backward"])
    }

    /* ------------------------------------------ F2 ------------------------------------------ */

    @Test fun `main sign is added to the group of additional signs without main sign`() {
        val groups = parseDE("DE:1022-10").groups
        assertNull(groups.single().main)
        assertEquals("DE:267,1022-10", groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
    }

    @Test fun `other groups are not changed when a main sign is added to an orphan group`() {
        val groups = parseDE("DE:1022-10;250,1020-30").groups
        assertEquals(2, groups.size)
        assertEquals(
            "DE:267,1022-10;250,1020-30",
            groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue()
        )
        // only the first orphan group gets it
        val twoOrphans = listOf(
            TrafficSignGroup(null, listOf(part("1022-10"))),
            TrafficSignGroup(part("250")),
            TrafficSignGroup(null, listOf(part("1020-30"))),
        )
        val result = twoOrphans.withMainSign(part("267"))
        assertEquals(part("267"), result[0].main)
        assertNull(result[2].main)
        assertEquals(twoOrphans[1], result[1])
    }

    @Test fun `without an orphan group a new group is added`() {
        assertEquals("DE:250;267", parseDE("DE:250").groups.withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:267", emptyList<TrafficSignGroup>().withMainSign(part("267")).toValueOrNull("DE")!!.toOsmValue())
    }

    /* ------------------------------------------ F3 ------------------------------------------ */

    @Test fun `empty and blank directional values do not hide the general value`() {
        for (blank in listOf("", " ", "  \t")) {
            val tags = mapOf("traffic_sign:forward" to blank, "traffic_sign" to "DE:250")
            assertEquals("DE:250", effectiveTrafficSignValue(tags, F))
            assertEquals(TrafficSignTagState.SIGNED, trafficSignTagState(tags, F))
            val (f, b) = parseTrafficSignWaySigns(tags, catalogue)
            assertEquals("DE:250", f!!.toOsmValue())
            assertEquals("DE:250", b!!.toOsmValue())
            assertEquals(DirectionSelection.of(f), WaySignSelection.of(tags, catalogue).forward)
        }
    }

    @Test fun `empty values without general value mean not set`() {
        for (blank in listOf("", " ")) {
            val tags = mapOf("traffic_sign:forward" to blank, "traffic_sign:backward" to blank, "traffic_sign" to blank)
            assertNull(effectiveTrafficSignValue(tags, F))
            assertEquals(TrafficSignTagState.NOT_SURVEYED, trafficSignTagState(tags, B))
            assertEquals(Pair(null, null), parseTrafficSignWaySigns(tags, catalogue))
            assertEquals(DirectionSelection.NotSurveyed, WaySignSelection.of(tags, catalogue).forward)
            assertTrue(needsTrafficSignSurvey(tags))
        }
    }

    @Test fun `tag state, parser and overlay style agree for all combinations of empty and filled values`() {
        val values = listOf(null, "", "  ", "none", "yes", "DE:250")
        for (g in values) for (f in values) for (b in values) {
            val tags = listOfNotNull(
                road, g?.let { "traffic_sign" to it }, f?.let { "traffic_sign:forward" to it }, b?.let { "traffic_sign:backward" to it }
            ).toMap()
            val parsed = parseTrafficSignWaySigns(tags, catalogue)
            for (d in TrafficSignDirection.entries) {
                val v = parsed.toList()[d.ordinal]
                val expected = when {
                    v == null -> TrafficSignTagState.NOT_SURVEYED
                    v.isNone -> TrafficSignTagState.NONE
                    v.toOsmValue() == "yes" -> TrafficSignTagState.UNSPECIFIC
                    else -> TrafficSignTagState.SIGNED
                }
                assertEquals(expected, trafficSignTagState(tags, d), "$tags $d")
            }
            // quest candidate <=> overlay does not show it as complete
            val complete = trafficSignWayStyle(tags).stroke!!.color !in listOf(
                de.westnordost.streetcomplete.data.overlays.OverlayColor.Red,
                de.westnordost.streetcomplete.data.overlays.OverlayColor.Orange,
            )
            assertEquals(complete, !needsTrafficSignSurvey(tags), "$tags")
        }
    }

    @Test fun `nothing edited changes nothing, also with empty tags`() {
        val tags = mapOf(road, "traffic_sign" to "DE:250", "traffic_sign:forward" to "", "traffic_sign:backward" to " ")
        assertTrue(WaySignSelection.of(tags, catalogue).toWaySigns(WaySignSelection.of(tags, catalogue)).deriveWayTags(tags).changes.isEmpty())
        assertEquals(tags, edit(tags))
        assertTrue(TrafficSignWaySigns(null, null).deriveWayTags(tags).changes.isEmpty())
    }

    @Test fun `empty tag of the unedited direction is not deleted`() {
        val tags = mapOf(road, "traffic_sign:backward" to "")
        val result = edit(tags, F to signed("DE:250"))
        assertEquals("", result["traffic_sign:backward"])
        assertEquals("DE:250", result["traffic_sign:forward"])
        val changes = TrafficSignWaySigns(parseDE("DE:250"), null).deriveWayTags(tags).changes
        assertTrue(changes.none { it is TrafficSignTagChange.Remove }, changes.toString())
    }

    @Test fun `empty general tag is not deleted`() {
        val tags = mapOf(road, "traffic_sign" to "")
        val changes = TrafficSignWaySigns(parseDE("DE:250"), parseDE("DE:274-30")).deriveWayTags(tags).changes
        assertTrue(changes.none { it is TrafficSignTagChange.Remove }, changes.toString())
    }

    @Test fun `empty directional value gets the general value when the other direction is edited`() {
        val tags = mapOf(road, "traffic_sign" to "DE:250", "traffic_sign:forward" to "")
        val result = edit(tags, B to signed("DE:274-30"))
        // forward keeps what applied to it so far: the general sign
        assertEquals("DE:250", result["traffic_sign:forward"])
        assertEquals("DE:274-30", result["traffic_sign:backward"])
        assertNull(result["traffic_sign"])
    }

    @Test fun `no false conflict for an empty directional tag`() {
        val tags = mapOf(road, "traffic_sign" to "DE:250", "traffic_sign:forward" to "")
        val tagging = TrafficSignWaySigns(null, parseDE("DE:274-30")).deriveWayTags(tags)
        assertTrue(tagging.resolvedConflicts.none { it.key == "traffic_sign:forward" }, tagging.resolvedConflicts.toString())
    }

    /* ------------------------------------------ F5 ------------------------------------------ */

    @Test fun `unchanged directions are passed as not surveyed whatever their spelling`() {
        for (raw in listOf("DE:274[30]", "de:274-30", "DE:274-30;", "DE:274[30],", " DE:274-30 ")) {
            val tags = mapOf(road, "traffic_sign" to raw)
            val original = WaySignSelection.of(tags, catalogue)
            assertEquals(TrafficSignWaySigns(null, null), original.toWaySigns(original), raw)
            assertEquals(tags, edit(tags), raw)
        }
    }

    @Test fun `setting a direction to an equivalent spelling changes nothing`() {
        val tags = mapOf(road, "traffic_sign" to "DE:274[30]")
        assertEquals(tags, edit(tags, F to signed("DE:274-30"), B to signed("DE:274-30")))
        assertEquals(tags, edit(tags, F to signed("DE:274-30")))
    }

    @Test fun `raw value of the unedited direction is preserved`() {
        for (raw in listOf("DE:274[30]", "DE:250;DE:274-30", "de:250", "DE:250,,1020-30;", "traffic_sign=DE:250", "DE:250[3]")) {
            val tags = mapOf(road, "traffic_sign" to raw)
            val result = edit(tags, B to signed("DE:240"))
            assertEquals(raw, result["traffic_sign:forward"], raw)
            assertEquals("DE:240", result["traffic_sign:backward"], raw)
        }
        val directional = mapOf(road, "traffic_sign:forward" to "DE:274[30]", "traffic_sign:backward" to "DE:250;DE:274-30")
        val result = edit(directional, F to signed("DE:240"))
        assertEquals("DE:250;DE:274-30", result["traffic_sign:backward"])
    }

    @Test fun `both directions changed to the same value give one general tag`() {
        val tags = mapOf(road, "traffic_sign:forward" to "DE:250", "traffic_sign:backward" to "DE:274[30]")
        assertEquals(mapOf(road, "traffic_sign" to "DE:240"), edit(tags, F to signed("DE:240"), B to signed("DE:240")))
    }

    @Test fun `changing a direction to the equivalent of the other keeps the other as written`() {
        // only the changed direction is rewritten, the other one is not touched
        val tags = mapOf(road, "traffic_sign:forward" to "DE:250", "traffic_sign:backward" to "DE:274[30]")
        val result = edit(tags, F to signed("DE:274-30"))
        assertEquals("DE:274-30", result["traffic_sign:forward"])
        assertEquals("DE:274[30]", result["traffic_sign:backward"])
    }

    @Test fun `answers are idempotent and reversing the way mirrors the result`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "traffic_sign:backward" to "DE:250")
        val first = edit(tags, F to signed("DE:240"))
        assertEquals(first, edit(first, F to signed("DE:240")))
        val original = WaySignSelection.of(first, catalogue)
        assertTrue(original.toWaySigns(original).deriveWayTags(first).changes.isEmpty())

        fun swap(m: Map<String, String>) = m.mapKeys { (k, _) ->
            k.replace("forward", "@").replace("backward", "forward").replace("@", "backward")
        }
        assertEquals(swap(first), edit(swap(tags), B to signed("DE:240")))
    }

    /* ------------------------------------------ F4 ------------------------------------------ */

    @Test fun `unreadable values are never partly interpreted`() {
        for (raw in listOf("DE:250;AT:52", "AT:52", "DE:274[30", "DE:250,\"Anlieger frei", "250")) {
            val v = parseDE(raw)
            assertTrue(v.isUnparseable, raw)
            assertTrue(v.groups.isEmpty(), raw)
            assertEquals(raw, v.toOsmValue())
        }
    }

    @Test fun `unreadable value of the unedited direction is kept`() {
        val tags = mapOf(road, "traffic_sign" to "DE:250;AT:52")
        val result = edit(tags, F to signed("DE:274-30"))
        assertEquals("DE:250;AT:52", result["traffic_sign:backward"])
        assertEquals("DE:274-30", result["traffic_sign:forward"])
    }

    @Test fun `unreadable value is only replaced by an explicit new selection`() {
        val tags = mapOf(road, "traffic_sign:forward" to "AT:52")
        val original = WaySignSelection.of(tags, catalogue)
        assertEquals(TrafficSignWaySigns(null, null), original.toWaySigns(original))
        // "leave as it is" does not replace it
        val kept = original.with(F, DirectionSelection.NotSurveyed)
        assertEquals(TrafficSignWaySigns(null, null), kept.toWaySigns(original))
        assertEquals(tags, edit(tags, F to DirectionSelection.NotSurveyed))
        // none and signs replace it
        assertEquals("none", edit(tags, F to DirectionSelection.None)["traffic_sign:forward"])
        assertEquals("DE:250", edit(tags, F to signed("DE:250"))["traffic_sign:forward"])
    }

    /* -------------------------- gaps: free texts, brackets, raw values -------------------------- */

    @Test fun `quoted free texts with separators and brackets survive a roundtrip`() {
        for (value in listOf(
            "DE:250,\"Anwohner, Taxi frei\"",
            "DE:250,\"a; b\";274-30",
            "DE:250,\"x=y [1,2]\"",
            "DE:250,\"one\",\"two, three\"",
            "DE:260,\"Kfz-Verkehr frei\";\"Text; mit, allem [und] =\"",
            "city_limit;DE:250,\"a,b\"",
        )) {
            val parsed = parseDE(value)
            assertFalse(parsed.isUnparseable, value)
            assertEquals(value, parsed.toOsmValue(), value)
            assertEquals(parsed, parseDE(parsed.toOsmValue()), value)
        }
    }

    @Test fun `free texts keep their parts when other signs are added or removed`() {
        val groups = parseDE("DE:250,\"Anwohner, Taxi frei\";274-30").groups
        assertEquals(
            "DE:250,\"Anwohner, Taxi frei\",1020-30;274-30",
            groups.withModifier(0, part("1020-30")).toValueOrNull("DE")!!.toOsmValue()
        )
        assertEquals("DE:274-30", groups.withoutGroup(0).toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:250;274-30", groups.withoutModifier(0, 0).toValueOrNull("DE")!!.toOsmValue())
    }

    @Test fun `custom bracket values survive editing of the group`() {
        val groups = parseDE("DE:250[3],1020-30;274-30").groups
        assertEquals(
            "DE:250[3],1020-30,1022-10;274-30",
            groups.withModifier(0, part("1022-10")).toValueOrNull("DE")!!.toOsmValue()
        )
        assertEquals("DE:250[3];274-30", groups.withoutModifier(0, 0).toValueOrNull("DE")!!.toOsmValue())
        // through the engine: only the edited direction is written
        val tags = mapOf(road, "traffic_sign:forward" to "DE:250[3],1020-30", "traffic_sign:backward" to "DE:239")
        val value = groups.withModifier(0, part("1022-10")).toValueOrNull("DE")!!
        val result = edit(tags, F to DirectionSelection.Signed(parseDE("DE:250[3],1020-30,1022-10")))
        assertEquals("DE:250[3],1020-30,1022-10", result["traffic_sign:forward"])
        assertEquals("DE:239", result["traffic_sign:backward"])
        assertEquals(value.toOsmValue().substringBefore(";"), result["traffic_sign:forward"])
    }

    @Test fun `raw values of the other direction survive every kind of edit`() {
        val raws = listOf("DE:274[30]", "de:250", "DE:250;DE:274-30", "AT:52", "DE:250,\"a, b\"", "yes", "maxspeed", "DE:250[3]")
        for (raw in raws) {
            val tags = mapOf(road, "traffic_sign:backward" to raw)
            for (sel in listOf(DirectionSelection.None, signed("DE:240"), signed("DE:239,1022-10"))) {
                assertEquals(raw, edit(tags, F to sel)["traffic_sign:backward"], "$raw $sel")
            }
        }
    }
}
