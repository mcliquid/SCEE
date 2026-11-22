package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import de.westnordost.streetcomplete.ui.supportsQuestFormKeyboardShortcuts

/** Ja/Nein-Shortcuts (Y/N) nur für eindeutige binäre Antwortleisten. */
data class QuestFormBinaryYesNo(
    val onYes: () -> Unit,
    val onNo: () -> Unit,
)

/** Aktueller Tastatur-Shortcut-Zustand einer Ebene (Formular, Dialog, Auswahlleiste). */
data class QuestFormKeyboardLayerState(
    val numberedOptions: () -> List<() -> Unit> = { emptyList() },
    val onSubmit: (() -> Unit)? = null,
    val canSubmit: () -> Boolean = { false },
    val onLastPicked: (() -> Unit)? = null,
    val binaryYesNo: QuestFormBinaryYesNo? = null,
    val onEscape: (() -> Unit)? = null,
    val suppressShortcuts: () -> Boolean = { false },
)

internal class QuestFormKeyboardLayerHolder(
    initial: QuestFormKeyboardLayerState,
) {
    var state by androidx.compose.runtime.mutableStateOf(initial)
}

@Stable
class QuestFormKeyboardScope {
    private val layers = mutableStateListOf<QuestFormKeyboardLayerHolder>()
    private var textInputFocusDepth by mutableIntStateOf(0)

    internal val layerStates: List<QuestFormKeyboardLayerState>
        get() = layers.map { it.state }

    internal fun register(holder: QuestFormKeyboardLayerHolder) {
        layers.add(holder)
    }

    internal fun unregister(holder: QuestFormKeyboardLayerHolder) {
        layers.remove(holder)
    }

    fun onTextInputFocusChanged(focused: Boolean) {
        textInputFocusDepth += if (focused) 1 else -1
        if (textInputFocusDepth < 0) textInputFocusDepth = 0
    }

    fun hasTextInputFocus(): Boolean = textInputFocusDepth > 0
}

val LocalQuestFormKeyboardScope = compositionLocalOf<QuestFormKeyboardScope?> { null }

/** Override in tests to simulate desktop form keyboard shortcuts. */
val LocalQuestFormKeyboardShortcutsEnabled = compositionLocalOf { supportsQuestFormKeyboardShortcuts() }

@Composable
private fun questFormKeyboardShortcutsActive(): Boolean = LocalQuestFormKeyboardShortcutsEnabled.current

@Composable
fun QuestFormKeyboardScopeProvider(content: @Composable () -> Unit) {
    if (!questFormKeyboardShortcutsActive()) {
        content()
        return
    }
    val scope = remember { QuestFormKeyboardScope() }
    androidx.compose.runtime.CompositionLocalProvider(LocalQuestFormKeyboardScope provides scope) {
        content()
    }
}

/** Registriert Shortcut-Handler; neuere Ebenen (z. B. Dialoge) haben Vorrang. */
@Composable
fun QuestFormKeyboardLayer(
    numberedOptions: () -> List<() -> Unit> = { emptyList() },
    onSubmit: (() -> Unit)? = null,
    canSubmit: () -> Boolean = { false },
    onLastPicked: (() -> Unit)? = null,
    binaryYesNo: QuestFormBinaryYesNo? = null,
    onEscape: (() -> Unit)? = null,
    suppressShortcuts: () -> Boolean = { false },
) {
    if (!questFormKeyboardShortcutsActive()) return
    val scope = LocalQuestFormKeyboardScope.current ?: return
    val holder = remember { QuestFormKeyboardLayerHolder(QuestFormKeyboardLayerState()) }
    holder.state = QuestFormKeyboardLayerState(
        numberedOptions = numberedOptions,
        onSubmit = onSubmit,
        canSubmit = canSubmit,
        onLastPicked = onLastPicked,
        binaryYesNo = binaryYesNo,
        onEscape = onEscape,
        suppressShortcuts = suppressShortcuts,
    )
    DisposableEffect(scope, holder) {
        scope.register(holder)
        onDispose { scope.unregister(holder) }
    }
}

fun digitIndexFromKey(key: Key): Int? = when (key) {
    Key.One, Key.NumPad1 -> 0
    Key.Two, Key.NumPad2 -> 1
    Key.Three, Key.NumPad3 -> 2
    Key.Four, Key.NumPad4 -> 3
    Key.Five, Key.NumPad5 -> 4
    Key.Six, Key.NumPad6 -> 5
    Key.Seven, Key.NumPad7 -> 6
    Key.Eight, Key.NumPad8 -> 7
    Key.Nine, Key.NumPad9 -> 8
    else -> null
}

internal fun isQuestFormKeyboardEnterKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)

/**
 * Zentraler Dispatcher für Desktop-Questformular-Shortcuts.
 * @return true wenn das Ereignis verarbeitet wurde.
 */
fun dispatchQuestFormKeyboardShortcut(
    layers: List<QuestFormKeyboardLayerState>,
    hasTextInputFocus: Boolean,
    key: Key,
    type: KeyEventType = KeyEventType.KeyDown,
): Boolean {
    if (type != KeyEventType.KeyDown) return false
    if (layers.any { it.suppressShortcuts() }) return false

    val topToBottom = layers.asReversed()

    when {
        key == Key.Escape -> {
            val layer = topToBottom.firstOrNull { it.onEscape != null } ?: return false
            layer.onEscape?.invoke()
            return true
        }
        key == Key.Enter || key == Key.NumPadEnter -> {
            if (hasTextInputFocus) return false
            for (layer in topToBottom) {
                if (layer.onSubmit != null && layer.canSubmit()) {
                    layer.onSubmit.invoke()
                    return true
                }
            }
            return false
        }
        key == Key.L && !hasTextInputFocus -> {
            val layer = topToBottom.firstOrNull { it.onLastPicked != null } ?: return false
            layer.onLastPicked?.invoke()
            return true
        }
        key == Key.Y && !hasTextInputFocus -> {
            val layer = topToBottom.firstOrNull { it.binaryYesNo != null } ?: return false
            layer.binaryYesNo?.onYes?.invoke()
            return true
        }
        key == Key.N && !hasTextInputFocus -> {
            val layer = topToBottom.firstOrNull { it.binaryYesNo != null } ?: return false
            layer.binaryYesNo?.onNo?.invoke()
            return true
        }
        else -> {
            if (hasTextInputFocus) return false
            val digit = digitIndexFromKey(key) ?: return false
            topToBottom.firstOrNull { it.numberedOptions().isNotEmpty() }?.let { layer ->
                val options = layer.numberedOptions()
                if (digit < options.size) {
                    options[digit].invoke()
                    return true
                }
            }
            return false
        }
    }
}

/**
 * Desktop: fängt Tastatureingaben in Compose-[Dialog]-Fenstern ab (Fokus liegt nicht im
 * [BottomSheetFormScaffold], Ziffern/Esc würden sonst nicht ankommen).
 */
@Composable
fun QuestFormDialogKeyboardHost(
    modifier: Modifier = Modifier,
    onEscape: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!questFormKeyboardShortcutsActive()) {
        content()
        return
    }
    val scope = LocalQuestFormKeyboardScope.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
  Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (scope != null && dispatchQuestFormKeyboardShortcut(
                        scope.layerStates,
                        scope.hasTextInputFocus(),
                        event.key,
                        event.type,
                    )
                ) {
                    return@onKeyEvent true
                }
                if (onEscape != null && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onEscape()
                    true
                } else {
                    false
                }
            },
    ) {
        content()
    }
}

@Composable
internal fun Modifier.trackQuestFormTextInputFocus(): Modifier {
    if (!questFormKeyboardShortcutsActive()) return this
    val scope = LocalQuestFormKeyboardScope.current ?: return this
    return onFocusChanged { scope.onTextInputFocusChanged(it.isFocused) }
}

internal fun Modifier.questFormKeyboardShortcuts(
    enabled: Boolean,
    scope: QuestFormKeyboardScope?,
    escapeEnabled: Boolean,
    escapeFocusRequester: FocusRequester,
    onEscapeDismiss: () -> Unit,
): Modifier {
    if (!enabled || scope == null) {
        return if (escapeEnabled) {
            focusRequester(escapeFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onEscapeDismiss()
                        true
                    } else {
                        false
                    }
                }
        } else {
            this
        }
    }
    return focusRequester(escapeFocusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (dispatchQuestFormKeyboardShortcut(scope.layerStates, scope.hasTextInputFocus(), event.key, event.type)) {
                return@onKeyEvent true
            }
            if (escapeEnabled && event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                onEscapeDismiss()
                true
            } else {
                false
            }
        }
}

/** Kleine Ziffernanzeige für Desktop-Shortcuts neben Auswahloptionen. */
@Composable
fun QuestFormShortcutDigitHint(digit: Int, modifier: Modifier = Modifier) {
    if (!questFormKeyboardShortcutsActive() || digit !in 1..9) return
    androidx.compose.material.Text(
        text = digit.toString(),
        style = androidx.compose.material.MaterialTheme.typography.caption.copy(
            color = androidx.compose.material.MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
        ),
        modifier = modifier,
    )
}