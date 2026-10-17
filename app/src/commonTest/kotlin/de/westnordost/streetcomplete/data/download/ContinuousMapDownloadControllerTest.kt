package de.westnordost.streetcomplete.data.download

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.download.tiles.asBoundingBoxOfEnclosingTiles
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.area
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinuousMapDownloadControllerTest {

    @Test fun `gates require enabled zoom internet foreground and idle download`() {
        assertTrue(
            shouldAttemptContinuousDownload(
                enabled = true,
                zoom = ContinuousMapDownloadController.MIN_ZOOM,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = true,
                isDownloading = false,
                isForeground = true,
            )
        )
        assertFalse(
            shouldAttemptContinuousDownload(
                enabled = false,
                zoom = 20.0,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = true,
                isDownloading = false,
                isForeground = true,
            )
        )
        assertFalse(
            shouldAttemptContinuousDownload(
                enabled = true,
                zoom = ContinuousMapDownloadController.MIN_ZOOM - 0.1,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = true,
                isDownloading = false,
                isForeground = true,
            )
        )
        assertFalse(
            shouldAttemptContinuousDownload(
                enabled = true,
                zoom = 20.0,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = false,
                isDownloading = false,
                isForeground = true,
            )
        )
        assertFalse(
            shouldAttemptContinuousDownload(
                enabled = true,
                zoom = 20.0,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = true,
                isDownloading = true,
                isForeground = true,
            )
        )
        assertFalse(
            shouldAttemptContinuousDownload(
                enabled = true,
                zoom = 20.0,
                minZoom = ContinuousMapDownloadController.MIN_ZOOM,
                hasInternet = true,
                isDownloading = false,
                isForeground = false,
            )
        )
    }

    @Test fun `small viewport stays tile-aligned without growing past max area`() {
        // ~one z16 tile around Berlin
        val tiny = BoundingBox(52.52, 13.40, 52.521, 13.401)
        val clipped = clipBoundingBoxToMaxArea(tiny, ContinuousMapDownloadController.MAX_AREA_KM2)
        val expected = tiny.asBoundingBoxOfEnclosingTiles(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
        assertEquals(expected, clipped)
        assertTrue(clipped.area() / 1_000_000.0 <= ContinuousMapDownloadController.MAX_AREA_KM2 * 2)
    }

    @Test fun `large viewport is clipped toward center`() {
        val large = BoundingBox(52.4, 13.2, 52.6, 13.6)
        assertTrue(large.area() / 1_000_000.0 > ContinuousMapDownloadController.MAX_AREA_KM2)
        val clipped = clipBoundingBoxToMaxArea(large, ContinuousMapDownloadController.MAX_AREA_KM2)
        // After tile alignment the area can be slightly above the soft max; stay well below the original.
        assertTrue(clipped.area() < large.area())
        val center = LatLon(
            (large.min.latitude + large.max.latitude) / 2.0,
            (large.min.longitude + large.max.longitude) / 2.0,
        )
        val maxRadius = sqrt(ContinuousMapDownloadController.MAX_AREA_KM2 * 1_000_000.0 / PI) * 2.5
        assertTrue(clipped.min.latitude <= center.latitude)
        assertTrue(clipped.max.latitude >= center.latitude)
        assertTrue(clipped.max.latitude - clipped.min.latitude < maxRadius / 111_000.0 * 2)
    }

    @Test fun `defaults match plan`() {
        assertEquals(17.0, ContinuousMapDownloadController.MIN_ZOOM)
        assertEquals(750L, ContinuousMapDownloadController.DEBOUNCE_MS)
        assertEquals(0.25, ContinuousMapDownloadController.MAX_AREA_KM2)
    }
}
