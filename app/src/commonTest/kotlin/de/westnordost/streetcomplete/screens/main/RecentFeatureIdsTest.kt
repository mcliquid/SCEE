package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentFeatureIdsTest {
    private val key = Prefs.CREATE_POI_RECENT_FEATURE_IDS
    private val fallback = listOf("a", "b")

    @Test fun `fallback list if nothing stored`() {
        assertEquals(fallback, recentFeatureIds(inMemoryPrefs(), key, fallback))
    }

    @Test fun `stored ids are returned oldest first and replace the fallback`() {
        val prefs = inMemoryPrefs()
        addRecentFeatureId(prefs, key, "x")
        addRecentFeatureId(prefs, key, "y")
        assertEquals(listOf("x", "y"), recentFeatureIds(prefs, key, fallback))
        assertEquals(listOf("y", "x"), recentFeatureIds(prefs, key, fallback).reversed())
    }

    @Test fun `reusing a feature moves it to the end without duplicate`() {
        val prefs = inMemoryPrefs()
        listOf("x", "y", "z", "x").forEach { addRecentFeatureId(prefs, key, it) }
        assertEquals(listOf("y", "z", "x"), recentFeatureIds(prefs, key, fallback))
    }

    @Test fun `adding the latest feature again changes nothing`() {
        val prefs = inMemoryPrefs()
        addRecentFeatureId(prefs, key, "x")
        addRecentFeatureId(prefs, key, "x")
        assertEquals(listOf("x"), recentFeatureIds(prefs, key, fallback))
    }

    @Test fun `only the last 35 are kept`() {
        val prefs = inMemoryPrefs()
        (1..40).forEach { addRecentFeatureId(prefs, key, "f$it") }
        val ids = recentFeatureIds(prefs, key, fallback)
        assertEquals(35, ids.size)
        assertEquals("f6", ids.first())
        assertEquals("f40", ids.last())
    }

    @Test fun `excluded ids are skipped and fall back if nothing is left`() {
        val prefs = inMemoryPrefs()
        addRecentFeatureId(prefs, key, "shop")
        addRecentFeatureId(prefs, key, "x")
        assertEquals(listOf("x"), recentFeatureIds(prefs, key, fallback, exclude = setOf("shop")))
        val onlyShop = inMemoryPrefs()
        addRecentFeatureId(onlyShop, key, "shop")
        assertEquals(fallback, recentFeatureIds(onlyShop, key, fallback, exclude = setOf("shop")))
    }

    @Test fun `create poi and insert node lists are independent`() {
        val prefs = inMemoryPrefs()
        addRecentFeatureId(prefs, Prefs.CREATE_POI_RECENT_FEATURE_IDS, "poi")
        assertEquals(fallback, recentFeatureIds(prefs, Prefs.INSERT_NODE_RECENT_FEATURE_IDS, fallback))
    }
}
