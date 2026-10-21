package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberQrScanner(onScanned: (String) -> Unit, onNoHandler: () -> Unit): (() -> Unit)? = null
