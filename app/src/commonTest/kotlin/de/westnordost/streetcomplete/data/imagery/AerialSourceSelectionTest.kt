package de.westnordost.streetcomplete.data.imagery

import kotlin.test.Test
import kotlin.test.assertEquals

class AerialSourceSelectionTest {

    @Test fun roundtrip() {
        listOf(
            AerialSourceSelection.Esri,
            AerialSourceSelection.Auto,
            AerialSourceSelection.Eli("Baden_Wuerttemberg-DOP20"),
            AerialSourceSelection.Manual("uuid-1"),
        ).forEach { sel ->
            assertEquals(sel, AerialSourceSelection.decode(sel.encode()))
        }
    }

    @Test fun `null and blank become esri`() {
        assertEquals(AerialSourceSelection.Esri, AerialSourceSelection.decode(null))
        assertEquals(AerialSourceSelection.Esri, AerialSourceSelection.decode(""))
        assertEquals(AerialSourceSelection.Esri, AerialSourceSelection.decode("unknown"))
    }
}
