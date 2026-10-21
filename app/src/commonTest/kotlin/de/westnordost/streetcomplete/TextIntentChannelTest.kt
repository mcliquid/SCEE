package de.westnordost.streetcomplete

import androidx.lifecycle.SavedStateHandle
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The shared-text intent travels activity -> AppViewModel -> App -> MainNavHost -> MainScreen. */
class TextIntentChannelTest {
    @Test fun `intent arriving before the main screen is composed is still there`() {
        val vm = AppViewModel(inMemoryPrefs(), SavedStateHandle())
        vm.openTextIntent("content://a")
        assertEquals("content://a", vm.textIntentUri.value)
    }

    @Test fun `new intent replaces the pending one`() {
        val vm = AppViewModel(inMemoryPrefs(), SavedStateHandle())
        vm.openTextIntent("content://a")
        vm.openTextIntent("content://b")
        assertEquals("content://b", vm.textIntentUri.value)
    }

    @Test fun `consumed intent is gone and does not return after recreation`() {
        val state = SavedStateHandle()
        AppViewModel(inMemoryPrefs(), state).run { openTextIntent("content://a"); consumeTextIntent() }
        assertNull(AppViewModel(inMemoryPrefs(), state).textIntentUri.value)
    }

    @Test fun `pending intent survives recreation until consumed`() {
        val state = SavedStateHandle()
        AppViewModel(inMemoryPrefs(), state).openTextIntent("content://a")
        assertEquals("content://a", AppViewModel(inMemoryPrefs(), state).textIntentUri.value)
    }
}
