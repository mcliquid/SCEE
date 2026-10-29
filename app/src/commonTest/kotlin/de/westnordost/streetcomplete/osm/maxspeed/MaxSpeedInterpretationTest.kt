package de.westnordost.streetcomplete.osm.maxspeed

import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedUnit.*
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedValue.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxSpeedInterpretationTest {

    private fun interpret(vararg tags: Pair<String, String>) = interpretMaxSpeed(mapOf(*tags))

    /* ----------------------------------- numeric ------------------------------------ */

    @Test fun `plain numbers are km per hour`() {
        for (n in listOf(30, 50, 100, 130)) {
            assertEquals(Numeric(n, KMH), parseMaxSpeedValue("$n"))
        }
    }

    @Test fun `mph is not km per hour`() {
        val v = assertIs<Numeric>(parseMaxSpeedValue("30 mph"))
        assertEquals(MPH, v.unit)
        assertEquals(30, v.value)
        assertEquals(48.28, v.kmh, 0.01)
    }

    @Test fun `explicit km per hour unit`() {
        assertEquals(Numeric(50, KMH, unitWasExplicit = true), parseMaxSpeedValue("50 km/h"))
    }

    @Test fun `knots`() {
        assertEquals(Numeric(10, KNOTS, unitWasExplicit = true), parseMaxSpeedValue("10 knots"))
    }

    @Test fun `unknown units are not reinterpreted`() {
        assertEquals(Invalid("50 kmh"), parseMaxSpeedValue("50 kmh"))
        assertEquals(Invalid("50 kph"), parseMaxSpeedValue("50 kph"))
    }

    @Test fun `malformed numbers are invalid`() {
        assertEquals(Invalid("50mph"), parseMaxSpeedValue("50mph"))
        assertEquals(Invalid("5o"), parseMaxSpeedValue("5o"))
        assertEquals(Invalid("0"), parseMaxSpeedValue("0"))
        assertEquals(Invalid("50;30"), parseMaxSpeedValue("50;30"))
        assertEquals(Invalid("fast"), parseMaxSpeedValue("fast"))
    }

    /* ---------------------------------- special values -------------------------------- */

    @Test fun `none is unrestricted`() {
        assertEquals(Unrestricted, parseMaxSpeedValue("none"))
    }

    @Test fun `walk`() {
        assertEquals(Walk, parseMaxSpeedValue("walk"))
    }

    @Test fun `signals and variable are recognized`() {
        assertEquals(Variable("signals"), parseMaxSpeedValue("signals"))
        assertEquals(Variable("variable"), parseMaxSpeedValue("variable"))
    }

    @Test fun `urban and rural codes carry no number`() {
        val urban = assertIs<Code>(parseMaxSpeedValue("DE:urban"))
        assertEquals("DE", urban.country)
        assertEquals("urban", urban.type)
        assertNull(urban.zoneSpeed)
        assertTrue(urban.isKnownType)
        assertNull(assertIs<Code>(parseMaxSpeedValue("DE:rural")).zoneSpeed)
    }

    @Test fun `zone codes carry their literal number`() {
        assertEquals(30, assertIs<Code>(parseMaxSpeedValue("DE:zone30")).zoneSpeed)
        assertEquals(20, assertIs<Code>(parseMaxSpeedValue("DE:zone:20")).zoneSpeed)
    }

    @Test fun `subdivision codes`() {
        val code = assertIs<Code>(parseMaxSpeedValue("US-WA:rural"))
        assertEquals("US-WA", code.country)
    }

    @Test fun `unknown but plausible country code is not invalid`() {
        val code = assertIs<Code>(parseMaxSpeedValue("XY:somethingelse"))
        assertFalse(code.isKnownType)
        assertTrue(interpret("maxspeed" to "XY:somethingelse").issues.isEmpty())
    }

    /* ----------------------------------- directional ---------------------------------- */

    @Test fun `forward only`() {
        val i = interpret("maxspeed:forward" to "70")
        assertEquals(Numeric(70, KMH), i.forward)
        assertNull(i.backward)
        assertTrue(i.isDirectional)
    }

    @Test fun `backward only`() {
        val i = interpret("maxspeed:backward" to "50")
        assertNull(i.forward)
        assertEquals(Numeric(50, KMH), i.backward)
    }

    @Test fun `different forward and backward`() {
        val i = interpret("maxspeed:forward" to "70", "maxspeed:backward" to "50")
        assertEquals(Numeric(70, KMH), i.forward)
        assertEquals(Numeric(50, KMH), i.backward)
        assertTrue(i.isDirectional)
    }

    @Test fun `general limit with forward override`() {
        val i = interpret("maxspeed" to "50", "maxspeed:forward" to "30")
        assertEquals(Numeric(30, KMH), i.forward)
        assertEquals(Numeric(50, KMH), i.backward)
    }

    @Test fun `general limit with backward override`() {
        val i = interpret("maxspeed" to "50", "maxspeed:backward" to "30")
        assertEquals(Numeric(50, KMH), i.forward)
        assertEquals(Numeric(30, KMH), i.backward)
    }

    @Test fun `same values in both directions are not directional`() {
        assertFalse(interpret("maxspeed:forward" to "50", "maxspeed:backward" to "50").isDirectional)
        assertFalse(interpret("maxspeed" to "50").isDirectional)
    }

    @Test fun `oneway does not reverse forward and backward`() {
        for (oneway in listOf("oneway" to "-1", "oneway:bicycle" to "-1")) {
            val i = interpret("maxspeed:forward" to "70", "maxspeed:backward" to "50", oneway)
            assertEquals(Numeric(70, KMH), i.forward)
            assertEquals(Numeric(50, KMH), i.backward)
        }
    }

    /* ----------------------------------- conditional ---------------------------------- */

    @Test fun `general limit with conditional`() {
        val i = interpret("maxspeed" to "50", "maxspeed:conditional" to "30 @ (Mo-Fr 07:00-17:00)")
        assertEquals(Numeric(50, KMH), i.forward)
        assertEquals(Numeric(50, KMH), i.backward)
        assertTrue(i.hasForwardConditional && i.hasBackwardConditional)
    }

    @Test fun `conditional only has no binding value`() {
        val i = interpret("maxspeed:conditional" to "30 @ (22:00-06:00)")
        assertFalse(i.hasBindingValue)
        assertTrue(i.hasConditional)
        assertFalse(i.isMissing)
    }

    @Test fun `directional conditional`() {
        val i = interpret("maxspeed" to "70", "maxspeed:forward:conditional" to "50 @ wet")
        assertTrue(i.hasForwardConditional)
        assertFalse(i.hasBackwardConditional)
    }

    @Test fun `conditional value does not overwrite the unconditional one`() {
        val i = interpret("maxspeed" to "70", "maxspeed:conditional" to "30 @ (Mo-Fr 07:00-17:00)")
        assertEquals(Numeric(70, KMH), i.forward)
    }

    /* ------------------------------------ advisory ------------------------------------ */

    @Test fun `advisory only is not binding`() {
        val i = interpret("highway" to "primary", "maxspeed:advisory" to "70")
        assertFalse(i.hasBindingValue)
        assertEquals(Numeric(70, KMH), i.forwardAdvisory)
        assertFalse(i.isMissing)
    }

    @Test fun `binding plus advisory`() {
        val i = interpret("maxspeed" to "100", "maxspeed:advisory" to "70")
        assertEquals(Numeric(100, KMH), i.forward)
        assertEquals(Numeric(70, KMH), i.forwardAdvisory)
    }

    @Test fun `directional advisory`() {
        val i = interpret("maxspeed:advisory:forward" to "60")
        assertEquals(Numeric(60, KMH), i.forwardAdvisory)
        assertNull(i.backwardAdvisory)
    }

    /* ------------------------------- missing / type keys ------------------------------ */

    @Test fun `missing`() {
        val i = interpret("highway" to "residential")
        assertTrue(i.isMissing)
        assertNull(i.forward)
        assertNull(i.backward)
    }

    @Test fun `no speed inferred from highway`() {
        for (highway in listOf("residential", "primary", "motorway", "service", "living_street")) {
            val i = interpret("highway" to highway)
            assertTrue(i.isMissing)
            assertFalse(i.hasBindingValue)
        }
    }

    @Test fun `type key without maxspeed is an implicit code, not a number`() {
        val i = interpret("maxspeed:type" to "DE:urban")
        assertFalse(i.hasBindingValue)
        assertEquals("DE:urban", i.typeCode?.raw)
        assertFalse(i.isMissing)
    }

    @Test fun `source maxspeed sign is no code`() {
        val i = interpret("maxspeed" to "50", "source:maxspeed" to "sign")
        assertNull(i.typeCode)
        assertTrue(i.issues.isEmpty())
    }

    @Test fun `zone maxspeed with number is normalized to a zone`() {
        assertEquals(30, interpret("zone:maxspeed" to "DE:30").typeCode?.zoneSpeed)
    }

    /* ------------------------------------- issues ------------------------------------- */

    @Test fun `invalid value is an issue`() {
        assertEquals(setOf(MaxSpeedIssue.INVALID_VALUE), interpret("maxspeed" to "fast").issues)
        assertEquals(setOf(MaxSpeedIssue.INVALID_VALUE), interpret("maxspeed:forward" to "50 kmh").issues)
    }

    @Test fun `numeric limit contradicting zone`() {
        assertEquals(setOf(MaxSpeedIssue.ZONE_SPEED_MISMATCH), interpret("maxspeed" to "50", "maxspeed:type" to "DE:zone30").issues)
        assertEquals(setOf(MaxSpeedIssue.ZONE_SPEED_MISMATCH), interpret("maxspeed" to "50", "zone:maxspeed" to "DE:30").issues)
    }

    @Test fun `matching zone is fine`() {
        assertTrue(interpret("maxspeed" to "30", "maxspeed:type" to "DE:zone30").issues.isEmpty())
        assertTrue(interpret("maxspeed" to "30", "source:maxspeed" to "DE:zone:30").issues.isEmpty())
    }

    @Test fun `mph limit is not compared against zone number`() {
        assertTrue(interpret("maxspeed" to "20 mph", "maxspeed:type" to "GB:zone20").issues.isEmpty())
    }

    @Test fun `none in zone`() {
        assertEquals(setOf(MaxSpeedIssue.UNRESTRICTED_IN_ZONE), interpret("maxspeed" to "none", "maxspeed:type" to "DE:zone30").issues)
    }

    @Test fun `tags written by AddMaxSpeed are valid`() {
        val written = listOf(
            mapOf("maxspeed" to "50", "maxspeed:type" to "sign"),
            mapOf("maxspeed" to "30", "maxspeed:type" to "DE:zone30"),
            mapOf("maxspeed:type" to "DE:urban"),
            mapOf("maxspeed:type" to "DE:living_street"),
            mapOf("maxspeed:advisory" to "30 mph", "maxspeed:type:advisory" to "sign"),
        )
        for (tags in written) assertTrue(interpretMaxSpeed(tags).issues.isEmpty(), "$tags")
    }
}
