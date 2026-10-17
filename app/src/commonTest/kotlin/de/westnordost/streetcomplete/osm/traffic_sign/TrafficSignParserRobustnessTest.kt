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

/** F6 (traffic_sign= inside of free text) and F7 (custom signs with , ; = in quotation marks). The
 *  expected values are written out literally, not derived by parsing and formatting again, so that
 *  a mistake of both parser and formatter does not go unnoticed. */
class TrafficSignParserRobustnessTest {

    private val catalogue = TrafficSignTestData.catalogueDE

    private fun texts(value: String) = parseDE(value).parts.map { it.text }
    private fun kinds(value: String) = parseDE(value).parts.map { it.kind }

    /* ------------------------------------------ F6 ------------------------------------------ */

    @Test fun `a leading key is removed`() {
        for (key in listOf("traffic_sign", "traffic_sign:forward", "traffic_sign:backward", "traffic_sign ", "traffic_sign:forward  ", "bicycle:right:traffic_sign")) {
            val v = parseDE("$key=DE:250")
            assertEquals(listOf("250"), v.parts.map { it.text }, key)
            assertEquals("DE:250", v.toOsmValue(), key)
        }
        assertEquals("DE:250", parseDE("traffic_sign = DE:250").toOsmValue())
        assertEquals("DE:250,1020-30", parseDE(" traffic_sign=DE:250,1020-30 ").toOsmValue())
    }

    @Test fun `an equals sign inside of a free text is never a key`() {
        val v = parseDE("DE:250,\"traffic_sign=x\"")
        assertFalse(v.isUnparseable)
        assertEquals(listOf("250", "\"traffic_sign=x\""), v.parts.map { it.text })
        assertEquals(listOf(TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.EXCEPTION_MODIFIER), v.parts.map { it.kind })
        assertEquals("DE:250,\"traffic_sign=x\"", v.toOsmValue())

        assertEquals(listOf("250", "\"Anwohner=ja\""), texts("DE:250,\"Anwohner=ja\""))
        assertEquals("DE:250,\"Anwohner=ja\"", parseDE("DE:250,\"Anwohner=ja\"").toOsmValue())
    }

    @Test fun `only the leading key is removed`() {
        val v = parseDE("traffic_sign=DE:250,\"traffic_sign:forward=x\"")
        assertEquals(listOf("250", "\"traffic_sign:forward=x\""), v.parts.map { it.text })
        assertEquals("DE:250,\"traffic_sign:forward=x\"", v.toOsmValue())
    }

    @Test fun `a key is only a key if it is traffic_sign`() {
        for (value in listOf("foo=DE:250", "Traffic_Sign=DE:250", "traffic_signs=DE:250", "traffic_sign:Forward=DE:250", "x traffic_sign=DE:250", "\"traffic_sign\"=DE:250")) {
            val v = parseDE(value)
            assertTrue(v.isUnparseable, value)
            assertEquals(value, v.toOsmValue(), value)
        }
    }

    @Test fun `free text with every special character`() {
        for (value in listOf(
            "DE:250,\"a=b, c; d [e] f\"",
            "DE:250,\"traffic_sign=x; traffic_sign=y\"",
            "DE:250,\"[\"",
            "DE:250,\"]\"",
            "DE:250,\"1,2;3=4[5]\";274-30",
            "DE:250,\"x\",\"y, z\"",
            "city_limit;DE:250,\"a, b\"",
        )) {
            val v = parseDE(value)
            assertFalse(v.isUnparseable, value)
            assertEquals(value, v.toOsmValue(), value)
        }
        assertEquals(listOf("250", "\"a=b, c; d [e] f\""), texts("DE:250,\"a=b, c; d [e] f\""))
        assertEquals(listOf("250", "\"1,2;3=4[5]\"", "274-30"), texts("DE:250,\"1,2;3=4[5]\";274-30"))
    }

    @Test fun `main sign, additional sign, free text and unknown fragments in one value`() {
        val value = "DE:250,1020-30,\"Sonst, was\";274-30;9999-99,\"x=y\""
        assertEquals(listOf("250", "1020-30", "\"Sonst, was\"", "274-30", "9999-99", "\"x=y\""), texts(value))
        assertEquals(
            listOf(
                TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.EXCEPTION_MODIFIER, TrafficSignKind.EXCEPTION_MODIFIER,
                TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.EXCEPTION_MODIFIER,
            ),
            kinds(value)
        )
        assertEquals(value, parseDE(value).toOsmValue())
        assertEquals(listOf(3, 1, 2), parseDE(value).groups.map { listOfNotNull(it.main).size + it.modifiers.size })
    }

