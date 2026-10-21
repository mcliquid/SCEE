package de.westnordost.streetcomplete.data

import de.westnordost.streetcomplete.data.download.DownloadController
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
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

    @Test fun `GPX track download enqueues tail bboxes before first download`() {
        val b1 = BoundingBox(LatLon(0.0, 0.0), LatLon(1.0, 1.0))
        val b2 = BoundingBox(LatLon(2.0, 2.0), LatLon(3.0, 3.0))
        val b3 = BoundingBox(LatLon(4.0, 4.0), LatLon(5.0, 5.0))
        val started = mutableListOf<BoundingBox>()
        val controller = object : DownloadController {
            override fun download(bbox: BoundingBox, isUserInitiated: Boolean, enqueue: Boolean) {
                started.add(bbox)
            }
            override fun downloadAlongGpxTrack(trackPoints: List<LatLon>, minDownloadDistance: Double) = Unit
        }
        Downloader.enqueuedDownloads.clear()
        try {
            enqueueGpxTrackDownloads(controller, listOf(b1, b2, b3))
            assertEquals(listOf(b1), started)
            assertEquals(listOf(b2, b3), Downloader.enqueuedDownloads.toList())
        } finally {
            Downloader.enqueuedDownloads.clear()
        }
    }
}
