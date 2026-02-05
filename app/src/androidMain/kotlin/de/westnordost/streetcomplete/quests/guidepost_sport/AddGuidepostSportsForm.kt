package de.westnordost.streetcomplete.quests.guidepost_sport

import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.quests.AItemsSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddGuidepostSportsForm : AItemsSelectQuestForm<GuidepostSport, Set<GuidepostSportsAnswer>>() {
    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_guidepost_sports_answer_simple) { confirmJustSimple() }
    )
    override val serializer = serializer<GuidepostSport>()

    override val items = GuidepostSport.selectableValues
    override val itemsPerRow = 3

    @Composable override fun ItemContent(item: GuidepostSport) {
        ImageWithLabel(painterResource(item.iconResId), stringResource(item.titleResId))
    }

    override fun onClickOk(selectedItems: Set<GuidepostSport>) {
        applyAnswer(selectedItems)
    }

    private fun confirmJustSimple() {
        activity?.let { AlertDialog.Builder(it)
            .setMessage(R.string.quest_guidepost_sports_answer_simple_description)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(setOf(IsSimpleGuidepost)) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
        }
    }
}
