package de.westnordost.streetcomplete.screens.main.edithistory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.edithistory.Edit
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.isEditUndoEnabled
import de.westnordost.streetcomplete.screens.main.isUnsyncedEditBlockedByUpload
import de.westnordost.streetcomplete.ui.common.ToastPopup
import de.westnordost.streetcomplete.ui.common.rememberPredictiveBackProgress
import de.westnordost.streetcomplete.ui.ktx.dir
import de.westnordost.streetcomplete.ui.ktx.isItemAtIndexFullyVisible
import de.westnordost.streetcomplete.ui.ktx.plus
import de.westnordost.streetcomplete.ui.theme.titleSmall
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.ktx.toLocalDateTime
import de.westnordost.streetcomplete.util.locale.DateTimeFormatStyle
import de.westnordost.streetcomplete.util.locale.LocalDateFormatter
import de.westnordost.streetcomplete.util.locale.LocalTimeFormatter
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEdit
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteCreationAttempt
import de.westnordost.streetcomplete.screens.main.errors.UncertainNoteDialog
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/** Shows the edit history in a sidebar. The edit history is grouped by time and date, ordered by
 *  the most recent edit at the bottom. The list always scrolls to the currently selected edit. */
@Composable
fun EditHistorySidebar(
    editItems: List<EditItem>,
    selectedEdit: Edit?,
    isUploading: Boolean,
    onSelectEdit: (Edit) -> Unit,
    onUndoEdit: suspend (Edit) -> Boolean,
    onDismissRequest: () -> Unit,
    getEditElement: suspend (Edit) -> Element?,
    getNoteCreationAttempt: suspend (Edit) -> NoteCreationAttempt?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var resolveNoteId by remember { mutableStateOf<Long?>(null) }
    var showUndoDialog by remember { mutableStateOf(false) }
    var undoTargetId by remember { mutableStateOf<String?>(null) }
    var editElement by rememberSerializable { mutableStateOf<Element?>(null) }
    var undoUnavailableMessage by remember { mutableStateOf<StringResource?>(null) }
    val edits = editItems.map { it.edit }
    val dialogEdit = resolveUndoDialogEdit(edits, undoTargetId)

    // scrolling to selected item
    val selectedIndex = remember(selectedEdit) {
        if (selectedEdit != null) editItems
            .indexOfLast { it.edit == selectedEdit }
            .takeUnless { it < 0 }
        else null
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex ?: editItems.lastIndex)
    LaunchedEffect(selectedEdit) {
        // except for first scroll, only scroll if not fully visible
        if (selectedIndex != null && !state.isItemAtIndexFullyVisible(selectedIndex)) {
            state.animateScrollToItem(selectedIndex)
        }
    }

    // close on back, following the back gesture towards the edge
    val backProgress by rememberPredictiveBackProgress(onBackCompleted = onDismissRequest)
    val dir = LocalLayoutDirection.current.dir

    suspend fun routeProtectedNote(edit: Edit): Boolean {
        getNoteCreationAttempt(edit) ?: return false
        resolveNoteId = (edit as NoteEdit).id
        return true
    }

    fun onClickUndoEdit(edit: Edit, confirmed: Boolean = false) {
        if (!isEditUndoEnabled(edit, isUploading)) {
            undoUnavailableMessage = Res.string.toast_undo_unavailable
            return
        }
        scope.launch {
            try {
                if (routeProtectedNote(edit)) return@launch
                if (confirmed) {
                    if (!onUndoEdit(edit) && !routeProtectedNote(edit)) undoUnavailableMessage = Res.string.toast_undo_unavailable
                } else {
                    val id = undoDialogTargetId(edit)
                    editElement = getEditElement(edit)
                    undoTargetId = id
                    showUndoDialog = true
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { undoUnavailableMessage = Res.string.toast_undo_unavailable }
        }
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                translationX = -backProgress * size.width * dir
                alpha = 1f - backProgress
            }
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
            .shadow(16.dp),
        // not using surface's elevation here because we don't want it to change its background
        // color to gray in dark mode
    ) {
        val verticalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
        LazyColumn(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                .consumeWindowInsets(verticalInsets)
                .width(80.dp),
            state = state,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            // bottom 24 dp to align with undo button
            contentPadding = verticalInsets.asPaddingValues() + PaddingValues(bottom = 56.dp)
        ) {
            items(
                items = editItems,
                key = { editHistoryLazyColumnKey(it.edit) }
            ) { editItem ->
                Column {
                    DateTimeHeader(
                        timestamp = editItem.edit.createdTimestamp,
                        showDate = editItem.showDate,
                        showTime = editItem.showTime
                    )
                    EditHistoryItem(
                        selected = selectedEdit == editItem.edit,
                        onSelect = { onSelectEdit(editItem.edit) },
                        onUndo = { onClickUndoEdit(editItem.edit) },
                        edit = editItem.edit,
                        undoEnabled = !isUnsyncedEditBlockedByUpload(editItem.edit, isUploading),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    LaunchedEffect(showUndoDialog, edits) {
        if (showUndoDialog && dialogEdit == null) {
            showUndoDialog = false
            undoTargetId = null
            editElement = null
        }
    }

    if (showUndoDialog && dialogEdit != null) {
        UndoDialog(
            edit = dialogEdit,
            element = editElement,
            onDismissRequest = {
                showUndoDialog = false
                undoTargetId = null
                editElement = null
            },
            onConfirmed = {
                val target = editToUndo(edits, undoTargetId, isUploading)
                val stillThere = resolveUndoDialogEdit(edits, undoTargetId) != null
                showUndoDialog = false
                undoTargetId = null
                editElement = null
                when {
                    target != null -> onClickUndoEdit(target, confirmed = true)
                    stillThere -> undoUnavailableMessage = Res.string.toast_undo_unavailable
                }
            }
        )
    }

    resolveNoteId?.let { id ->
        UncertainNoteDialog(id, onDismiss = { resolveNoteId = null })
    }

    undoUnavailableMessage?.let { message ->
        ToastPopup(
            onDismissRequest = { undoUnavailableMessage = null },
            text = stringResource(message)
        )
    }
}

@Composable
private fun DateTimeHeader(
    timestamp: Long,
    showDate: Boolean,
    showTime: Boolean,
    modifier: Modifier = Modifier
) {
    val dateTime = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime()
    val dateFormatter = LocalDateFormatter(style = DateTimeFormatStyle.Short)
    val timeFormatter = LocalTimeFormatter(style = DateTimeFormatStyle.Short)

    CompositionLocalProvider(
        LocalTextStyle provides MaterialTheme.typography.titleSmall,
        LocalContentAlpha provides ContentAlpha.medium
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            // divider to demarcate time boundary
            if (showDate || showTime) {
                Divider()
            }
            if (showDate) {
                // locale-dependent, e.g. 13/8/24
                Text(dateFormatter.format(dateTime.date))
            }
            if (showTime) {
                // locale-dependent, e.g. 12:30 PM
                Text(timeFormatter.format(dateTime.time))
            }
        }
    }
}

/** LazyColumn item key; [Edit.key] alone is not unique for repeated quest-hide events. */
internal fun editHistoryLazyColumnKey(edit: Edit): String =
    "${Json.encodeToString(edit.key)}#${edit.createdTimestamp}"

/** Identity captured when the undo dialog opens. Later selection changes do not retarget it. */
internal fun undoDialogTargetId(edit: Edit): String = editHistoryLazyColumnKey(edit)

internal fun resolveUndoDialogEdit(edits: List<Edit>, targetId: String?): Edit? {
    if (targetId == null) return null
    return edits.find { undoDialogTargetId(it) == targetId }
}

/** The edit the open dialog may undo, or null when that edit is gone or undo is no longer allowed. */
internal fun editToUndo(edits: List<Edit>, targetId: String?, isUploading: Boolean): Edit? {
    val edit = resolveUndoDialogEdit(edits, targetId) ?: return null
    if (!isEditUndoEnabled(edit, isUploading)) return null
    return edit
}
