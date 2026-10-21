package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals

/** Desktop only: the Android parser needs the real XML pull parser, which plain host tests do not have */
class LoadValidGpxTrackPointsTest {
    private val gpx = """<?xml version="1.0" encoding="UTF-8"?>
        <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
          <trk><trkseg>
            <trkpt lat="52.0" lon="13.0"/>
            <trkpt lat="52.1" lon="13.1"/>
          </trkseg></trk>
        </gpx>"""

    @Test fun `a valid track is loaded without error`() {
        var errors = 0
        val points = loadGpxTrackPoints({ errors++ }, false) { gpx }
        assertEquals(listOf(LatLon(52.0, 13.0), LatLon(52.1, 13.1)), points)
        assertEquals(0, errors)
    }
}
