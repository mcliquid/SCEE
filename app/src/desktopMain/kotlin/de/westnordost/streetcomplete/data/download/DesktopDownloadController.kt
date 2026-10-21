package de.westnordost.streetcomplete.data.download

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.enqueueGpxTrackDownloads
import de.westnordost.streetcomplete.data.gpxTrackDownloadBBoxes
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

// TODO desktop: (On Linux) we might want to use the dedicated D-Bus API
//  https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.Background.html
//  to request continuing the download to run in the background

class DesktopDownloadController(
    private val downloader: Downloader,
    private val prefs: ObservableSettings,
    coroutineContext: CoroutineContext = Dispatchers.Main,
) : DownloadController {
    // Downloader already logs errors and notifies its listeners
    private val scope = CoroutineScope(
        coroutineContext + SupervisorJob() + CoroutineExceptionHandler { _, _ -> }
    )
    // A second replacement must also wait for the first download's cancellation cleanup.
    private val requests = Mutex()
    private val queue = ArrayDeque<BoundingBox>()
    private var job: Job? = null

    override fun download(bbox: BoundingBox, isUserInitiated: Boolean, enqueue: Boolean) {
        scope.launch {
            requests.withLock {
                val previous = job
                if (previous?.isActive == true) {
                    if (enqueue) {
                        queue.addLast(bbox)
                        return@withLock
                    }
                    if (!isUserInitiated) return@withLock
                }
                previous?.cancelAndJoin()
                job = scope.launch {
                    var next = bbox
                    var userInitiated = isUserInitiated
                    while (true) {
                        // Keep the queue alive after errors; Downloader logs and notifies listeners.
                        supervisorScope {
                            launch {
                                downloader.download(next, userInitiated,
                                    prefs.getBoolean(Prefs.MANUAL_DOWNLOAD_OVERRIDE_CACHE, true))
                            }.join()
                        }
                        // Taking the next request and ending the job happen under the lock, so a request that is enqueued
                        // meanwhile either is seen here or finds no running job and starts its own.
                        next = requests.withLock { queue.removeFirstOrNull().also { if (it == null) job = null } } ?: break
                        // Android also promotes queued follow-ups to user-initiated downloads.
                        userInitiated = true
                    }
                }
            }
        }
    }

    override fun downloadAlongGpxTrack(trackPoints: List<LatLon>, minDownloadDistance: Double) {
        scope.launch {
            val bboxes = gpxTrackDownloadBBoxes(trackPoints, minDownloadDistance)
            enqueueGpxTrackDownloads(this@DesktopDownloadController, bboxes)
        }
    }

    fun close() { scope.cancel() }
}
