package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficSignSelectionTest {
    private val catalogue = TrafficSignTestData.catalogueDE

    private fun selectionOf(tags: Map<String, String>) = WaySignSelection.of(tags, catalogue)

    private fun signed(value: String) = DirectionSelection.Signed(parseDE(value))

    private fun TrafficSignWayTagging.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.entries.associate { it.key to it.value }
    }

    /* ----------------------------------- three states ----------------------------------- */

    @Test fun `missing tags are not surveyed, not none`() {
        val s = selectionOf(mapOf("highway" to "footway"))
        assertEquals(DirectionSelection.NotSurveyed, s.forward)
        assertEquals(DirectionSelection.NotSurveyed, s.backward)
        assertNull(s.toWaySigns().forward)
        assertNull(s.toWaySigns().backward)
    }

    @Test fun `none is its own state`() {
        val s = selectionOf(mapOf("traffic_sign" to "none"))
        assertEquals(DirectionSelection.None, s.forward)
        assertEquals(DirectionSelection.None, s.backward)
        assertEquals(TrafficSignValue.NONE, s.toWaySigns().forward)
    }

    @Test fun `directional tags are read per direction`() {
        val s = selectionOf(mapOf("traffic_sign:forward" to "DE:237", "traffic_sign:backward" to "none"))
        assertEquals(signed("DE:237"), s.forward)
        assertEquals(DirectionSelection.None, s.backward)
    }

    @Test fun `general tag applies to a direction without own tag`() {
        val s = selectionOf(mapOf("traffic_sign" to "DE:240", "traffic_sign:forward" to "DE:239"))
        assertEquals(signed("DE:239"), s.forward)
        assertEquals(signed("DE:240"), s.backward)
    }

    @Test fun `encode and decode round trip`() {
        for (selection in listOf(
            DirectionSelection.NotSurveyed,
            DirectionSelection.None,
            signed("DE:237"),
            signed("DE:239,1022-10"),
            signed("DE:274[30]"),
            signed("DE:250,1020-30;274-30"),
        )) {
            assertEquals(selection, DirectionSelection.decode(selection.encode(), catalogue), selection.encode())
        }
    }

    @Test fun `unparseable existing value is kept as it is`() {
        val s = selectionOf(mapOf("traffic_sign" to "AT:52"))
        val forward = s.forward as DirectionSelection.Signed
        assertTrue(forward.value.isUnparseable)
        assertEquals("AT:52", forward.encode())
        assertEquals(forward, DirectionSelection.decode(forward.encode(), catalogue))
    }

    /* ---------------------------------- partial editing ---------------------------------- */

    @Test fun `editing one direction does not change the other`() {
        val tags = mapOf(
            "highway" to "footway", "bicycle" to "designated", "segregated" to "no",
            "traffic_sign" to "DE:240",
        )
        val original = selectionOf(tags)
        val edited = original.with(TrafficSignDirection.FORWARD, signed("DE:239"))
        val result = edited.toWaySigns().deriveWayTags(tags).appliedTo(tags)
        assertEquals("DE:239", result["traffic_sign:forward"])
        assertEquals("DE:240", result["traffic_sign:backward"])
        assertNull(result["traffic_sign"])
        // backward is still a shared foot and cycle way
        assertEquals("designated", result["bicycle:backward"] ?: result["bicycle"])
    }

    @Test fun `not surveyed in both directions changes nothing`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "traffic_sign:forward" to "DE:237")
        val s = WaySignSelection(DirectionSelection.NotSurveyed, DirectionSelection.NotSurveyed)
        assertEquals(emptyList(), s.toWaySigns().deriveWayTags(tags).changes)
    }

    @Test fun `not surveyed direction is never written as none`() {
        val tags = mapOf("highway" to "footway")
        val s = WaySignSelection(signed("DE:239"), DirectionSelection.NotSurveyed)
        val result = s.toWaySigns().deriveWayTags(tags).appliedTo(tags)
        assertEquals("DE:239", result["traffic_sign:forward"])
        assertNull(result["traffic_sign:backward"])
        assertTrue(result.values.none { it == "none" })
    }

    @Test fun `unusual and contradicting combinations are still saved`() {
        val tags = mapOf("highway" to "residential")
        val s = WaySignSelection(signed("DE:237;250;274[30]"), signed("DE:240,1000-31"))
        val result = s.toWaySigns().deriveWayTags(tags).appliedTo(tags)
        assertEquals("DE:237;250;274-30", result["traffic_sign:forward"])
        assertEquals("DE:240,1000-31", result["traffic_sign:backward"])
    }

    /* ------------------------------ replacing signs with none ------------------------------ */

    @Test fun `replacing existing signs with none needs confirmation`() {
        val tags = mapOf("traffic_sign:forward" to "DE:237")
        val s = selectionOf(tags).with(TrafficSignDirection.FORWARD, DirectionSelection.None)
        assertTrue(s.replacesSignsWithNone(tags, catalogue))
    }

    @Test fun `none on a direction without signs needs no confirmation`() {
        val tags = mapOf("highway" to "footway")
        val s = selectionOf(tags).with(TrafficSignDirection.FORWARD, DirectionSelection.None)
        assertFalse(s.replacesSignsWithNone(tags, catalogue))
    }

    @Test fun `none over none needs no confirmation`() {
        val tags = mapOf("traffic_sign" to "none")
        assertFalse(selectionOf(tags).replacesSignsWithNone(tags, catalogue))
    }

    /* ------------------------------------- editing groups ------------------------------------- */

    @Test fun `groups can be composed and decomposed`() {
        val main = TrafficSignPart.of(catalogue.getByOsmValuePart("239")!!)
        val modifier = TrafficSignPart.of(catalogue.getByOsmValuePart("1022-10")!!)
        val second = TrafficSignPart.of(catalogue.getByOsmValuePart("250")!!)
        var groups = emptyList<TrafficSignGroup>()
        assertNull(groups.toValueOrNull("DE"))
        groups = groups.withMainSign(main).withModifier(0, modifier).withMainSign(second)
        assertEquals("DE:239,1022-10;250", groups.toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:239;250", groups.withoutModifier(0, 0).toValueOrNull("DE")!!.toOsmValue())
        assertEquals("DE:250", groups.withoutGroup(0).toValueOrNull("DE")!!.toOsmValue())
        assertNull(groups.withoutGroup(0).withoutGroup(0).toValueOrNull("DE"))
    }

    @Test fun `variable value input is validated`() {
        assertTrue(isValidTrafficSignValueInput("integer", "30"))
        assertFalse(isValidTrafficSignValueInput("integer", "3.5"))
        assertFalse(isValidTrafficSignValueInput("integer", ""))
        assertTrue(isValidTrafficSignValueInput("float", "7,5"))
        assertEquals("7.5", normalizeTrafficSignValueInput("float", " 7,5 "))
        assertFalse(isValidTrafficSignValueInput("float", "x"))
        assertTrue(isValidTrafficSignValueInput("opening_hours", "Mo-Fr 16:00-18:00"))
        assertFalse(isValidTrafficSignValueInput("integer", "3]0"))
    }

    @Test fun `custom signs`() {
        val known = parseCustomTrafficSign("1010-50", catalogue, asModifier = true)
        assertNotNull(known)
        assertNotNull(known.sign)
        assertEquals("1010-50", known.text)
        val prefixed = parseCustomTrafficSign("DE:237", catalogue, asModifier = false)
        assertEquals("237", prefixed?.text)

        val unknown = parseCustomTrafficSign("9999-99", catalogue, asModifier = true)
        assertNotNull(unknown)
        assertNull(unknown.sign)
        assertEquals(TrafficSignKind.EXCEPTION_MODIFIER, unknown.kind)
        assertEquals("DE:9999-99", TrafficSignValue.of("DE", listOf(TrafficSignGroup(null, listOf(unknown)))).toOsmValue())

        assertNull(parseCustomTrafficSign("", catalogue, false))
        assertNull(parseCustomTrafficSign("237,1022-10", catalogue, false))
        assertNull(parseCustomTrafficSign("a b", catalogue, false))
        // variable value missing
        assertNull(parseCustomTrafficSign("274", catalogue, false))
    }
}
