package de.westnordost.streetcomplete.data.imagery

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualRasterSourcesStoreTest {

    @Test fun `migrates legacy single url`() {
        val settings = MapSettings()
        settings.putString(Prefs.RASTER_TILE_URL, "https://ex.com/{z}/{x}/{y}.png")
        settings.putInt(Prefs.RASTER_TILE_MAXZOOM, 18)
        settings.putBoolean(Prefs.NO_SATELLITE_LABEL, true)
        val preferences = Preferences(settings)
        val store = ManualRasterSourcesStore(preferences)
        val all = store.getAll()
        assertTrue(all.any { it.url == "https://ex.com/{z}/{x}/{y}.png" })
        val migrated = all.single { it.url == "https://ex.com/{z}/{x}/{y}.png" }
        assertEquals(18, migrated.maxZoom)
        assertTrue(migrated.hideLabels)
        assertEquals(ManualRasterSourcesStore.DEFAULT_MIGRATED_NAME, migrated.name)
        assertTrue(!preferences.contains(Prefs.RASTER_TILE_URL))
        assertEquals(
            AerialSourceSelection.Manual(migrated.id),
            AerialSourceSelection.decode(preferences.getString(Prefs.AERIAL_SOURCE, ""))
        )
        assertTrue(all.any { it.id == BuiltinManualRasterSources.BAYERN_ID })
        assertTrue(all.any { it.id == BuiltinManualRasterSources.BW_ID })
    }

    @Test fun `seeds builtins when empty`() {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        val all = store.getAll()
        assertEquals(2, all.size)
        assertTrue(preferences.contains(Prefs.MANUAL_RASTER_SOURCES))
        assertNotNull(store.get(BuiltinManualRasterSources.BAYERN_ID)?.coverage?.geoJson)
        assertTrue(store.get(BuiltinManualRasterSources.BAYERN_ID)!!.preferInAuto)
        assertTrue(store.get(BuiltinManualRasterSources.BW_ID)!!.preferInAuto)
    }

    @Test fun `put update and delete with fallback`() {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        val id = store.createId()
        val source = ManualRasterSource(
            id = id,
            name = "VoGIS",
            url = "https://ex.com/?BBOX={bbox-epsg-3857}",
            maxZoom = ApplicationConstants.RASTER_DEFAULT_MAXZOOM,
        )
        store.put(source)
        preferences.putString(Prefs.AERIAL_SOURCE, AerialSourceSelection.Manual(id).encode())
        store.put(source.copy(name = "VoGIS 2023"))
        assertEquals("VoGIS 2023", store.get(id)?.name)
        assertTrue(store.delete(id))
        assertNull(store.get(id))
        assertEquals(
            AerialSourceSelection.Esri,
            AerialSourceSelection.decode(preferences.getString(Prefs.AERIAL_SOURCE, ""))
        )
    }

    @Test fun `migrates json without new fields`() {
        val settings = MapSettings()
        settings.putString(
            Prefs.MANUAL_RASTER_SOURCES,
            """[{"id":"old","name":"Old","url":"https://old.example/{z}/{x}/{y}.png","maxZoom":17}]"""
        )
        val store = ManualRasterSourcesStore(Preferences(settings))
        val old = store.get("old")!!
        assertEquals(false, old.preferInAuto)
        assertNull(old.coverage)
        assertEquals("Old", old.name)
        // builtins still seeded
        assertNotNull(store.get(BuiltinManualRasterSources.BAYERN_ID))
    }

    @Test fun `does not overwrite edited builtin`() {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        val edited = store.get(BuiltinManualRasterSources.BAYERN_ID)!!.copy(name = "My Bayern")
        store.put(edited)
        store.ensureBuiltins()
        assertEquals("My Bayern", store.get(BuiltinManualRasterSources.BAYERN_ID)?.name)
    }

    @Test fun `deleted builtin is not reseeded`() {
        val preferences = Preferences(MapSettings())
        val store = ManualRasterSourcesStore(preferences)
        assertTrue(store.delete(BuiltinManualRasterSources.BAYERN_ID))
        store.ensureBuiltins()
        assertNull(store.get(BuiltinManualRasterSources.BAYERN_ID))
        assertNotNull(store.get(BuiltinManualRasterSources.BW_ID))
    }
}
