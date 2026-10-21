package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.runtime.Composable

/**
 * Returns a function that starts scanning a QR code and reports its text to [onScanned], or null on
 * platforms without a scanner (desktop: no camera integration; values are entered by hand there).
 * [onNoHandler] is called when the platform has no app that can scan.
 */
@Composable
internal expect fun rememberQrScanner(onScanned: (String) -> Unit, onNoHandler: () -> Unit): (() -> Unit)?
