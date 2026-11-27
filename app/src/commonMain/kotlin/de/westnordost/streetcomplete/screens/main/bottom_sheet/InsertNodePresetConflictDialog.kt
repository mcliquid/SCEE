package de.westnordost.streetcomplete.screens.main.bottom_sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.insert_node_preset_conflict_keep
import de.westnordost.streetcomplete.resources.insert_node_preset_conflict_message
import de.westnordost.streetcomplete.resources.insert_node_preset_conflict_replace
import de.westnordost.streetcomplete.resources.insert_node_preset_conflict_title
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmationDialog
import org.jetbrains.compose.resources.stringResource

/** Asks whether preset values may replace differing values of an existing vertex. Dismissing the
 *  dialog in any way other than choosing [onReplace] keeps the existing values. */
@Composable
fun InsertNodePresetConflictDialog(
    conflicts: List<PresetTagConflict>,
    onKeepExisting: () -> Unit,
    onReplace: () -> Unit,
) {
    // ConfirmationDialog calls onDismissRequest after onConfirmed too; don't treat that as "keep"
    var replacing by remember { mutableStateOf(false) }
    ConfirmationDialog(
        onDismissRequest = { if (!replacing) onKeepExisting() },
        onConfirmed = {
            replacing = true
            onReplace()
        },
        title = { Text(stringResource(Res.string.insert_node_preset_conflict_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.insert_node_preset_conflict_message))
                Text(conflicts.joinToString("\n") { "${it.key}: ${it.existingValue} → ${it.presetValue}" })
            }
        },
        confirmButtonText = stringResource(Res.string.insert_node_preset_conflict_replace),
        cancelButtonText = stringResource(Res.string.insert_node_preset_conflict_keep),
    )
}