    @Test fun `damaged values with a key stay as they are`() {
        for (value in listOf("traffic_sign=DE:250,\"x", "traffic_sign=DE:274[30", "traffic_sign=", "traffic_sign=DE:", "traffic_sign=AT:52")) {
            val v = parseDE(value)
            assertTrue(v.isUnparseable, value)
            assertEquals(value, v.toOsmValue(), value)
        }
    }

    @Test fun `a doubled key is not removed twice`() {
        // the second one is part of the value, which is then not readable
        assertTrue(parseDE("traffic_sign=traffic_sign=DE:250").isUnparseable)
    }

    @Test fun `spaces after the country prefix are part of the notation, not of the sign`() {
        assertEquals("DE:250", parseDE("DE: 250").toOsmValue())
        assertEquals("DE:250,1020-30", parseDE("DE:  250 , 1020-30").toOsmValue())
        assertTrue(parseDE("DE: 250").parts.single().isRecognized)
    }

    /* ----------------------------------- raw values stay raw ----------------------------------- */

    private fun edit(tags: Map<String, String>, vararg edits: Pair<TrafficSignDirection, DirectionSelection>): Map<String, String> {
        val original = WaySignSelection.of(tags, catalogue)
        val selection = edits.fold(original) { s, (d, sel) -> s.with(d, sel) }
        val builder = StringMapChangesBuilder(tags)
        selection.toWaySigns(original).deriveWayTags(tags).applyTo(builder)
        val m = tags.toMutableMap()
        for (c in builder.create().changes) when (c) {
            is StringMapEntryAdd -> m[c.key] = c.value
            is StringMapEntryModify -> m[c.key] = c.value
            is StringMapEntryDelete -> m.remove(c.key)
        }
        return m
    }

    @Test fun `raw values with keys and free text of the unedited direction are kept`() {
        for (raw in listOf(
            "traffic_sign=DE:250", "DE:250,\"traffic_sign=x\"", "DE:250,\"Anwohner, Taxi frei\"", "DE:274[30],\"a=b\"", "DE:250,,1020-30;",
            "traffic_sign:forward=DE:250,\"x\"", "DE:250,\"unclosed",
        )) {
            val tags = mapOf("highway" to "track", "traffic_sign:forward" to raw)
            val result = edit(tags, TrafficSignDirection.BACKWARD to DirectionSelection.None)
            assertEquals(raw, result["traffic_sign:forward"], raw)
            assertEquals("none", result["traffic_sign:backward"], raw)
            // merely opening and confirming does not rewrite anything
            assertEquals(tags, edit(tags), raw)
        }
    }

    /* ------------------------------------------ F7 ------------------------------------------ */

    private fun custom(text: String, asModifier: Boolean = true) = parseCustomTrafficSign(text, catalogue, asModifier)

    @Test fun `free texts with comma, semicolon and equals sign are accepted`() {
        for (text in listOf("\"Anwohner, Taxi frei\"", "\"Lieferverkehr; Anlieger\"", "\"A=B\"", "\"a, b; c = d [e]\"", "DE:\"Anwohner, Taxi frei\"", "  \"Anwohner, Taxi frei\"  ")) {
            val part = assertNotNull(custom(text), text)
            assertNull(part.sign, text)
            assertEquals(text.trim().removePrefix("DE:"), part.text, text)
        }
    }

    @Test fun `the kind of a custom free text is given by the place where it is added`() {
        assertEquals(TrafficSignKind.EXCEPTION_MODIFIER, custom("\"Anwohner, Taxi frei\"", asModifier = true)!!.kind)
        assertEquals(TrafficSignKind.TRAFFIC_SIGN, custom("\"Anwohner, Taxi frei\"", asModifier = false)!!.kind)
        // a text from the catalogue is the catalogue sign
        assertNotNull(custom("\"Kfz-Verkehr frei\"")!!.sign)
    }

    @Test fun `custom variable values with brackets still work`() {
        val speed = custom("274[33]", asModifier = false)!!
        assertEquals("274[33]", speed.text)
        assertEquals("33", speed.value)
        // a value that has a sign of its own in the catalogue is that sign
        assertEquals("274-30", custom("274[30]", asModifier = false)!!.text)
        assertEquals("1040-31[08:00-11:00,16:00-18:00]", custom("1040-31[08:00-11:00,16:00-18:00]")!!.text)
        assertEquals("1042-31[Mo-Sa 18:00-19:00]", custom("1042-31[Mo-Sa 18:00-19:00]")!!.text)
        assertNull(custom("274"))
    }

    @Test fun `a bracket value for a sign without variable value is not offered, but kept if it exists`() {
        // 1020-32[42] is not a catalogue value: as input it is rejected (open decision, see report) ...
        assertNull(custom("1020-32[42]"))
        // ... but an existing value is read and written back as it is
        val v = parseDE("DE:250,1020-32[42]")
        assertEquals("DE:250,1020-32[42]", v.toOsmValue())
        assertTrue(v.parts[1].hasUnexpectedValue)
    }

