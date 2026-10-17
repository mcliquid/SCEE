package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficSignTagStateTest {

    private fun state(vararg tags: Pair<String, String>, direction: TrafficSignDirection = TrafficSignDirection.FORWARD) =
        trafficSignTagState(mapOf(*tags), direction)

    @Test fun `states of a direction`() {
        assertEquals(TrafficSignTagState.NOT_SURVEYED, state())
        assertEquals(TrafficSignTagState.NOT_SURVEYED, state("traffic_sign" to "  "))
        assertEquals(TrafficSignTagState.UNSPECIFIC, state("traffic_sign" to "yes"))
        assertEquals(TrafficSignTagState.UNSPECIFIC, state("traffic_sign" to " YES "))
        assertEquals(TrafficSignTagState.NONE, state("traffic_sign" to "none"))
        assertEquals(TrafficSignTagState.NONE, state("traffic_sign" to "no"))
        assertEquals(TrafficSignTagState.NONE, state("traffic_sign" to "None"))
        assertEquals(TrafficSignTagState.SIGNED, state("traffic_sign" to "DE:274-30"))
        assertEquals(TrafficSignTagState.SIGNED, state("traffic_sign" to "AT:52"))
    }

    @Test fun `named values other than yes count as surveyed`() {
        for (v in listOf("maxspeed", "variable", "hazard", "signals", "destination", "city_limit")) {
            assertEquals(TrafficSignTagState.SIGNED, state("traffic_sign" to v), v)
        }
    }

    @Test fun `directional value wins over the general value`() {
        val tags = arrayOf("traffic_sign" to "yes", "traffic_sign:backward" to "none")
        assertEquals(TrafficSignTagState.UNSPECIFIC, state(*tags, direction = TrafficSignDirection.FORWARD))
        assertEquals(TrafficSignTagState.NONE, state(*tags, direction = TrafficSignDirection.BACKWARD))
    }

    private fun needs(vararg tags: Pair<String, String>) = needsTrafficSignSurvey(mapOf(*tags))

    @Test fun `survey is needed as long as one direction is missing or only yes`() {
        assertTrue(needs())
        assertTrue(needs("traffic_sign" to "yes"))
        assertTrue(needs("traffic_sign:forward" to "DE:274-30"))
        assertTrue(needs("traffic_sign:backward" to "none"))
        assertTrue(needs("traffic_sign:forward" to "yes", "traffic_sign:backward" to "DE:239"))
        assertTrue(needs("traffic_sign" to "DE:274-30", "traffic_sign:backward" to "yes"))
    }

    @Test fun `no survey needed if both directions are answered, none included`() {
        assertFalse(needs("traffic_sign" to "none"))
        assertFalse(needs("traffic_sign" to "no"))
        assertFalse(needs("traffic_sign" to "DE:274-30"))
        assertFalse(needs("traffic_sign" to "yes", "traffic_sign:forward" to "DE:1", "traffic_sign:backward" to "none"))
        assertFalse(needs("traffic_sign:forward" to "DE:240", "traffic_sign:backward" to "none"))
        assertFalse(needs("traffic_sign" to "maxspeed"))
    }

    @Test fun `oneway does not make the other direction irrelevant`() {
        assertTrue(needs("oneway" to "yes", "traffic_sign:forward" to "DE:274-30"))
        assertTrue(needs("oneway" to "-1", "traffic_sign:backward" to "DE:274-30"))
        assertFalse(needs("oneway" to "yes", "traffic_sign" to "none"))
    }

    @Test fun `reversing the way does not change whether a survey is needed`() {
        val directions = listOf(null, "yes", "none", "DE:274-30")
        for (f in directions) for (b in directions) {
            fun tags(f: String?, b: String?) = listOfNotNull(
                f?.let { "traffic_sign:forward" to it }, b?.let { "traffic_sign:backward" to it }
            ).toMap()
            assertEquals(needsTrafficSignSurvey(tags(f, b)), needsTrafficSignSurvey(tags(b, f)), "$f $b")
        }
    }

    /* -------------------------------------- country -------------------------------------- */

    // Germany: lon < 10, Poland: lon >= 10
    private val country: (LatLon) -> String? = { if (it.longitude < 10.0) "DE" else "PL" }
    private val de1 = LatLon(50.0, 9.0)
    private val de2 = LatLon(50.0, 9.001)
    private val pl = LatLon(50.0, 10.5)

    private fun line(vararg points: LatLon) =
        ElementPolylinesGeometry(listOf(points.toList()), points[points.size / 2])

    @Test fun `way completely in Germany`() {
        assertTrue(isWayInGermany(line(de1, de2), country))
        assertTrue(isWayInGermany(line(de1, de2), { if (it.longitude < 10.0) "DE-BY" else null }))
    }

    @Test fun `ways that are not or not completely in Germany`() {
        assertFalse(isWayInGermany(line(pl, LatLon(50.0, 10.6)), country))
        assertFalse(isWayInGermany(line(de1, de2, pl), country))
        assertFalse(isWayInGermany(line(pl, de1, de2), country))
        // the middle is in Germany, both ends are not
        assertFalse(isWayInGermany(ElementPolylinesGeometry(listOf(listOf(pl, de1, pl)), de1), country))
        assertFalse(isWayInGermany(null, country))
        assertFalse(isWayInGermany(ElementPointGeometry(de1), country))
        assertFalse(isWayInGermany(line(de1, de2), { null }))
    }
}
