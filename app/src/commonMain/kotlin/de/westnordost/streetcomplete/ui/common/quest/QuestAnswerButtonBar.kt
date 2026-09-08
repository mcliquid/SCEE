package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.DropdownMenuItem
import de.westnordost.streetcomplete.ui.common.VerticalDivider
import org.jetbrains.compose.resources.stringResource

@Immutable
data class AnswerItem(val text: String, val action: () -> Unit)

/**
 * Segmented answer button bar matching the pre-Compose FlexboxLayout behavior:
 * every visible segment (including the optional "Uh…" other-answers control) shares the
 * available width equally, with vertical dividers between segments and centered labels.
 */
@Composable
fun QuestAnswerButtonBar(
    modifier: Modifier = Modifier,
    answers: List<AnswerItem> = emptyList(),
    otherAnswers: @Composable (() -> List<AnswerItem>)? = null,
) {
    if (otherAnswers == null && answers.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (otherAnswers != null) {
            OtherAnswersTextButton(
                answers = otherAnswers,
                modifier = Modifier.weight(1f),
            )
        }
        for ((index, item) in answers.withIndex()) {
            if (otherAnswers != null || index != 0) {
                VerticalDivider(Modifier.height(24.dp))
            }
            TextButton(
                onClick = item.action,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.text,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun OtherAnswersTextButton(
    answers: @Composable () -> List<AnswerItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(Res.string.quest_generic_otherAnswers2),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (answer in answers()) {
                DropdownMenuItem(onClick = { expanded = false; answer.action() }) {
                    Text(answer.text)
                }
            }
        }
    }
}

@Preview
@Composable
private fun QuestAnswerButtonBarPreview() {
    QuestAnswerButtonBar(
        answers = listOf(
            AnswerItem("No") {},
            AnswerItem("Yes") {},
        ),
        otherAnswers = { listOf(
            AnswerItem("Can't say") {}
        ) }
    )
}

@Preview
@Composable
private fun QuestAnswerButtonBarManyAnswersPreview() {
    QuestAnswerButtonBar(
        answers = listOf(
            AnswerItem("No") {},
            AnswerItem("Perhaps") {},
            AnswerItem("Depends how you define \"No\"") {},
            AnswerItem("Yes") {},
        ),
        otherAnswers = { listOf(
            AnswerItem("Depends how you define \"Yes\"") {},
            AnswerItem("Can't say") {}
        ) }
    )
}
