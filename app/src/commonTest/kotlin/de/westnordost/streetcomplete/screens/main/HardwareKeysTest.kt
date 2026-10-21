package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import de.westnordost.streetcomplete.AppViewModel
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HardwareKeysTest {
    private fun prefsWithVolumeZoom(on: Boolean) = inMemoryPrefs().also { it.putBoolean(Prefs.VOLUME_ZOOM, on) }

    @Test fun `volume keys stay with the system if volume zoom is off`() {
        val prefs = prefsWithVolumeZoom(false)
        for (key in listOf(HardwareKey.VolumeUp, HardwareKey.VolumeDown)) {
            for (isUp in listOf(false, true)) {
                assertEquals(HardwareKeyResult(consumed = false, forward = null), handleHardwareKey(key, isUp, prefs))
            }
        }
    }

    @Test fun `volume zoom is off by default`() {
        assertFalse(handleHardwareKey(HardwareKey.VolumeUp, true, inMemoryPrefs()).consumed)
    }

    @Test fun `volume keys are consumed on down and forwarded once on up`() {
        val prefs = prefsWithVolumeZoom(true)
        assertEquals(HardwareKeyResult(true, null), handleHardwareKey(HardwareKey.VolumeUp, false, prefs)) // also repeats
        assertEquals(HardwareKeyResult(true, HardwareKey.VolumeUp), handleHardwareKey(HardwareKey.VolumeUp, true, prefs))
        assertEquals(HardwareKeyResult(true, null), handleHardwareKey(HardwareKey.VolumeDown, false, prefs))
        assertEquals(HardwareKeyResult(true, HardwareKey.VolumeDown), handleHardwareKey(HardwareKey.VolumeDown, true, prefs))
    }

    @Test fun `menu key is always consumed and forwarded on up only`() {
        for (on in listOf(false, true)) {
            val prefs = prefsWithVolumeZoom(on)
            assertEquals(HardwareKeyResult(true, null), handleHardwareKey(HardwareKey.Menu, false, prefs))
            assertEquals(HardwareKeyResult(true, HardwareKey.Menu), handleHardwareKey(HardwareKey.Menu, true, prefs))
        }
    }

    @Test fun `volume up zooms in, volume down zooms out, menu opens the main menu`() {
        assertEquals(HardwareKeyAction.ZoomIn, hardwareKeyAction(HardwareKey.VolumeUp))
        assertEquals(HardwareKeyAction.ZoomOut, hardwareKeyAction(HardwareKey.VolumeDown))
        assertEquals(HardwareKeyAction.OpenMainMenu, hardwareKeyAction(HardwareKey.Menu))
    }

    @Test fun `in the open main menu only the released menu key opens the settings`() {
        assertTrue(isMenuKeyReleased(Key.Menu, KeyEventType.KeyUp))
        assertFalse(isMenuKeyReleased(Key.Menu, KeyEventType.KeyDown))
        for (key in listOf(Key.Escape, Key.Back, Key.Enter, Key.VolumeUp, Key.DirectionDown, Key.Tab)) {
            assertFalse(isMenuKeyReleased(key, KeyEventType.KeyUp))
        }
    }

    @Test fun `forward is null for keys that are not handled`() {
        assertNull(handleHardwareKey(HardwareKey.VolumeDown, true, prefsWithVolumeZoom(false)).forward)
    }
}

class HardwareKeyForwardingTest {
    private val prefs = inMemoryPrefs().also { it.putBoolean(Prefs.VOLUME_ZOOM, true) }

    /** The way the activity forwards a full key press: down is only consumed, up is forwarded */
    private fun press(keys: MutableSharedFlow<HardwareKey>, key: HardwareKey) {
        handleHardwareKey(key, false, prefs).forward?.let { keys.tryEmit(it) }
        handleHardwareKey(key, true, prefs).forward?.let { keys.tryEmit(it) }
    }

