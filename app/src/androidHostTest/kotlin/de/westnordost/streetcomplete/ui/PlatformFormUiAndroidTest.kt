package de.westnordost.streetcomplete.ui

import kotlin.test.Test
import kotlin.test.assertFalse

class PlatformFormUiAndroidTest {
    @Test fun `android keeps touch sheet dismiss semantics`() {
        assertFalse(showsPersistentQuestFormCloseButton())
        assertFalse(supportsQuestFormEscapeDismiss())
    }
}
