package de.westnordost.streetcomplete.quests.crossing_markings_colour

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.quests.building_colour.colorFilter
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.item_select.ItemsSelectGrid
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import com.cheonjaeung.compose.grid.SimpleGridCells
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddCrossingMarkingsColourForm(
    on: (QuestAction<CrossingMarkingsColourAnswer>) -> Unit,
    element: Element,
    modifier: Modifier = Modifier,
) {
    val initial = remember(element.id) { parseCrossingMarkingsColourAnswer(element.tags) }
    var selectedStandard by rememberSerializable { mutableStateOf(initial.standard) }
    var otherColours by rememberSerializable { mutableStateOf(initial.other) }
    var showOtherDialog by remember { mutableStateOf(false) }

    QuestForm(
        on = on,
        isComplete = selectedStandard.isNotEmpty() || otherColours.isNotEmpty(),
        onClickOk = { on(Answer(CrossingMarkingsColourAnswer(selectedStandard, otherColours))) },
        modifier = modifier,
        isResurvey = initial.standard.isNotEmpty() || initial.other.isNotEmpty(),
        otherAnswers = {
            listOf(AnswerItem(stringResource(Res.string.quest_crossing_markings_colour_other)) {
                showOtherDialog = true
            })
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalTextStyle provides MaterialTheme.typography.body2
            ) {
                Text(stringResource(Res.string.quest_multiselect_hint))
            }
            if (otherColours.isNotEmpty()) {
                Text(otherColours.sorted().joinToString(", "))
            }
            ItemsSelectGrid(
                columns = SimpleGridCells.Fixed(3),
                items = CrossingMarkingsColour.entries,
                selectedItems = selectedStandard,
                onSelect = { item, selected ->
                    selectedStandard = if (selected) selectedStandard + item else selectedStandard - item
                },
                modifier = Modifier,
                itemContent = {
                    ImageWithLabel(
                        painter = painterResource(Res.drawable.ic_building_colour),
                        label = stringResource(it.titleRes),
                        colorFilter = it.colorFilter(),
                    )
                }
            )
        }
    }

    if (showOtherDialog) {
        OtherCrossingMarkingsColourDialog(
            onDismissRequest = { showOtherDialog = false },
            onConfirm = { colour ->
                otherColours = otherColours + colour
                showOtherDialog = false
            },
        )
    }
}

@Composable
private fun OtherCrossingMarkingsColourDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue()) }
    val normalized = normalizeOtherCrossingMarkingsColour(text.text)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.quest_crossing_markings_colour_other)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.quest_crossing_markings_colour_other_hint))
                TextField2(
                    value = text,
                    onValueChange = { text = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
            }
        },
        buttonRow = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
            TextButton(
                onClick = { normalized?.let(onConfirm) },
                enabled = normalized != null,
            ) {
                Text(stringResource(Res.string.ok))
            }
        },
    )
}
