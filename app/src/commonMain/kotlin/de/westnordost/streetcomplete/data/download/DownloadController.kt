package de.westnordost.streetcomplete.data.download

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

/** Controls downloading */
interface DownloadController {
    /** Download in at least the given bounding box asynchronously. The next-bigger rectangle
     * in a (z16) tiles grid that encloses the given bounding box will be downloaded.
     *
     * @param bbox the minimum area to download
     * @param isUserInitiated whether this shall be a priority download (cancels previous downloads
     *        and puts itself in the front)
     */
    fun download(bbox: BoundingBox, isUserInitiated: Boolean = false, enqueue: Boolean = false)

    /** Import a GPX track and enqueue corridor downloads; not tied to UI composition scope. */
    fun downloadAlongGpxTrack(trackPoints: List<LatLon>, minDownloadDistance: Double = 10.0)
}
