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
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.addLastPicked
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ItemSelectGrid
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.takeFavorites
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Quest form that lets the user answer by selecting one item from a set of [items], displayed in a
 *  grid with a width of [itemsPerRow]. If [submitOnSelection] is true, selecting an item submits it
 *  as the answer immediately. Otherwise, the selection must be confirmed with the OK button.
 *  If [favoriteKey] is not null, moves the last picked items saved for that key to the front in the
 *  first row.
 *  */
@Composable
inline fun <reified I> ItemSelectQuestForm(
    noinline on: (QuestAction<I>) -> Unit,
    items: List<I>,
    noinline itemContent: @Composable (item: I) -> Unit,
    modifier: Modifier = Modifier,
    itemsPerRow: Int = 3,
    favoriteKey: String? = null,
    title: String = stringResource(LocalQuestType.current!!.title),
    noinline otherAnswers: @Composable (() -> List<AnswerItem>) = { emptyList() },
    preferences: Preferences = koinInject(),
    submitOnSelection: Boolean = false,
) {
    val reorderedItems = remember(items, itemsPerRow, favoriteKey) {
        if (favoriteKey != null && items.size > preferences.getInt(Prefs.FAVS_FIRST_MIN_LINES, 1) * 2 * itemsPerRow) {
            val favourites = preferences.getLastPicked<I>(favoriteKey)
                .takeFavorites(n = 2 * itemsPerRow, history = 50, first = 2)
            (favourites + items).distinct()
        } else {
            items
        }
    }
    var selectedItem by rememberSerializable { mutableStateOf<I?>(null) }
    val submitAnswer: (I) -> Unit = { value ->
        if (favoriteKey != null) preferences.addLastPicked(favoriteKey, value)
        on(Answer(value))
    }

    QuestForm(
        on = on,
        isComplete = selectedItem != null,
        onClickOk = { submitAnswer(selectedItem!!) },
        modifier = modifier,
        title = title,
        otherAnswers = otherAnswers,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(
                LocalContentAlpha provides ContentAlpha.medium,
                LocalTextStyle provides MaterialTheme.typography.body2
            ) {
                Text(stringResource(Res.string.quest_roofShape_select_one))
            }
            ItemSelectGrid(
                columns = SimpleGridCells.Fixed(itemsPerRow),
                items = reorderedItems,
                selectedItem = selectedItem,
                onSelect = { selection ->
                    handleSingleChoiceSelection(
                        selection = selection,
                        submitOnSelection = submitOnSelection,
                        onIntermediateSelection = { selectedItem = it },
                        onTerminalAnswer = submitAnswer,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                itemContent = itemContent
            )
        }
    }
}
