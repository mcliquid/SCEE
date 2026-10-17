package de.westnordost.streetcomplete.overlays.custom

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.util.getIndexedCustomOverlayPref
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CustomOverlayFilterCacheTest {

    private fun filterPref(index: Int) =
        getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_FILTER, index)

    private fun dashPref(index: Int) =
        getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_DASH_FILTER, index)

    @Test fun invalidFilterReturnsNullAndEmptyStyledElements() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "%%% not a filter")
        assertNull(resolveCustomOverlayFilters(prefs))
        assertTrue(CustomOverlay(prefs).getStyledElements(TestMapDataWithGeometry(emptyList())).none())
    }

    @Test fun repeatedResolveUsesCachedFilterExpression() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with amenity = bench")
        val first = resolveCustomOverlayFilters(prefs)!!.filter
        val second = resolveCustomOverlayFilters(prefs)!!.filter
        assertSame(first, second)
    }

    @Test fun changedFilterStringUsesNewExpression() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with amenity = bench")
        val first = resolveCustomOverlayFilters(prefs)!!.filter
        prefs.putString(filterPref(0), "nodes with amenity = cafe")
        val second = resolveCustomOverlayFilters(prefs)!!.filter
        assertNotSame(first, second)
    }

    @Test fun selectedOverlayIndexSwitchUsesSlotFilter() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with amenity = bench")
        prefs.putString(filterPref(1), "nodes with shop = bakery")
        prefs.putInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 0)
        val slot0 = resolveCustomOverlayFilters(prefs)!!.filter
        prefs.putInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 1)
        val slot1 = resolveCustomOverlayFilters(prefs)!!.filter
        assertNotSame(slot0, slot1)
    }

    @Test fun dashFilterUsesCachedWaysWithExpression() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "ways with highway")
        prefs.putString(dashPref(0), "surface = gravel")
        val first = resolveCustomOverlayFilters(prefs)!!.dashFilter
        val second = resolveCustomOverlayFilters(prefs)!!.dashFilter
        assertSame(first, second)
    }

    @Test fun invalidDashFilterIsIgnored() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with amenity = bench")
        prefs.putString(dashPref(0), "(unclosed")
        assertNull(resolveCustomOverlayFilters(prefs)!!.dashFilter)
    }

    @Test fun getStyledElementsMatchesFilter() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with amenity = bench")
        val mapData = TestMapDataWithGeometry(listOf(
            node(1, tags = mapOf("amenity" to "bench")),
            node(2, tags = mapOf("shop" to "bakery")),
        ))
        val ids = CustomOverlay(prefs).getStyledElements(mapData).map { it.first.id }.toList()
        assertEquals(listOf(1L), ids)
    }
}
