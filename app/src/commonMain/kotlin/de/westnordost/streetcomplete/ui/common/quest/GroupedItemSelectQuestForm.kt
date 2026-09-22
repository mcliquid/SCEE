package de.westnordost.streetcomplete.ui.common.quest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalTextStyle
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.addLastPicked
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.AreYouSureDialog
import de.westnordost.streetcomplete.ui.common.item_select.Group
import de.westnordost.streetcomplete.ui.common.item_select.GroupedItemSelectColumn
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.takeFavorites
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Quest form that lets the user select one item from a set of items arranged in [groups]. If
 *  [submitOnSelection] is true, concrete item selections submit immediately. Group selections
 *  always expand the group or retain their explicit confirmation flow.
 *
 *  At the top, ungrouped [topItems] are shown as a quick selection.
 *  If [favoriteKey] is not null, the last picked items saved for that key supplant the [topItems],
 *  i.e. are only padded by them. */
@Composable
inline fun <reified G : Group<I>, reified I> GroupedItemSelectQuestForm(
    noinline on: (QuestAction<I>) -> Unit,
    groups: List<G>,
    topItems: List<I>,
    noinline groupContent: @Composable (group: G) -> Unit,
    noinline itemContent: @Composable (item: I) -> Unit,
    modifier: Modifier = Modifier,
    favoriteKey: String? = null,
    noinline otherAnswers: @Composable (() -> List<AnswerItem>) = { emptyList() },
    preferences: Preferences = koinInject(),
    title: String = stringResource(LocalQuestType.current!!.title),
    submitOnSelection: Boolean = false,
) {
    val actualTopItems = remember(topItems) {
        if (favoriteKey != null) {
            preferences.getLastPicked<I>(favoriteKey)
                .takeFavorites(n = topItems.size, first = 1, pad = topItems)
        } else {
            topItems
        }
    }
    var selectedGroup by rememberSerializable { mutableStateOf<G?>(null) }
    var selectedItem by rememberSerializable { mutableStateOf<I?>(null) }

    var confirmSelectionOfGroupItem by remember { mutableStateOf<I?>(null) }
    val submitAnswer: (I) -> Unit = { value ->
        if (favoriteKey != null) preferences.addLastPicked(favoriteKey, value)
        on(Answer(value))
    }

    QuestForm(
        on = on,
        isComplete = selectedItem != null || selectedGroup?.item != null,
        onClickOk = {
            val group = selectedGroup
            val groupItem = group?.item
            val item = selectedItem
            if (item != null) {
                submitAnswer(item)
            } else if (groupItem != null) {
                confirmSelectionOfGroupItem = groupItem
            }
        },
        modifier = modifier,
        otherAnswers = otherAnswers,
        title = title
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalTextStyle provides MaterialTheme.typography.body2
            ) {
                Text(stringResource(Res.string.quest_select_hint_most_specific))
            }
            GroupedItemSelectColumn(
                groups = groups,
                topItems = actualTopItems,
                selectedItem = selectedItem,
                selectedGroup = selectedGroup,
                onSelect = { group, item ->
                    if (item != null) {
                        handleSingleChoiceSelection(
                            selection = item,
                            submitOnSelection = submitOnSelection,
                            onIntermediateSelection = {
                                selectedGroup = group
                                selectedItem = it
                            },
                            onTerminalAnswer = submitAnswer,
                        )
                    } else {
                        selectedGroup = group
                        selectedItem = null
                    }
                },
                groupContent = groupContent,
                itemContent = itemContent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    confirmSelectionOfGroupItem?.let { groupItem ->
        AreYouSureDialog(
            onDismissRequest = { confirmSelectionOfGroupItem = null },
            onConfirmed = {
                submitAnswer(groupItem)
            },
            text = { Text(stringResource(Res.string.quest_generic_item_confirmation)) }
        )
    }
}
