package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The GPX track and the custom geometry are read again every time the main screen is composed */
class ReloadMapExtrasTest {
    private val track1 = listOf(LatLon(1.0, 1.0), LatLon(2.0, 2.0))
    private val track2 = listOf(LatLon(3.0, 3.0), LatLon(4.0, 4.0))

    private var gpx: List<LatLon>? = track1
    private var geometry: String? = "geo1"
    private var gpxLoads = 0
    private var geometryLoads = 0

    private fun reload(prefs: de.westnordost.streetcomplete.data.preferences.Preferences): Pair<List<LatLon>, String?> {
        var result: Pair<List<LatLon>, String?>? = null
        runBlocking {
            reloadMapExtras(prefs, { gpxLoads++; gpx }, { geometryLoads++; geometry }) { t, g -> result = t to g }
        }
        return result!!
    }

    private fun prefs(showGpx: Boolean, showGeometry: Boolean) = inMemoryPrefs().also {
        it.putBoolean(Prefs.SHOW_GPX_TRACK, showGpx)
        it.putBoolean(Prefs.SHOW_CUSTOM_GEOMETRY, showGeometry)
    }

    @Test fun `nothing is shown or even read by default`() {
        assertEquals(emptyList<LatLon>() to null, reload(inMemoryPrefs()))
        assertEquals(0, gpxLoads)
        assertEquals(0, geometryLoads)
    }

    @Test fun `track and geometry are shown if enabled`() {
        assertEquals(track1 to "geo1", reload(prefs(showGpx = true, showGeometry = true)))
    }

    @Test fun `each one is independent`() {
        assertEquals(track1 to null, reload(prefs(showGpx = true, showGeometry = false)))
        assertEquals(emptyList<LatLon>() to "geo1", reload(prefs(showGpx = false, showGeometry = true)))
    }

    @Test fun `a replaced file is picked up by the next reload`() {
        val prefs = prefs(true, true)
        assertEquals(track1 to "geo1", reload(prefs))
        gpx = track2
        geometry = "geo2"
        assertEquals(track2 to "geo2", reload(prefs))
    }

    @Test fun `a removed or unreadable file clears what was shown`() {
        val prefs = prefs(true, true)
        reload(prefs)
        gpx = null
        geometry = null
        assertEquals(emptyList<LatLon>() to null, reload(prefs))
    }

    @Test fun `switching the setting off removes the data on the next reload`() {
        val prefs = prefs(true, true)
        assertEquals(track1 to "geo1", reload(prefs))
        prefs.putBoolean(Prefs.SHOW_GPX_TRACK, false)
        prefs.putBoolean(Prefs.SHOW_CUSTOM_GEOMETRY, false)
        assertEquals(emptyList<LatLon>() to null, reload(prefs))
        assertNull(reload(prefs).second)
    }
}
