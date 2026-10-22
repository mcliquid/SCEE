package de.westnordost.streetcomplete.ui.common.bottom_sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/** Whether the header shows a close/back control (swipe-expanded back arrow or persistent desktop close). */
internal fun shouldShowBottomSheetCloseButton(
    swipeExpandedBackVisible: Boolean,
    persistentCloseButton: Boolean,
): Boolean = swipeExpandedBackVisible || persistentCloseButton

internal fun isQuestFormDismissEscapeKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.Escape

/** Desktop quest-form Escape; uses bubbling [onKeyEvent] so popups and dialogs can consume Esc first. */
internal fun Modifier.questFormEscapeDismiss(
    enabled: Boolean,
    focusRequester: FocusRequester,
    onDismiss: () -> Unit,
): Modifier = if (!enabled) {
    this
} else {
    focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { event ->
            if (isQuestFormDismissEscapeKey(event)) {
                onDismiss()
                true
            } else {
                false
            }
        }
}
