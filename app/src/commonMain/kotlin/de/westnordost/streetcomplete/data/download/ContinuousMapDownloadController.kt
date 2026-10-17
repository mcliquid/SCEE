package de.westnordost.streetcomplete.data.download

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.connection.ActiveNetworkConnection
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.download.tiles.asBoundingBoxOfEnclosingTiles
import de.westnordost.streetcomplete.data.download.tiles.enclosingTilesRect
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.math.area
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Optionally downloads OSM data for the visible map after the camera has been idle.
 * Independent of GPS-based [de.westnordost.streetcomplete.data.quest.AutoSyncer] / [Prefs.AUTO_DOWNLOAD].
 */
class ContinuousMapDownloadController(
    private val prefs: Preferences,
    private val downloadController: DownloadController,
    private val downloadProgressSource: DownloadProgressSource,
    private val downloadedTilesSource: DownloadedTilesSource,
    private val activeNetworkConnection: ActiveNetworkConnection,
) {
    private val scope = CoroutineScope(SupervisorJob() + CoroutineName(TAG))
    private var debounceJob: Job? = null
    private var hasInternet: Boolean = false
    private var isForeground: Boolean = false

    val isEnabled: Boolean get() = prefs.getBoolean(Prefs.CONTINUOUS_MAP_DOWNLOAD, false)

    init {
        scope.launch {
            activeNetworkConnection.capabilities.collect { capabilities ->
                hasInternet = capabilities?.hasInternet == true
            }
        }
    }

    fun setForeground(foreground: Boolean) {
        isForeground = foreground
        if (!foreground) {
            debounceJob?.cancel()
            debounceJob = null
        }
    }

    /** Call on every camera/viewport change. Debounces and may start one download. */
    fun onViewportChanged(zoom: Double, bbox: BoundingBox?) {
        debounceJob?.cancel()
        if (!isEnabled || !isForeground || bbox == null) return
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            tryDownload(zoom, bbox)
        }
    }

    fun onClear() {
        debounceJob?.cancel()
        scope.coroutineContext.cancelChildren()
    }

    private suspend fun tryDownload(zoom: Double, viewport: BoundingBox) {
        if (!shouldAttemptContinuousDownload(
                enabled = isEnabled,
                zoom = zoom,
                minZoom = MIN_ZOOM,
                hasInternet = hasInternet,
                isDownloading = downloadProgressSource.isDownloadInProgress,
                isForeground = isForeground,
            )
        ) {
            return
        }

        val downloadBBox = clipBoundingBoxToMaxArea(viewport, MAX_AREA_KM2)
        val tiles = downloadBBox.enclosingTilesRect(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
        val ignoreOlderThan = max(0L, nowAsEpochMilliseconds() - ApplicationConstants.REFRESH_DATA_AFTER)
        val alreadyFresh = withContext(Dispatchers.IO) {
            downloadedTilesSource.contains(tiles, ignoreOlderThan)
        }
        if (alreadyFresh) {
            Log.i(TAG, "Skip continuous download: tiles still fresh")
            return
        }
        if (downloadProgressSource.isDownloadInProgress) return

        Log.i(TAG, "Starting continuous download at zoom $zoom")
        try {
            downloadController.download(downloadBBox, isUserInitiated = false, enqueue = false)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Cannot start continuous download", e)
        }
    }

    companion object {
        private const val TAG = "ContinuousMapDownload"
        const val MIN_ZOOM = 17.0
        const val DEBOUNCE_MS = 750L
        const val MAX_AREA_KM2 = 0.25
    }
}

fun shouldAttemptContinuousDownload(
    enabled: Boolean,
    zoom: Double,
    minZoom: Double,
    hasInternet: Boolean,
    isDownloading: Boolean,
    isForeground: Boolean,
): Boolean =
    enabled && isForeground && hasInternet && !isDownloading && zoom >= minZoom

/**
 * Aligns [viewport] to download tiles and shrinks around the center when larger than [maxAreaKm2].
 */
fun clipBoundingBoxToMaxArea(viewport: BoundingBox, maxAreaKm2: Double): BoundingBox {
    val tileAligned = viewport.asBoundingBoxOfEnclosingTiles(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
    val areaKm2 = tileAligned.area() / 1_000_000.0
    if (areaKm2 <= maxAreaKm2) return tileAligned
    val center = LatLon(
        (tileAligned.min.latitude + tileAligned.max.latitude) / 2.0,
        (tileAligned.min.longitude + tileAligned.max.longitude) / 2.0,
    )
    val radiusMeters = sqrt(maxAreaKm2 * 1_000_000.0 / PI)
    return center.enclosingBoundingBox(radiusMeters)
        .asBoundingBoxOfEnclosingTiles(ApplicationConstants.DOWNLOAD_TILE_ZOOM)
}
