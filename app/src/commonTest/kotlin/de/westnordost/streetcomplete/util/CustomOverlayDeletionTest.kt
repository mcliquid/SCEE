package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomOverlayDeletionTest {

    private fun filterPref(index: Int) =
        getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_FILTER, index)

    private fun namePref(index: Int) =
        getIndexedCustomOverlayPref(Prefs.CUSTOM_OVERLAY_IDX_NAME, index)

    private fun writeOverlay(prefs: de.westnordost.streetcomplete.data.preferences.Preferences, index: Int, name: String) {
        prefs.putString(filterPref(index), "nodes with highway=primary")
        prefs.putString(namePref(index), name)
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, index.toString())
    }

    @Test fun createAndDeleteRemovesDefinitionAndConfiguration() {
        val prefs = inMemoryPrefs()
        writeOverlay(prefs, 3, "Mine")
        deleteCustomOverlay(prefs, 3)
        assertFalse(prefs.contains(filterPref(3)))
        assertFalse(prefs.contains(namePref(3)))
        assertFalse(prefs.contains(Prefs.CUSTOM_OVERLAY_INDICES))
        assertEquals(emptyList(), getCustomOverlayIndices(prefs))
        assertEquals(emptyList(), getFakeCustomOverlays(prefs, onlyIfExpertMode = false))
    }

    @Test fun deletingInactiveOverlayLeavesOthers() {
        val prefs = inMemoryPrefs()
        writeOverlay(prefs, 1, "A")
        prefs.putString(filterPref(2), "ways with highway=secondary")
        prefs.putString(namePref(2), "B")
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, "1,2")
        deleteCustomOverlay(prefs, 2)
        assertEquals(listOf(1), getCustomOverlayIndices(prefs))
        assertTrue(prefs.contains(filterPref(1)))
        assertFalse(prefs.contains(filterPref(2)))
        assertEquals(1, getFakeCustomOverlays(prefs, onlyIfExpertMode = false).size)
    }

    @Test fun deletingActiveOverlayClearsSelection() {
        val prefs = inMemoryPrefs()
        writeOverlay(prefs, 4, "Active")
        prefs.putInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 4)
        prefs.selectedOverlayName = "CustomOverlay"
        val deletion = deleteCustomOverlay(prefs, 4)
        assertTrue(deletion.wasSelected)
        assertNull(prefs.selectedOverlayName)
        assertEquals(emptyList(), getCustomOverlayIndices(prefs))
    }

    @Test fun deletingInactiveOverlayDoesNotClearSelection() {
        val prefs = inMemoryPrefs()
        writeOverlay(prefs, 1, "A")
        prefs.putString(filterPref(2), "nodes with amenity=bicycle_parking")
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, "1,2")
        prefs.putInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 1)
        prefs.selectedOverlayName = "CustomOverlay"
        val deletion = deleteCustomOverlay(prefs, 2)
        assertFalse(deletion.wasSelected)
        assertEquals("CustomOverlay", prefs.selectedOverlayName)
        assertEquals(1, prefs.getInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 0))
    }

    @Test fun legacyOverlayZeroWithoutIndicesListCanBeDeleted() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(0), "nodes with natural=tree")
        assertEquals(listOf(0), getCustomOverlayIndices(prefs))
        deleteCustomOverlay(prefs, 0)
        assertEquals(emptyList(), getCustomOverlayIndices(prefs))
        assertFalse(prefs.contains(filterPref(0)))
    }

    @Test fun deletingOverlayOneDoesNotRemoveOverlayTen() {
        val prefs = inMemoryPrefs()
        prefs.putString(filterPref(1), "nodes with a=1")
        prefs.putString(filterPref(10), "nodes with a=10")
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, "1,10")
        deleteCustomOverlay(prefs, 1)
        assertEquals(listOf(10), getCustomOverlayIndices(prefs))
        assertFalse(prefs.contains(filterPref(1)))
        assertTrue(prefs.contains(filterPref(10)))
    }

    @Test fun newOverlayAfterDeletionUsesFreshIndices() {
        val prefs = inMemoryPrefs()
        writeOverlay(prefs, 2, "Old")
        deleteCustomOverlay(prefs, 2)
        prefs.putString(filterPref(2), "nodes with highway=tertiary")
        prefs.putString(namePref(2), "New")
        prefs.putString(Prefs.CUSTOM_OVERLAY_INDICES, "2")
        assertEquals(listOf(2), getCustomOverlayIndices(prefs))
        assertEquals(1, getFakeCustomOverlays(prefs, onlyIfExpertMode = false).size)
    }
}
