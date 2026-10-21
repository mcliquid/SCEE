package de.westnordost.streetcomplete.ui

/** Persistent close control in [BottomSheetFormScaffold] (desktop), not only after expanding the sheet. */
expect fun showsPersistentQuestFormCloseButton(): Boolean

/** [androidx.compose.ui.input.key.Key.Escape] dismisses the open quest form (desktop). */
expect fun supportsQuestFormEscapeDismiss(): Boolean
