package de.westnordost.streetcomplete.ui.common.bottom_sheet

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BottomSheetFormDismissTest {
    @Test fun `persistent close or expanded back shows close control`() {
        assertFalse(shouldShowBottomSheetCloseButton(swipeExpandedBackVisible = false, persistentCloseButton = false))
        assertTrue(shouldShowBottomSheetCloseButton(swipeExpandedBackVisible = true, persistentCloseButton = false))
        assertTrue(shouldShowBottomSheetCloseButton(swipeExpandedBackVisible = false, persistentCloseButton = true))
    }
}
