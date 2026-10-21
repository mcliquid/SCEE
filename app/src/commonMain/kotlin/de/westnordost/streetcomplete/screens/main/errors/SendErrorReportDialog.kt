package de.westnordost.streetcomplete.screens.main.errors

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogProperties
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import org.jetbrains.compose.resources.stringResource

/** Dialog that asks user to send a crash report to the developer */
@Composable
fun SendErrorReportDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    title: String,
    reportText: String,
) {
    val clipboard = LocalClipboardManager.current
    ConfirmationDialog(
        onDismissRequest = onDismissRequest,
        onConfirmed = onConfirmed,
        title = { Text(title) },
        text = {
            Column {
                Text(stringResource(Res.string.crash_message))
                Button({
                    clipboard.setText(AnnotatedString(reportText))
                }) {
                    Text(stringResource(Res.string.action_copy)) }
            }
        },
        confirmButtonText = stringResource(Res.string.crash_compose_email),
        // should be more of a modal dialog
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    )
}
