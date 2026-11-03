package de.westnordost.streetcomplete.overlays

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

class OverlaysRegistryTest {

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    @Test
    fun `overlay registry initializes with unique type names`() {
        Prefs.preferences = inMemoryPrefs()
        val registry = overlaysRegistry(
            getCountryInfoByLocation = { countryInfo },
            getCountryCodeByLocation = { null },
            getFeature = { null },
            prefs = Prefs.preferences,
        )
        val names = registry.map { it.name }
        val duplicates = names.groupBy { it }.filter { it.value.size > 1 }.keys.sorted()
        assertEquals(emptyList(), duplicates, "Duplicate overlay names: $duplicates")
        // sanity: registry non-empty
        assert(registry.isNotEmpty())
    }
}
