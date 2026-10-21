package de.westnordost.streetcomplete.ui.util

import androidx.compose.ui.graphics.toArgb
import kotlin.test.*

class DisplayColorsTest {
    @Test fun `hex colors preserve RGB alpha and channel order`() {
        assertEquals(0xff123456.toInt(), parseHexColor("#123456").toArgb())
        assertEquals(0x8012aBcD.toInt(), parseHexColor("#8012aBcD").toArgb())
        assertEquals(0x00123456, parseHexColor("#00123456").toArgb())
    }

    @Test fun `unsupported shorthand malformed colors and names are not silently normalized`() {
        for (input in listOf("", "#RGB", "#123", "#1234", "#1234567", "#gg1122", "red")) {
            assertFailsWith<IllegalArgumentException> { parseHexColor(input) }
        }
    }

    @Test fun `white tint keeps previous truncation and alpha arithmetic`() {
        assertEquals(0xffcccccc.toInt(), nearbyQuestTint(0xff000000.toInt()).toArgb())
        assertEquals(0xccffffff.toInt(), nearbyQuestTint(0x00ffffff).toArgb())
        assertEquals(0xffffffff.toInt(), nearbyQuestTint(-1).toArgb())
    }
}
