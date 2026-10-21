package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import io.ticofab.androidgpxparser.parser.GPXParser

internal actual fun parseGpxTrackPoints(gpx: String): List<LatLon> =
    GPXParser().parse(gpx.byteInputStream()).tracks.map { track ->
        track.trackSegments.map { segment ->
            segment.trackPoints
        }
    }.flatten().flatten()
        .map { trackPoint ->
            LatLon(
                latitude = trackPoint.latitude,
                longitude = trackPoint.longitude
            )
        }
