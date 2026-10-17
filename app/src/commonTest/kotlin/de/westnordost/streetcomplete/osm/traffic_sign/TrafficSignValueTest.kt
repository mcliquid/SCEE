package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData.catalogueDE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficSignValueTest {

    /* ---------------------------------------- parsing ----------------------------------------- */

    @Test fun `parse single sign`() {
        val v = parseDE("DE:237")
        assertEquals("DE", v.country)
        assertEquals(listOf("237"), v.parts.map { it.text })
        assertEquals(catalogueDE.getByOsmValuePart("237"), v.parts.single().sign)
        assertTrue(v.isFullyRecognized)
    }

    @Test fun `parse main sign with additional sign`() {
        val v = parseDE("DE:239,1022-10")
        assertEquals(1, v.groups.size)
        val group = v.groups.single()
        assertEquals("239", group.main!!.text)
        assertEquals(listOf("1022-10"), group.modifiers.map { it.text })
        assertEquals(TrafficSignKind.EXCEPTION_MODIFIER, group.modifiers.single().kind)
    }

    @Test fun `parse independent signs`() {
        val v = parseDE("DE:250,1020-30;274-30")
        assertEquals(2, v.groups.size)
        assertEquals("250", v.groups[0].main!!.text)
        assertEquals(listOf("1020-30"), v.groups[0].modifiers.map { it.text })
        assertEquals("274-30", v.groups[1].main!!.text)
        assertEquals(emptyList(), v.groups[1].modifiers)
    }

    @Test fun `parse with country prefix repeated`() {
        assertEquals("DE:250;274-30", parseDE("DE:250;DE:274-30").toOsmValue())
    }

    @Test fun `parse lowercase country prefix`() {
        val v = parseDE("de:274-30")
        assertEquals("DE", v.country)
        assertEquals("DE:274-30", v.toOsmValue())
    }

    @Test fun `parse ignores key`() {
        assertEquals("DE:250", parseDE("traffic_sign=DE:250").toOsmValue())
        assertEquals("DE:250", parseDE("traffic_sign:forward=DE:250").toOsmValue())
    }

    @Test fun `parse trims whitespace`() {
        assertEquals("DE:250,1020-30", parseDE(" DE:250 , 1020-30 ").toOsmValue())
    }

    @Test fun `parse ignores empty parts`() {
        assertEquals("DE:250,1020-30", parseDE("DE:250,,1020-30;").toOsmValue())
    }

    @Test fun `parse redirects`() {
        assertEquals("DE:274-30", parseDE("DE:274[30]").toOsmValue())
        assertEquals("DE:241-30", parseDE("DE:241").toOsmValue())
        assertEquals("DE:242.1", parseDE("DE:242").toOsmValue())
        assertEquals("DE:250,\"Kfz-Verkehr frei\"", parseDE("DE:250,KFZ frei").toOsmValue())
    }

    @Test fun `parse variable values`() {
        val v = parseDE("DE:274[33]")
        val part = v.parts.single()
        assertEquals("274[33]", part.text)
        assertEquals("33", part.value)
        assertEquals("274[47]", part.sign!!.osmValuePart)
        assertEquals("Zulässige Höchstgeschwindigkeit von 33 km/h", part.description)
        assertEquals("DE:274[33]", v.toOsmValue())
    }

    @Test fun `parse variable value with separators inside brackets`() {
        val v = parseDE("DE:250,1040-31[08:00-11:00,16:00-18:00]")
        assertEquals(listOf("250", "1040-31[08:00-11:00,16:00-18:00]"), v.parts.map { it.text })
        assertEquals("08:00-11:00,16:00-18:00", v.parts[1].value)
        assertEquals(TrafficSignKind.CONDITION_MODIFIER, v.parts[1].kind)
        assertEquals("DE:250,1040-31[08:00-11:00,16:00-18:00]", v.toOsmValue())
    }

    @Test fun `parse variable value missing`() {
        val part = parseDE("DE:274").parts.single()
        assertTrue(part.isRecognized)
        assertTrue(part.isMissingValue)
        assertNull(part.value)
        assertEquals("DE:274", parseDE("DE:274").toOsmValue())
    }

    @Test fun `parse unexpected value`() {
        val part = parseDE("DE:250[3]").parts.single()
        assertTrue(part.hasUnexpectedValue)
        assertEquals("DE:250[3]", parseDE("DE:250[3]").toOsmValue())
    }

    @Test fun `parse free text sign`() {
        val v = parseDE("DE:260,\"Kfz-Verkehr frei\"")
        assertEquals(catalogueDE.getByOsmValuePart("\"Kfz-Verkehr frei\""), v.parts[1].sign)
    }

    @Test fun `unknown signs are retained`() {
        val v = parseDE("DE:250,9999-99;1234;\"Anwohner, Taxi frei\"")
        assertEquals(listOf("250", "9999-99", "1234", "\"Anwohner, Taxi frei\""), v.parts.map { it.text })
        assertEquals(
            listOf(TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.EXCEPTION_MODIFIER, TrafficSignKind.TRAFFIC_SIGN, TrafficSignKind.TRAFFIC_SIGN),
            v.parts.map { it.kind }
        )
        assertFalse(v.isFullyRecognized)
        assertFalse(v.parts[1].isRecognized)
        assertEquals("DE:250,9999-99;1234;\"Anwohner, Taxi frei\"", v.toOsmValue())
    }

    @Test fun `unknown additional sign as free text is retained`() {
        assertEquals("DE:250,\"Anwohner frei\"", parseDE("DE:250,\"Anwohner frei\"").toOsmValue())
    }

    @Test fun `parse named values`() {
        assertTrue(parseDE("none").isNone)
        assertTrue(parseDE("no").isNone)
        assertEquals("none", parseDE("no").toOsmValue())
        assertEquals("none", parseDE("DE:none").toOsmValue())
        val v = parseDE("city_limit;DE:310")
        assertTrue(v.parts[0].isNamedValue)
        assertEquals("310", v.parts[1].text)
        assertEquals("city_limit;DE:310", v.toOsmValue())
        assertEquals("DE:310;city_limit", parseDE("DE:310;city_limit").toOsmValue())
    }

    @Test fun `values that can not be parsed are retained unchanged`() {
        for (value in listOf(
            "AT:52", // other country
            "DE:250;AT:52", // mixed countries
            "DE:274[30", // unbalanced brackets
            "DE:250,\"Anlieger frei", // unbalanced quotes
            "250", // without country prefix
            "", // empty
            "DE:", // only prefix
        )) {
            val v = parseDE(value)
            assertTrue(v.isUnparseable, value)
            assertFalse(v.isFullyRecognized, value)
            assertEquals(value, v.toOsmValue(), value)
        }
    }

    /* ------------------------------------ formatting -------------------------------------- */

    @Test fun `format selected signs`() {
        val v = TrafficSignValue.of("DE", listOf(
            TrafficSignGroup(part("250"), listOf(part("1020-30"))),
            TrafficSignGroup(part("274-30")),
        ))
        assertEquals("DE:250,1020-30;274-30", v.toOsmValue())
    }

    @Test fun `format selected sign with variable value`() {
        val sign = catalogueDE.getByOsmValuePart("274[47]")!!
        assertEquals("DE:274[33]", TrafficSignValue.of("DE", listOf(TrafficSignGroup(TrafficSignPart.of(sign, "33")))).toOsmValue())
        // default value if none given
        assertEquals("DE:274[47]", TrafficSignValue.of("DE", listOf(TrafficSignGroup(TrafficSignPart.of(sign)))).toOsmValue())
    }

    @Test fun `variable value for sign without variable value is rejected`() {
        assertFailsWith<IllegalArgumentException> { TrafficSignPart.of(catalogueDE.getByOsmValuePart("250")!!, "3") }
        assertFailsWith<IllegalArgumentException> { TrafficSignPart.named("something") }
    }

    @Test fun `format none`() {
        assertEquals("none", TrafficSignValue.NONE.toOsmValue())
        assertTrue(TrafficSignValue.NONE.isNone)
    }

    @Test fun `format additional sign after named value uses semicolon`() {
        // like signsToTrafficSignTagValue of the converter
        val v = TrafficSignValue("DE", listOf(TrafficSignPart.named("city_limit"), part("1020-30")))
        assertEquals("city_limit;DE:1020-30", v.toOsmValue())
    }

    /* ------------------------------------- roundtrips ------------------------------------- */

    @Test fun `roundtrip of every single sign`() {
        for (sign in catalogueDE.signs) {
            val value = TrafficSignValue.of("DE", listOf(TrafficSignGroup(TrafficSignPart.of(sign))))
            val osmValue = value.toOsmValue()
            val parsed = parseDE(osmValue)
            assertEquals(value, parsed, osmValue)
            assertEquals(osmValue, parsed.toOsmValue())
        }
    }

    @Test fun `roundtrip of every main sign with every additional sign`() {
        val mainSigns = catalogueDE.signs.filter { !it.isModifier }
        val modifiers = catalogueDE.signs.filter { it.isModifier }
        for (main in mainSigns) {
            for (modifier in modifiers) {
                val value = TrafficSignValue.of("DE", listOf(TrafficSignGroup(TrafficSignPart.of(main), listOf(TrafficSignPart.of(modifier)))))
                val osmValue = value.toOsmValue()
                assertEquals(value, parseDE(osmValue), osmValue)
            }
        }
    }

    @Test fun `roundtrip of normalized existing values`() {
        for (value in listOf(
            "DE:239,1022-10",
            "DE:240,1000-31",
            "DE:260,1026-36",
            "DE:250,1020-30;274-30",
            "DE:274.1",
            "DE:274[33],1053-35",
            "DE:1040-30[16:00-18:00]",
            "DE:260,\"Kfz-Verkehr frei\"",
            "city_limit;DE:310",
            "none",
            "DE:250,9999;123",
        )) {
            assertEquals(value, parseDE(value).toOsmValue(), value)
            assertEquals(parseDE(value), parseDE(parseDE(value).toOsmValue()), value)
        }
    }

    @Test fun `parsing does not change the catalogue`() {
        val before = catalogueDE.signs.toList()
        parseDE("DE:274[33];274.1[25];262-7.5;Kleinkrafträder frei;1040-30[8:00-9:00]")
        assertEquals(before, catalogueDE.signs)
    }

    private fun part(osmValuePart: String) = TrafficSignPart.of(catalogueDE.getByOsmValuePart(osmValuePart)!!)
}
