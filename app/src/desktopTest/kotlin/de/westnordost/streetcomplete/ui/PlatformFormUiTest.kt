package de.westnordost.streetcomplete.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformFormUiTest {
    @Test fun `desktop enables persistent close and escape dismiss`() {
        assertTrue(showsPersistentQuestFormCloseButton())
        assertTrue(supportsQuestFormEscapeDismiss())
    }
}
