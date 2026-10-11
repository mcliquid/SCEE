package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.translate
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class GpxImportTest {
    @Test fun `duplicate points never start an unbounded interpolation`() {
        for (latitude in listOf(-70.0, -30.0, 0.0, 30.0, 52.5, 60.0, 70.0)) {
            val point = LatLon(latitude, 13.3)
            // Limit consumption so a regression fails instead of hanging the test process.
            assertEquals(listOf(point), interpolate(point, point, 200.0).take(4).toList(), "latitude $latitude")
        }
    }

    @Test fun `short segments do not step beyond their endpoint`() {
        val start = LatLon(52.5, 13.3)
        val end = start.translate(40.0, 90.0)
        assertEquals(listOf(start), interpolate(start, end, 200.0).toList())
    }

    @Test fun `long interpolation is bounded and follows original bearing`() {
        val start = LatLon(70.0, 13.3)
        val end = start.translate(10000.0, 90.0)
        val points = interpolate(start, end, 200.0).toList()
        assertTrue(points.size in 50..51)
        assertEquals(start, points.first())
        assertTrue(points.last().distanceTo(end) <= 200.001)
    }

    @Test fun `GPX keeps original track points and display setting`() = runBlocking {
        val start = LatLon(52.5, 13.3)
        val points = listOf(start, start, start.translate(800.0, 90.0))
        val imported = importGpx(points, true, 100.0).getOrThrow()
        assertEquals(points, imported.trackpoints)
        assertTrue(imported.displayTrack)
        assertTrue(imported.downloadAlongTrack)
        assertTrue(imported.downloadBBoxes.isNotEmpty())
    }
}
