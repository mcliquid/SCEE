package de.westnordost.streetcomplete.screens.main.edithistory

import androidx.compose.foundation.layout.Column
import de.westnordost.streetcomplete.ui.common.dialogs.ScrollableAlertDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import de.westnordost.streetcomplete.data.upload.UploadController
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun RejectedElementEditDialog(editId: Long, onDismiss: () -> Unit, onUndo: () -> Unit) {
    val controller = koinInject<ElementEditsController>()
    val uploads = koinInject<UploadController>()
    val scope = rememberCoroutineScope()
    var edit by remember(editId) { mutableStateOf<de.westnordost.streetcomplete.data.osm.edits.ElementEdit?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(editId) { edit = withContext(Dispatchers.IO) { controller.get(editId) } }
    val review = edit?.review
    ScrollableAlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.element_review_attention)) },
        content = { Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(Res.string.element_review_identity, editId))
            Text(if (review == null) stringResource(Res.string.element_review_unresolved)
                else if (review.editId != editId) stringResource(Res.string.element_review_dependency, review.editId)
                else stringResource(Res.string.element_review_rejected, review.httpStatus))
            if (review != null) Text(review.reason)
            if (failed) Text(stringResource(Res.string.element_review_action_failed))
        } },
        buttonRow = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(enabled = !busy && edit != null, onClick = onUndo) {
                Text(stringResource(Res.string.element_review_undo))
            }
            if (review != null) TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        val retried = withContext(Dispatchers.IO) { controller.retryRejectedEdit(review.editId) }
                        if (retried) { uploads.upload(isUserInitiated = true); onDismiss() } else failed = true
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { failed = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(Res.string.element_review_retry)) }
        },
    )
}
