package de.westnordost.streetcomplete.screens.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoadGpxTrackPointsTest {
    private var errors = 0

    private fun load(complain: Boolean = false, read: suspend () -> String?) =
        loadGpxTrackPoints({ errors++ }, complain, read)

    @Test fun `a missing file is not an error for the map but is one in the settings`() {
        assertNull(load { null })
        assertEquals(0, errors)
        assertNull(load(complain = true) { null })
        assertEquals(1, errors)
    }

    @Test fun `text that is not gpx is reported once`() {
        assertNull(load { "this is not xml" })
        assertEquals(1, errors)
    }

    @Test fun `broken xml is reported once`() {
        assertNull(load { "<gpx><trk><trkseg><trkpt lat=\"1\"" })
        assertEquals(1, errors)
    }

    @Test fun `a track with fewer than two points is reported once`() {
        val one = """<gpx version="1.1" creator="t" xmlns="http://www.topografix.com/GPX/1/1">
            <trk><trkseg><trkpt lat="52.0" lon="13.0"/></trkseg></trk></gpx>"""
        assertNull(load { one })
        assertEquals(1, errors)
    }

    @Test fun `an empty file is reported once`() {
        assertNull(load { "" })
        assertEquals(1, errors)
    }

    @Test fun `a file that cannot be read is reported once`() {
        assertNull(load { throw IllegalStateException("io") })
        assertEquals(1, errors)
    }
}
