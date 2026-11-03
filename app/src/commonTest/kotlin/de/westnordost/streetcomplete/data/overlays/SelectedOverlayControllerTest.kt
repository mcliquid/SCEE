package de.westnordost.streetcomplete.data.overlays

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.overlays.overlaysRegistry
import de.westnordost.streetcomplete.overlays.way_lit.WayLitOverlay
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.util.getFakeCustomOverlays
import de.westnordost.streetcomplete.util.getIndexedCustomOverlayPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SelectedOverlayControllerTest {

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    private fun registry(): OverlayRegistry {
        Prefs.preferences = inMemoryPrefs()
        return overlaysRegistry(
            getCountryInfoByLocation = { countryInfo },
            getCountryCodeByLocation = { null },
            getFeature = { null },
            prefs = Prefs.preferences,
        )
    }

    private fun controller(registry: OverlayRegistry = registry()): SelectedOverlayController =
        SelectedOverlayController(Prefs.preferences, registry)

    @Test fun `selecting null clears persisted overlay`() {
        val registry = registry()
        val ctrl = controller(registry)
        ctrl.selectedOverlay = registry.first { it.name == "WayLitOverlay" }
        ctrl.selectedOverlay = null
        assertNull(Prefs.preferences.selectedOverlayName)
        assertNull(ctrl.selectedOverlay)
    }

    @Test fun `selecting registry overlay persists and restores canonical instance`() {
        val registry = registry()
        val ctrl = controller(registry)
        val wayLit = registry.first { it.name == "WayLitOverlay" }
        ctrl.selectedOverlay = wayLit
        assertEquals("WayLitOverlay", Prefs.preferences.selectedOverlayName)
        assertSame(wayLit, ctrl.selectedOverlay)
    }

    @Test fun `selecting overlay by name works without registry instance identity`() {
        val registry = registry()
        val ctrl = controller(registry)
        val canonical = registry.first { it.name == "WayLitOverlay" }
        ctrl.selectedOverlay = WayLitOverlay()
        assertEquals("WayLitOverlay", Prefs.preferences.selectedOverlayName)
        assertSame(canonical, ctrl.selectedOverlay)
    }

    @Test fun `legacy ShopsOverlay preference resolves to PlacesOverlay`() {
        val registry = registry()
        val prefs = Prefs.preferences
        prefs.selectedOverlayName = "ShopsOverlay"
        val ctrl = controller(registry)
        val places = registry.first { it.name == "PlacesOverlay" }
        assertSame(places, ctrl.selectedOverlay)
        assertEquals("PlacesOverlay", prefs.selectedOverlayName)
    }

    @Test fun `switching between overlays updates active overlay`() {
        val registry = registry()
        val ctrl = controller(registry)
        val wayLit = registry.first { it.name == "WayLitOverlay" }
        val surface = registry.first { it.name == "SurfaceOverlay" }
        ctrl.selectedOverlay = wayLit
        ctrl.selectedOverlay = surface
        assertEquals("SurfaceOverlay", Prefs.preferences.selectedOverlayName)
        assertSame(surface, ctrl.selectedOverlay)
    }

    @Test fun `persisted selection is restored after controller recreation`() {
        val registry = registry()
        val prefs = Prefs.preferences
        prefs.selectedOverlayName = "CyclewayOverlay"
        val ctrl = controller(registry)
        assertSame(registry.first { it.name == "CyclewayOverlay" }, ctrl.selectedOverlay)
    }

    @Test fun `persisted unknown overlay name restores as null`() {
        val ctrl = controller()
        Prefs.preferences.selectedOverlayName = "NotARegisteredOverlay"
        assertNull(ctrl.selectedOverlay)
    }

    @Test fun `selecting fake custom overlay stores CustomOverlay and index`() {
        val registry = registry()
        val prefs = Prefs.preferences
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, "2")
        prefs.putString(getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_FILTER, 2), "nodes with highway=primary")
        prefs.putString(getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_NAME, 2), "Test overlay")
        val ctrl = controller(registry)
        val fake = getFakeCustomOverlays(prefs, onlyIfExpertMode = false).single { it.wikiLink == "2" }
        ctrl.selectedOverlay = fake
        assertEquals("CustomOverlay", prefs.selectedOverlayName)
        assertEquals(2, prefs.getInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, -1))
        assertSame(registry.first { it.name == "CustomOverlay" }, ctrl.selectedOverlay)
    }
}

class OverlaySelectionTest {

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    @Test fun `isSameSelectedOverlay matches by name not reference`() {
        Prefs.preferences = inMemoryPrefs()
        val registry = overlaysRegistry(
            getCountryInfoByLocation = { countryInfo },
            getCountryCodeByLocation = { null },
            getFeature = { null },
            prefs = Prefs.preferences,
        )
        val places = registry.first { it.name == "PlacesOverlay" }
        val detached = de.westnordost.streetcomplete.overlays.places.PlacesOverlay { null }
        assertTrue(isSameSelectedOverlay(places, detached, Prefs.preferences))
        assertFalse(isSameSelectedOverlay(
            registry.first { it.name == "WayLitOverlay" },
            detached,
            Prefs.preferences,
        ))
    }
}
