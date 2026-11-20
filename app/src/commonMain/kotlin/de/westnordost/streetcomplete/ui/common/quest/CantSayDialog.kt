package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog as ScAlertDialog
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Dialog in which the user is asked how to proceed when a quest can't be answered:
 * - wants to leave a note to explain why it can't be answered
 * - rather just hide the quest
 * - just disable the whole quest type */
@Composable
fun CantSayDialog(
    onDismissRequest: () -> Unit,
    onLeaveNote: () -> Unit,
    onHideQuest: () -> Unit,
    onDisableQuest: () -> Unit,
    onCreateCustomQuest: ((text: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val prefs: Preferences = koinInject()
    var createCustomQuest by rememberSaveable { mutableStateOf(false) }
    ScAlertDialog(
        onDismissRequest = onDismissRequest,
        buttonRow = {
            FlowRow {
                TextButton(onClick = { onDismissRequest(); onLeaveNote() }) {
                    Text(stringResource(Res.string.quest_leave_new_note_yes))
                }
                TextButton(onClick = { onDismissRequest(); onDisableQuest() }) {
                    Text(stringResource(Res.string.quest_leave_new_note_disable_quest))
                }
                TextButton(onClick = { onDismissRequest(); onHideQuest() }) {
                    Text(stringResource(Res.string.quest_leave_new_note_no))
                }
                if (prefs.getBoolean(Prefs.CREATE_EXTERNAL_QUESTS, false) && onCreateCustomQuest != null) {
                    TextButton(onClick = { createCustomQuest = true }) {
                        Text(stringResource(Res.string.create_custom_quest_title_message))
                    }
                }
            }
        },
        title = { Text(stringResource(Res.string.quest_leave_new_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.quest_leave_new_note_description))
                Text(stringResource(Res.string.quest_leave_new_note_description_disable))
            }
        },
        modifier = modifier,
    )
    if (createCustomQuest) {
        var text by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
        AlertDialog(
            onDismissRequest = { createCustomQuest = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCreateCustomQuest!!(text.text)
                        onDismissRequest()
                    },
                    enabled = text.text.isNotBlank()
                ) {
                    Text(stringResource(Res.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { createCustomQuest = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
            title = { Text(stringResource(Res.string.create_custom_quest_title_message)) },
            text = { TextField2(text, { text = it }) },
            modifier = modifier,
        )
    }
}