    @Test fun `a volume key press reaches the zoom action exactly once, in the right direction`() = runBlocking {
        val keys = MutableSharedFlow<HardwareKey>(extraBufferCapacity = 1)
        val zooms = mutableListOf<Double>()
        val handler = mutableStateOf<(HardwareKeyAction) -> Unit>({ action -> action.zoomAmount()?.let { zooms += it } })
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            keys.onSubscription { started.complete(Unit) }.collectHardwareKeyActions(handler)
        }
        started.await()
        press(keys, HardwareKey.VolumeUp)
        withTimeout(2000) { while (zooms.size < 1) delay(5) }
        press(keys, HardwareKey.VolumeDown)
        withTimeout(2000) { while (zooms.size < 2) delay(5) }
        job.cancelAndJoin()
        assertEquals(listOf(1.0, -1.0), zooms)
    }

    @Test fun `a handler that is replaced after the collector started is used for the next key`() = runBlocking {
        val keys = MutableSharedFlow<HardwareKey>(extraBufferCapacity = 1)
        val first = mutableListOf<Double>()
        val second = mutableListOf<Double>()
        val handler = mutableStateOf<(HardwareKeyAction) -> Unit>({ action -> action.zoomAmount()?.let { first += it } })
        val started = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.Default) {
            keys.onSubscription { started.complete(Unit) }.collectHardwareKeyActions(handler)
        }
        started.await()
        press(keys, HardwareKey.VolumeUp)
        withTimeout(2000) { while (first.size < 1) delay(5) }
        handler.value = { action -> action.zoomAmount()?.let { second += it } } // e.g. new camera state
        press(keys, HardwareKey.VolumeUp)
        withTimeout(2000) { while (second.size < 1) delay(5) }
        job.cancelAndJoin()
        assertEquals(listOf(1.0), first)
        assertEquals(listOf(1.0), second)
    }

    @Test fun `with volume zoom off nothing is forwarded`() = runBlocking {
        val off = inMemoryPrefs()
        val keys = MutableSharedFlow<HardwareKey>(extraBufferCapacity = 1)
        handleHardwareKey(HardwareKey.VolumeUp, true, off).forward?.let { keys.tryEmit(it) }
        assertEquals(0, keys.replayCache.size)
        assertEquals(null, handleHardwareKey(HardwareKey.VolumeUp, true, off).forward)
    }
}

/**
 * The activity emits into the AppViewModel it owns, and App / MainNavHost hand exactly that flow to
 * the main screen. A key press must arrive through this real producer, not through a separate flow.
 */
class HardwareKeyChannelTest {
    @Test fun `a key emitted into the app view model reaches the collector of the main screen`() = runBlocking {
        val prefs = inMemoryPrefs().also { it.putBoolean(Prefs.VOLUME_ZOOM, true) }
        val appViewModel = AppViewModel(prefs, SavedStateHandle())
        val zooms = mutableListOf<Double>()
        val handler = mutableStateOf<(HardwareKeyAction) -> Unit>({ action -> action.zoomAmount()?.let { zooms += it } })
        val started = CompletableDeferred<Unit>()
        // what MainNavHost passes to MainScreen
        val screenFlow = appViewModel.hardwareKeys.onSubscription { started.complete(Unit) }
        val job = launch(Dispatchers.Default) { screenFlow.collectHardwareKeyActions(handler) }
        started.await()

        // what MainActivity does for a volume up press
        handleHardwareKey(HardwareKey.VolumeUp, false, prefs).forward?.let { appViewModel.hardwareKeys.tryEmit(it) }
        handleHardwareKey(HardwareKey.VolumeUp, true, prefs).forward?.let { assertTrue(appViewModel.hardwareKeys.tryEmit(it)) }
        withTimeout(2000) { while (zooms.isEmpty()) delay(5) }
        job.cancelAndJoin()
        assertEquals(listOf(1.0), zooms)
    }

    @Test fun `different app view models do not share a channel`() {
        val a = AppViewModel(inMemoryPrefs(), SavedStateHandle())
        val b = AppViewModel(inMemoryPrefs(), SavedStateHandle())
        assertEquals(false, a.hardwareKeys === b.hardwareKeys)
    }
}
