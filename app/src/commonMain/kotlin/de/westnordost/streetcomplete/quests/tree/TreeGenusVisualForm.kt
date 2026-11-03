package de.westnordost.streetcomplete.quests.tree

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.addLastPicked
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.tree_genus_search
import de.westnordost.streetcomplete.resources.tree_genus_no_results
import de.westnordost.streetcomplete.resources.tree_genus_advanced
import de.westnordost.streetcomplete.resources.tree_genus_uncertain
import de.westnordost.streetcomplete.resources.quest_leafType_tree_is_just_a_stump
import de.westnordost.streetcomplete.ui.common.TextField2
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.util.takeFavorites
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun TreeGenusVisualForm(on: (QuestAction<TreeAnswer>) -> Unit, onAdvanced: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    val prefs: Preferences = koinInject()
    val genera = remember {
        val favorites = prefs.getLastPicked<String>("AddTreeGenusForm").takeFavorites(20, 50, 1)
        TreeGenus.entries.sortedBy { favorites.indexOf("false§${it.osmValue}").takeIf { it >= 0 } ?: Int.MAX_VALUE }
    }
    val titles = TreeGenus.entries.associateWith { stringResource(it.title) }
    val aliases = TreeGenus.entries.associateWith { stringResource(it.aliases) }
    val visible = genera.filter { it.matches(search, titles.getValue(it), aliases.getValue(it)) }

    ItemSelectQuestForm(
        on = { action ->
            if (action is Answer) {
                val genus = action.value
                prefs.addLastPicked("AddTreeGenusForm", "false§${genus.osmValue}")
                on(Answer(genus.toAnswer(titles.getValue(genus))))
            } else on(action as Action)
        },
        items = visible,
        itemsPerRow = 3,
        submitOnSelection = true,
        showSelectionHint = false,
        itemContent = { genus ->
            ImageWithLabel(painterResource(genus.image), titles.getValue(genus))
        },
        otherAnswers = {
            listOf(
                AnswerItem(stringResource(Res.string.tree_genus_search)) { showSearch = !showSearch; search = "" },
                AnswerItem(stringResource(Res.string.tree_genus_advanced), onAdvanced),
                AnswerItem(stringResource(Res.string.tree_genus_uncertain)) { on(Action.CantSay) },
                AnswerItem(stringResource(Res.string.quest_leafType_tree_is_just_a_stump)) { on(Answer(NotTreeButStump)) },
            )
        },
        headerContent = {
            if (showSearch) TextField2(search, { search = it }, label = { Text(stringResource(Res.string.tree_genus_search)) },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (visible.isEmpty()) Text(stringResource(Res.string.tree_genus_no_results))
        },
    )
}
