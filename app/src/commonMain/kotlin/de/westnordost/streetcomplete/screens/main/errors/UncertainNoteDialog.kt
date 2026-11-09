package de.westnordost.streetcomplete.screens.main.errors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import de.westnordost.streetcomplete.data.osmnotes.NoteLookup
import de.westnordost.streetcomplete.data.osmnotes.NoteUnavailableException
import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEdit
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteCreationAttempt
import org.jetbrains.compose.resources.StringResource
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsUploader
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import kotlinx.coroutines.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun UncertainNoteDialog(editId: Long, onDismiss: () -> Unit, unavailable: NoteLookup.Unavailable? = null) {
    val controller: NoteEditsController = koinInject()
    val uploader: NoteEditsUploader = koinInject()
    val uri = LocalUriHandler.current
    var edit by remember(editId) { mutableStateOf<NoteEdit?>(null) }
    var attempt by remember(editId) { mutableStateOf<NoteCreationAttempt?>(null) }
    val scope = rememberCoroutineScope()
    var text by remember(editId) { mutableStateOf("") }
    var noteId by remember(editId) { mutableStateOf("") }
    var confirm by remember(editId) { mutableStateOf(false) }
    var discard by remember(editId) { mutableStateOf(false) }
    var busy by remember(editId) { mutableStateOf(false) }
    var failure by remember(editId) { mutableStateOf<StringResource?>(null) }
    LaunchedEffect(editId) {
        try {
            val loaded = withContext(Dispatchers.IO) { controller.get(editId) to controller.getCreationAttempt(editId) }
            edit = loaded.first
            attempt = loaded.second
            text = loaded.second?.text.orEmpty()
            if (edit == null || attempt == null || edit?.isSynced == true) onDismiss()
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure = Res.string.note_resolution_state }
    }
    val canLink = edit?.action == NoteEditAction.CREATE && attempt?.noteId == null
    val targetId = attempt?.noteId ?: edit?.noteId?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(Res.string.upload_error)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text(stringResource(if (attempt?.noteId != null) Res.string.note_creation_acknowledged else Res.string.note_creation_uncertain))
            if (unavailable != null) Text(stringResource(if (unavailable == NoteLookup.Unavailable.HIDDEN)
                Res.string.note_resolution_hidden else Res.string.note_resolution_missing))
            Text(text)
            if (edit?.action != NoteEditAction.CREATE) Text(stringResource(Res.string.note_resolution_comment))
            if (targetId != null) TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/note/$targetId") }) {
                Text(stringResource(Res.string.note_resolution_history))
            }
            if (canLink) TextField(value = noteId, onValueChange = { noteId = it }, enabled = !busy,
                label = { Text(stringResource(Res.string.note_resolution_id)) })
            failure?.let { Text(stringResource(it)) }
            TextButton(enabled = !busy && edit != null && attempt != null, onClick = { discard = true; confirm = true }) {
                Text(stringResource(Res.string.note_resolution_discard))
            }
        } },
        confirmButton = { if (canLink) TextButton(enabled = !busy && (noteId.toLongOrNull() ?: 0) > 0,
            onClick = { discard = false; confirm = true }) { Text(stringResource(Res.string.note_resolution_link)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
    if (confirm) ConfirmationDialog(
        onDismissRequest = { confirm = false },
        text = { Text(if (discard) stringResource(Res.string.note_resolution_discard_confirm)
            else stringResource(Res.string.note_resolution_link_confirm) + " " + noteId) },
        onConfirmed = {
            busy = true
            failure = null
            val selectedId = noteId.toLongOrNull()
            val shouldDiscard = discard
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        if (shouldDiscard) uploader.discardUncertainCreation(editId)
                        else uploader.resolveCreation(editId, checkNotNull(selectedId))
                    }
                    onDismiss()
                } catch (e: CancellationException) { throw e }
                catch (e: NoteUnavailableException) {
                    failure = if (e.reason == NoteLookup.Unavailable.HIDDEN) Res.string.note_resolution_hidden else Res.string.note_resolution_missing
                }
                catch (e: ConnectionException) { failure = Res.string.note_resolution_network }
                catch (e: IllegalStateException) { failure = Res.string.note_resolution_state }
                catch (e: Exception) { failure = Res.string.note_resolution_failed }
                finally { busy = false }
            }
        },
    )
}
