package de.westnordost.streetcomplete.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class WheelPickerTest {
    @Test fun `center item is first visible item past viewport center`() {
        val items = listOf(
            WheelPickerVisibleItem(index = 0, offset = 0, size = 40),
            WheelPickerVisibleItem(index = 1, offset = 40, size = 40),
            WheelPickerVisibleItem(index = 2, offset = 80, size = 40),
        )
        assertEquals(1, findWheelPickerCenterItemIndex(items, viewportStartOffset = 0, viewportHeight = 120))
    }

    @Test fun `center item respects viewport start offset`() {
        val items = listOf(
            WheelPickerVisibleItem(index = 2, offset = 50, size = 40),
            WheelPickerVisibleItem(index = 3, offset = 90, size = 40),
        )
        assertEquals(2, findWheelPickerCenterItemIndex(items, viewportStartOffset = 40, viewportHeight = 80))
    }
}
