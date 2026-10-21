package de.westnordost.streetcomplete.ui.common.bottom_sheet

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/** Whether the header shows a close/back control (swipe-expanded back arrow or persistent desktop close). */
internal fun shouldShowBottomSheetCloseButton(
    swipeExpandedBackVisible: Boolean,
    persistentCloseButton: Boolean,
): Boolean = swipeExpandedBackVisible || persistentCloseButton

internal fun isQuestFormDismissEscapeKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.Escape
