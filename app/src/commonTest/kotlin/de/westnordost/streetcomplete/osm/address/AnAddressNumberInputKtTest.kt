package de.westnordost.streetcomplete.osm.address

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AnAddressNumberInputKtTest {

    @Test fun `null label stays null so suggestion can show while unfocused`() {
        assertNull(effectiveAddressNumberLabel(null))
    }

    @Test fun `blank label is treated as absent so suggestion can show while unfocused`() {
        // Empty string was previously passed for the housenumber field to align with the unit
        // field in expert mode; Material then hid the placeholder until focus (SCEE#918).
        assertNull(effectiveAddressNumberLabel(""))
    }

    @Test fun `non-empty label is kept`() {
        assertEquals("unit", effectiveAddressNumberLabel("unit"))
    }
}
