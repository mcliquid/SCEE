package de.westnordost.streetcomplete.data.download

import de.westnordost.streetcomplete.data.enqueueGpxTrackDownloads
import de.westnordost.streetcomplete.data.gpxTrackDownloadBBoxes
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.sync.IosSyncJob

class IosDownloadController(private val downloader: Downloader) : DownloadController {
    private val job = IosSyncJob(Downloader.TAG)
    private val gpxJob = IosSyncJob("GpxTrackDownload")

    override fun download(bbox: BoundingBox, isUserInitiated: Boolean, enqueue: Boolean) {
        job.launch(replace = isUserInitiated) { downloader.download(bbox, isUserInitiated) }
    }

    override fun downloadAlongGpxTrack(trackPoints: List<LatLon>, minDownloadDistance: Double) {
        gpxJob.launch {
            val bboxes = gpxTrackDownloadBBoxes(trackPoints, minDownloadDistance)
            enqueueGpxTrackDownloads(this@IosDownloadController, bboxes)
        }
    }

    fun close() {
        gpxJob.close()
        job.close()
    }
}
