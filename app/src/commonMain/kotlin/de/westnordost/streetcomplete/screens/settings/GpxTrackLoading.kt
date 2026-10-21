package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlinx.coroutines.runBlocking

/**
 * Loads the user's GPX track as one long track, no matter how it is stored internally (for now).
 * [readGpx] returns the file content, or null if there is no file.
 * [onError] is called once if the file cannot be read or has fewer than two points, and also for a
 * missing file if [complain] is set.
 */
fun loadGpxTrackPoints(
    onError: () -> Unit,
    complain: Boolean,
    readGpx: suspend () -> String?,
): List<LatLon>? {
    val text = try {
        runBlocking { readGpx() }
    } catch (_: Exception) {
        onError()
        return null
    }
    if (text == null) {
        if (complain) onError()
        return null
    }

    val gpxPoints = runCatching { parseGpxTrackPoints(text) }.getOrNull()
    if ((gpxPoints?.size ?: 0) < 2) {
        onError()
        return null
    }
    return gpxPoints
}
