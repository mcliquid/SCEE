package de.westnordost.streetcomplete.quests.surface

import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.Surface.ASPHALT
import de.westnordost.streetcomplete.osm.surface.Surface.CONCRETE
import de.westnordost.streetcomplete.osm.surface.Surface.PAVING_STONES
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddSidewalkSurfaceFormTest {

    @Test fun `BOTH uses the existing legacy last-picked key exactly`() {
        assertEquals("AddSidewalkSurfaceForm", sidewalkSurfaceLastPickedKey(true, true))
        assertEquals(SIDEWALK_SURFACE_LAST_PICKED_KEY, sidewalkSurfaceLastPickedKey(true, true))
    }

    @Test fun `LEFT uses the left last-picked namespace`() {
        assertEquals("AddSidewalkSurfaceForm.left", sidewalkSurfaceLastPickedKey(true, false))
    }

    @Test fun `RIGHT uses the right last-picked namespace`() {
        assertEquals("AddSidewalkSurfaceForm.right", sidewalkSurfaceLastPickedKey(false, true))
    }

    @Test fun `left and right last-picked histories do not leak into each other`() {
        val prefs = inMemoryPrefs()
        saveSidewalkSurfaceLastPicked(prefs, Sides(ASPHALT, null), hasSidewalkLeft = true, hasSidewalkRight = false)
        saveSidewalkSurfaceLastPicked(prefs, Sides(null, CONCRETE), hasSidewalkLeft = false, hasSidewalkRight = true)

        assertEquals(
            listOf(Sides(ASPHALT, null)),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = false)
        )
        assertEquals(
            listOf(Sides(null, CONCRETE)),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = false, hasSidewalkRight = true)
        )
        assertEquals(
            emptyList(),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = true)
        )
        assertEquals(
            emptyList(),
            prefs.getLastPicked<Sides<Surface>>(SIDEWALK_SURFACE_LAST_PICKED_KEY)
        )
    }

    @Test fun `one-sided stored Sides values contain null for the absent side`() {
        assertEquals(
            Sides(ASPHALT, null),
            sidewalkSurfacesForLastPicked(Sides(ASPHALT, CONCRETE), hasSidewalkLeft = true, hasSidewalkRight = false)
        )
        assertEquals(
            Sides(null, CONCRETE),
            sidewalkSurfacesForLastPicked(Sides(ASPHALT, CONCRETE), hasSidewalkLeft = false, hasSidewalkRight = true)
        )

        val prefs = inMemoryPrefs()
        saveSidewalkSurfaceLastPicked(prefs, Sides(ASPHALT, CONCRETE), hasSidewalkLeft = true, hasSidewalkRight = false)
        saveSidewalkSurfaceLastPicked(prefs, Sides(ASPHALT, PAVING_STONES), hasSidewalkLeft = false, hasSidewalkRight = true)

        val left = loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = false).single()
        assertEquals(ASPHALT, left.left)
        assertNull(left.right)

        val right = loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = false, hasSidewalkRight = true).single()
        assertNull(right.left)
        assertEquals(PAVING_STONES, right.right)
    }

    @Test fun `existing BOTH last-picked history remains compatible`() {
        val prefs = inMemoryPrefs()
        val legacyBoth = Sides(ASPHALT, CONCRETE)
        prefs.setLastPicked(SIDEWALK_SURFACE_LAST_PICKED_KEY, listOf(legacyBoth))

        assertEquals(
            listOf(legacyBoth),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = true)
        )
        assertEquals(
            emptyList(),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = false)
        )
        assertEquals(
            emptyList(),
            loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = false, hasSidewalkRight = true)
        )
    }

    @Test fun `selecting a left-only last-picked entry applies the left surface and completes the form`() {
        val prefs = inMemoryPrefs()
        saveSidewalkSurfaceLastPicked(prefs, Sides(ASPHALT, null), hasSidewalkLeft = true, hasSidewalkRight = false)

        val applied = loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = true, hasSidewalkRight = false).first()
        assertEquals(ASPHALT, applied.left)
        assertNull(applied.right)
        assertTrue(isSidewalkSurfaceFormComplete(applied, hasSidewalkLeft = true, hasSidewalkRight = false))
    }

    @Test fun `selecting a right-only last-picked entry applies the right surface and completes the form`() {
        val prefs = inMemoryPrefs()
        saveSidewalkSurfaceLastPicked(prefs, Sides(null, CONCRETE), hasSidewalkLeft = false, hasSidewalkRight = true)

        val applied = loadSidewalkSurfaceLastPicked(prefs, hasSidewalkLeft = false, hasSidewalkRight = true).first()
        assertNull(applied.left)
        assertEquals(CONCRETE, applied.right)
        assertTrue(isSidewalkSurfaceFormComplete(applied, hasSidewalkLeft = false, hasSidewalkRight = true))
    }
}

/** Mirrors the completeness condition in [AddSidewalkSurfaceForm]. */
private fun isSidewalkSurfaceFormComplete(
    sidewalkSurfaces: Sides<Surface>,
    hasSidewalkLeft: Boolean,
    hasSidewalkRight: Boolean,
): Boolean =
    (!hasSidewalkLeft || sidewalkSurfaces.left != null) &&
    (!hasSidewalkRight || sidewalkSurfaces.right != null)
