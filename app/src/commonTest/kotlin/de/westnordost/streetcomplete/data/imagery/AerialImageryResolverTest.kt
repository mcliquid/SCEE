package de.westnordost.streetcomplete.data.imagery

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.imagery.eli.EliIndex
import de.westnordost.streetcomplete.data.imagery.eli.EliIndexClient
import de.westnordost.streetcomplete.data.imagery.eli.EliIndexParser
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import io.ktor.client.HttpClient
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AerialImageryResolverTest {

    private val sampleIndex = EliIndex(
        EliIndexParser.parse(
            """
            {"type":"FeatureCollection","features":[{
              "type":"Feature",
              "properties":{
                "id":"Region-Best","name":"Region Best","type":"tms","category":"photo","best":true,
                "url":"https://region.example/{zoom}/{x}/{y}.png","max_zoom":19
              },
              "geometry":{"type":"Polygon","coordinates":[[[0,0],[2,0],[2,2],[0,2],[0,0]]]}
            }]}
            """.trimIndent()
        )
    )

    private fun resolver(): Pair<AerialImageryResolver, ManualRasterSourcesStore> {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        val client = EliIndexClient(
            httpClient = HttpClient(),
            prefs = preferences,
            fileSystem = SystemFileSystem,
            cacheDir = Path("build/tmp/eli-test-cache"),
        )
        return AerialImageryResolver(preferences, store, client) to store
    }

    @Test fun `esri default`() {
        val (r, _) = resolver()
        val resolved = r.resolveSelection(AerialSourceSelection.Esri, LatLon(1.0, 1.0), sampleIndex)!!
        assertEquals(AerialSourceSelection.Esri, resolved.effectiveSelection)
        assertTrue(resolved.tiles.single().contains("arcgisonline"))
    }

    @Test fun `auto picks best`() {
        val (r, _) = resolver()
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(1.0, 1.0), sampleIndex)!!
        assertEquals(AerialSourceSelection.Auto, resolved.effectiveSelection)
        assertTrue(resolved.tiles.single().contains("region.example"))
        assertEquals(19, resolved.maxZoom)
    }

    @Test fun `auto falls back to esri outside coverage`() {
        val (r, _) = resolver()
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(50.0, 50.0), sampleIndex)!!
        assertEquals(AerialSourceSelection.Esri, resolved.effectiveSelection)
    }

    @Test fun `manual stays selected regardless of location`() {
        val (r, store) = resolver()
        val id = store.createId()
        store.put(
            ManualRasterSource(
                id = id,
                name = "Custom",
                url = "https://custom.example/{z}/{x}/{y}.png",
                maxZoom = 17,
            )
        )
        val resolved = r.resolveSelection(
            AerialSourceSelection.Manual(id),
            LatLon(50.0, 50.0),
            sampleIndex,
        )!!
        assertEquals(AerialSourceSelection.Manual(id), resolved.effectiveSelection)
        assertEquals(17, resolved.maxZoom)
    }

    @Test fun `missing eli id returns null for fallback`() {
        val (r, _) = resolver()
        assertEquals(
            null,
            r.resolveSelection(AerialSourceSelection.Eli("missing"), LatLon(1.0, 1.0), sampleIndex)
        )
    }

    @Test fun `ensureSelectionValid resets deleted manual`() {
        val (r, store) = resolver()
        val id = store.createId()
        store.put(ManualRasterSource(id, "X", "https://ex.com/{z}/{x}/{y}.png", 18))
        r.setSelection(AerialSourceSelection.Manual(id))
        store.delete(id)
        assertEquals(AerialSourceSelection.Esri, r.currentSelection())
        r.ensureSelectionValid(sampleIndex)
        assertEquals(AerialSourceSelection.Esri, r.currentSelection())
    }

    @Test fun `auto prefers covering preferred manual over eli`() {
        val (r, store) = resolver()
        store.put(
            ManualRasterSource(
                id = "pref-manual",
                name = "Preferred",
                url = "https://preferred.example/{z}/{x}/{y}.png",
                maxZoom = 18,
                preferInAuto = true,
                coverage = ManualRasterCoverage(
                    geoJson = """{"type":"Polygon","coordinates":[[[0,0],[2,0],[2,2],[0,2],[0,0]]]}"""
                ),
            )
        )
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(1.0, 1.0), sampleIndex)!!
        assertEquals(AerialSourceSelection.Auto, resolved.effectiveSelection)
        assertTrue(resolved.tiles.single().contains("preferred.example"))
        assertEquals("Preferred", resolved.displayName)
    }

    @Test fun `auto ignores preferred manual without coverage`() {
        val (r, store) = resolver()
        store.put(
            ManualRasterSource(
                id = "worldwide-manual",
                name = "Worldwide",
                url = "https://worldwide.example/{z}/{x}/{y}.png",
                maxZoom = 18,
                preferInAuto = true,
                coverage = null,
            )
        )
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(1.0, 1.0), sampleIndex)!!
        assertTrue(resolved.tiles.single().contains("region.example"))
    }

    @Test fun `auto ignores preferred manual outside its coverage`() {
        val (r, store) = resolver()
        store.put(
            ManualRasterSource(
                id = "far-manual",
                name = "Far",
                url = "https://far.example/{z}/{x}/{y}.png",
                maxZoom = 18,
                preferInAuto = true,
                coverage = ManualRasterCoverage(minLat = 40.0, minLon = 40.0, maxLat = 41.0, maxLon = 41.0),
            )
        )
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(1.0, 1.0), sampleIndex)!!
        assertTrue(resolved.tiles.single().contains("region.example"))
    }

    @Test fun `auto picks smaller preferred area first`() {
        val (r, store) = resolver()
        store.put(
            ManualRasterSource(
                id = "large",
                name = "Large",
                url = "https://large.example/{z}/{x}/{y}.png",
                maxZoom = 18,
                preferInAuto = true,
                coverage = ManualRasterCoverage(minLat = 0.0, minLon = 0.0, maxLat = 10.0, maxLon = 10.0),
            )
        )
        store.put(
            ManualRasterSource(
                id = "small",
                name = "Small",
                url = "https://small.example/{z}/{x}/{y}.png",
                maxZoom = 18,
                preferInAuto = true,
                coverage = ManualRasterCoverage(minLat = 0.0, minLon = 0.0, maxLat = 2.0, maxLon = 2.0),
            )
        )
        val resolved = r.resolveSelection(AerialSourceSelection.Auto, LatLon(1.0, 1.0), sampleIndex)!!
        assertTrue(resolved.tiles.single().contains("small.example"))
    }

    @Test fun `explicit manual works outside coverage`() {
        val (r, store) = resolver()
        store.put(
            ManualRasterSource(
                id = "regional",
                name = "Regional",
                url = "https://regional.example/{z}/{x}/{y}.png",
                maxZoom = 16,
                preferInAuto = true,
                coverage = ManualRasterCoverage(minLat = 0.0, minLon = 0.0, maxLat = 1.0, maxLon = 1.0),
            )
        )
        val resolved = r.resolveSelection(
            AerialSourceSelection.Manual("regional"),
            LatLon(50.0, 50.0),
            sampleIndex,
        )!!
        assertEquals(AerialSourceSelection.Manual("regional"), resolved.effectiveSelection)
    }

    @Test fun `selecting aerial source sets theme background aerial`() {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        val client = EliIndexClient(
            httpClient = HttpClient(),
            prefs = preferences,
            fileSystem = SystemFileSystem,
            cacheDir = Path("build/tmp/eli-test-cache"),
        )
        val resolver = AerialImageryResolver(preferences, store, client)
        preferences.putString(Prefs.THEME_BACKGROUND, "MAP")
        de.westnordost.streetcomplete.screens.settings.applyBackgroundChoice(
            de.westnordost.streetcomplete.screens.settings.BackgroundChoice.Auto,
            preferences,
            resolver,
        )
        assertEquals("AERIAL", preferences.getString(Prefs.THEME_BACKGROUND, "MAP"))
        assertEquals(AerialSourceSelection.Auto, resolver.currentSelection())
    }
}
