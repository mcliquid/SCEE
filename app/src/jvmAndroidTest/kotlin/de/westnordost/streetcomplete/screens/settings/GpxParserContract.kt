package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** Same vectors on the Android library parser and the desktop parser. */
abstract class GpxParserContract {
    private fun gpx(body: String) =
        """<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1">$body</gpx>"""

    @Test fun `points of all segments of all tracks are returned in file order`() {
        val points = parseGpxTrackPoints(gpx("""
            <trk><name>a</name><trkseg>
              <trkpt lat="50.1" lon="8.2"><ele>100</ele><time>2020-01-01T00:00:00Z</time></trkpt>
              <trkpt lon="8.3" lat="50.2"/>
            </trkseg><trkseg><trkpt lat="-33.5" lon="-70.25"/></trkseg></trk>
            <trk><trkseg><trkpt lat="0" lon="0"/></trkseg></trk>
        """))
        assertEquals(listOf(LatLon(50.1, 8.2), LatLon(50.2, 8.3), LatLon(-33.5, -70.25), LatLon(0.0, 0.0)), points)
    }

    @Test fun `waypoints and routes are not part of the track`() {
        val points = parseGpxTrackPoints(gpx("""
            <wpt lat="1" lon="1"/><rte><rtept lat="2" lon="2"/></rte>
            <trk><trkseg><trkpt lat="3" lon="3"/></trkseg></trk>
        """))
        assertEquals(listOf(LatLon(3.0, 3.0)), points)
    }

    @Test fun `file without tracks has no points`() {
        assertEquals(emptyList(), parseGpxTrackPoints(gpx("<wpt lat=\"1\" lon=\"1\"/>")))
    }

    @Test fun `unicode in names is no problem`() {
        val points = parseGpxTrackPoints(gpx("<trk><name>Straße 🌍</name><trkseg><trkpt lat=\"1.5\" lon=\"2.5\"/></trkseg></trk>"))
        assertEquals(listOf(LatLon(1.5, 2.5)), points)
    }

    @Test fun `text that is not xml is an error`() {
        assertFails { parseGpxTrackPoints("this is not gpx") }
        assertFails { parseGpxTrackPoints(gpx("<trk><trkseg><trkpt lat=\"1\" lon=\"1\">")) }
    }
}
