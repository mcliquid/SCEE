package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrafficSignWayArrowsTest {
    private val north = listOf(LatLon(0.0, 0.0), LatLon(0.002, 0.0))
    // first leg north, second leg east
    private val corner = listOf(LatLon(0.0, 0.0), LatLon(0.001, 0.0), LatLon(0.001, 0.001))

    private fun assertBearing(expected: Double, actual: Double) {
        val diff = ((actual - expected + 540) % 360) - 180
        assertTrue(kotlin.math.abs(diff) < 1.0, "expected $expected but was $actual")
    }

    @Test fun `forward arrows point in the direction of the way`() {
        val arrows = north.directionArrows(TrafficSignDirection.FORWARD)
        assertEquals(3, arrows.size)
        arrows.forEach { assertBearing(0.0, it.bearing) }
    }

    @Test fun `backward arrows point against the direction of the way`() {
        val arrows = north.directionArrows(TrafficSignDirection.BACKWARD)
        assertEquals(2, arrows.size)
        arrows.forEach { assertBearing(180.0, it.bearing) }
    }

    @Test fun `arrows follow the curve of the way`() {
        val forward = corner.directionArrows(TrafficSignDirection.FORWARD)
        // 20 % of the way: still on the first leg
        assertBearing(0.0, forward.first().bearing)
        // 80 % of the way: on the second leg
        assertBearing(90.0, forward.last().bearing)
        val backward = corner.directionArrows(TrafficSignDirection.BACKWARD)
        // 35 % on the first leg (north) -> pointing south, 65 % on the second leg -> pointing west
        assertBearing(180.0, backward.first().bearing)
        assertBearing(270.0, backward.last().bearing)
    }

    @Test fun `arrows lie on the way`() {
        for (arrow in corner.directionArrows(TrafficSignDirection.FORWARD)) {
            val onFirstLeg = arrow.position.longitude < 1e-9 && arrow.position.latitude in 0.0..0.0010001
            val onSecondLeg = kotlin.math.abs(arrow.position.latitude - 0.001) < 1e-6 && arrow.position.longitude in 0.0..0.0010001
            assertTrue(onFirstLeg || onSecondLeg, "${arrow.position} is not on the way")
        }
    }

    @Test fun `reversed way gives reversed arrows`() {
        val forwardOnReversed = corner.reversed().directionArrows(TrafficSignDirection.FORWARD)
        assertBearing(270.0, forwardOnReversed.first().bearing)
    }

    @Test fun `degenerate ways have no arrows`() {
        assertEquals(emptyList(), emptyList<LatLon>().directionArrows(TrafficSignDirection.FORWARD))
        assertEquals(emptyList(), listOf(LatLon(1.0, 1.0)).directionArrows(TrafficSignDirection.FORWARD))
        assertEquals(emptyList(), listOf(LatLon(1.0, 1.0), LatLon(1.0, 1.0)).directionArrows(TrafficSignDirection.FORWARD))
    }
}
