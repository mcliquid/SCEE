package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.State
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import kotlinx.coroutines.flow.Flow

/** SCEE: Android hardware keys that are forwarded from the activity to the main screen */
enum class HardwareKey { VolumeUp, VolumeDown, Menu }

/** What the activity does with a key event: swallow it (so the system does not react) and/or forward it */
data class HardwareKeyResult(val consumed: Boolean, val forward: HardwareKey?)

/**
 * The menu key is always consumed. Volume keys only if "zoom with volume buttons" is on, otherwise they
 * change the system volume as usual. Consumed keys act on key up only, so a press (or holding the key)
 * triggers exactly one action.
 */
fun handleHardwareKey(key: HardwareKey, isKeyUp: Boolean, prefs: Preferences): HardwareKeyResult {
    val handled = key == HardwareKey.Menu || prefs.getBoolean(Prefs.VOLUME_ZOOM, false)
    return HardwareKeyResult(consumed = handled, forward = if (handled && isKeyUp) key else null)
}

enum class HardwareKeyAction { ZoomIn, ZoomOut, OpenMainMenu }

fun hardwareKeyAction(key: HardwareKey): HardwareKeyAction = when (key) {
    HardwareKey.VolumeUp -> HardwareKeyAction.ZoomIn
    HardwareKey.VolumeDown -> HardwareKeyAction.ZoomOut
    HardwareKey.Menu -> HardwareKeyAction.OpenMainMenu
}

/** Zoom steps for the volume keys */
fun HardwareKeyAction.zoomAmount(): Double? = when (this) {
    HardwareKeyAction.ZoomIn -> 1.0
    HardwareKeyAction.ZoomOut -> -1.0
    HardwareKeyAction.OpenMainMenu -> null
}

/**
 * Passes each forwarded key to the *current* [handler]. The handler is read per key and not captured
 * once: a long-running collector would otherwise keep using the camera state of the first composition.
 */
suspend fun Flow<HardwareKey>.collectHardwareKeyActions(handler: State<(HardwareKeyAction) -> Unit>) {
    collect { handler.value(hardwareKeyAction(it)) }
}

/**
 * The menu key, released while the main menu is open, opens the settings. The dialog has its own
 * window, so the activity does not get this key.
 */
fun isMenuKeyReleased(key: Key, type: KeyEventType): Boolean = key == Key.Menu && type == KeyEventType.KeyUp