    @Test fun `ambiguous, unbalanced and invalid input is rejected`() {
        for (text in listOf(
            "", " ", "237,1022-10", "237;250", "a,b", "a;b", "a=b", "a b", "traffic_sign=250", "\"unclosed", "unclosed\"", "\"a\"b\"", "\"a\"\"b\"", "\"\"", "\"  \"",
            "\"a\" \"b\"", "\"a\",\"b\"", "274[30", "274]30", "274[[30]]", "274[3[0]]", "274[30]]", "250,\"x\"", "DE:", "city_limit", "none", "yes", "\"a\"x", "274[30]x", "274[]x",
        )) {
            assertNull(custom(text), "[$text]")
        }
    }

    @Test fun `every accepted custom sign gives a value that reads back unchanged`() {
        val inputs = listOf(
            "\"Anwohner, Taxi frei\"", "\"Lieferverkehr; Anlieger\"", "\"A=B\"", "\"[x]\"", "274[33]", "1040-31[08:00-11:00,16:00-18:00]",
            "9999-99", "1010-50", "\"Kfz-Verkehr frei\"", "250", "\"Kleinkrafträder frei\"",
        )
        for (asModifier in listOf(true, false)) for (input in inputs) {
            val part = assertNotNull(custom(input, asModifier), input)
            val groups = if (asModifier) listOf(TrafficSignGroup(TrafficSignPart.of(catalogue.getByOsmValuePart("250")!!), listOf(part)))
            else listOf(TrafficSignGroup(part))
            val value = groups.toValueOrNull("DE")!!
            val osm = value.toOsmValue()
            val back = parseTrafficSignValue(osm, catalogue)
            assertFalse(back.isUnparseable, osm)
            assertEquals(value.parts.map { it.text }, back.parts.map { it.text }, osm)
            assertEquals(value.parts.map { it.kind }, back.parts.map { it.kind }, osm)
            assertEquals(osm, back.toOsmValue(), osm)
        }
    }

    @Test fun `several free texts and catalogue signs in one value`() {
        val main = TrafficSignPart.of(catalogue.getByOsmValuePart("250")!!)
        val a = custom("\"Anwohner, Taxi frei\"")!!
        val b = custom("\"Lieferverkehr; Anlieger\"")!!
        val mod = TrafficSignPart.of(catalogue.getByOsmValuePart("1020-30")!!)
        val value = listOf(TrafficSignGroup(main, listOf(a, mod, b)), TrafficSignGroup(custom("\"Sonder, Zeichen\"", asModifier = false))).toValueOrNull("DE")!!
        assertEquals("DE:250,\"Anwohner, Taxi frei\",1020-30,\"Lieferverkehr; Anlieger\";\"Sonder, Zeichen\"", value.toOsmValue())
        val back = parseTrafficSignValue(value.toOsmValue(), catalogue)
        assertEquals(listOf("250", "\"Anwohner, Taxi frei\"", "1020-30", "\"Lieferverkehr; Anlieger\"", "\"Sonder, Zeichen\""), back.parts.map { it.text })
        assertEquals(2, back.groups.size)
        assertEquals(3, back.groups[0].modifiers.size)
    }

    @Test fun `from input to the tag of the way`() {
        val tags = mapOf("highway" to "residential", "traffic_sign:backward" to "DE:239,\"x, y\"")
        val original = WaySignSelection.of(tags, catalogue)
        // the user adds a custom free text to a main sign group and confirms for forward
        val groups = parseDE("DE:250").groups
        val withText = groups.withModifier(0, custom("\"Anwohner, Taxi frei\"")!!)
        val result = edit(tags, TrafficSignDirection.FORWARD to DirectionSelection.Signed(withText.toValueOrNull("DE")!!))
        assertEquals("DE:250,\"Anwohner, Taxi frei\"", result["traffic_sign:forward"])
        assertEquals("DE:239,\"x, y\"", result["traffic_sign:backward"])
        assertEquals(setOf("highway", "traffic_sign:forward", "traffic_sign:backward"), result.keys)
        // for both directions, as the general tag
        val both = edit(tags - "traffic_sign:backward", TrafficSignDirection.FORWARD to DirectionSelection.Signed(withText.toValueOrNull("DE")!!), TrafficSignDirection.BACKWARD to DirectionSelection.Signed(withText.toValueOrNull("DE")!!))
        assertEquals("DE:250,\"Anwohner, Taxi frei\"", both["traffic_sign"])
        // reading the new tag gives the same selection again
        val again = WaySignSelection.of(result, catalogue)
        assertEquals(DirectionSelection.Signed(withText.toValueOrNull("DE")!!).encode(), again.forward.encode())
        assertEquals(original.backward.encode(), again.backward.encode())
    }
}
