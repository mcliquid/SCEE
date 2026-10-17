package de.westnordost.streetcomplete.osm.traffic_sign

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrafficSignSearchTest {
    private val catalogue = TrafficSignTestData.catalogueDE

    @Test fun `search by number finds exact match first`() {
        val result = catalogue.search("237", TrafficSignPickerKind.MAIN)
        assertEquals("237", result.first().osmValuePart)
    }

    @Test fun `search by description`() {
        val result = catalogue.search("radweg", TrafficSignPickerKind.MAIN)
        assertTrue(result.any { it.osmValuePart == "237" })
    }

    @Test fun `search is case-insensitive and trims`() {
        assertEquals(
            catalogue.search("radweg", TrafficSignPickerKind.MAIN),
            catalogue.search("  RADWEG ", TrafficSignPickerKind.MAIN)
        )
    }

    @Test fun `main and modifier signs are separate`() {
        val main = catalogue.search("", TrafficSignPickerKind.MAIN)
        val modifiers = catalogue.search("", TrafficSignPickerKind.MODIFIER)
        assertTrue(main.none { it.isModifier })
        assertTrue(modifiers.all { it.isModifier })
        assertEquals(catalogue.signs.size, main.size + modifiers.size)
    }

    @Test fun `every sign can be found by its own number`() {
        for (sign in catalogue.signs) {
            val kind = if (sign.isModifier) TrafficSignPickerKind.MODIFIER else TrafficSignPickerKind.MAIN
            val result = catalogue.search(sign.osmValuePart, kind, showAll = true)
            assertTrue(sign in result, "${sign.osmValuePart} not found")
        }
    }

    @Test fun `category filter`() {
        val result = catalogue.search("", TrafficSignPickerKind.MAIN, category = "speed")
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it.category == "speed" })
    }

    @Test fun `categories are listed per kind`() {
        assertTrue("speed" in catalogue.categories(TrafficSignPickerKind.MAIN))
        assertFalse("speed" in catalogue.categories(TrafficSignPickerKind.MODIFIER))
    }

    @Test fun `incompatible modifiers are hidden unless all are shown`() {
        val main = catalogue.getByOsmValuePart("237")!!
        val bicycleFree = catalogue.getByOsmValuePart("1022-10")!!
        assertFalse(bicycleFree.isCompatibleModifierOf(main))
        assertFalse(bicycleFree in catalogue.search("", TrafficSignPickerKind.MODIFIER, main = main))
        assertTrue(bicycleFree in catalogue.search("", TrafficSignPickerKind.MODIFIER, main = main, showAll = true))
    }

    @Test fun `without main sign every modifier is compatible`() {
        assertTrue(catalogue.signs.filter { it.isModifier }.all { it.isCompatibleModifierOf(null) })
    }

    @Test fun `signs with recommended way tags come first among equal matches`() {
        val result = catalogue.search("", TrafficSignPickerKind.MAIN)
        val firstWithout = result.indexOfFirst { !it.hasWayRecommendation }
        assertTrue(result.drop(firstWithout).none { it.hasWayRecommendation })
    }

    @Test fun `nothing found`() {
        assertEquals(emptyList(), catalogue.search("zzzzzz", TrafficSignPickerKind.MAIN))
    }
}
