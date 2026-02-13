package de.westnordost.streetcomplete.quests.shelter_type

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddShelterTypeForm : AItemSelectQuestForm<ShelterType, ShelterType>() {

    override val items = ShelterType.entries.filterNot { it == ShelterType.WEATHER_SHELTER }
    override val serializer = serializer<ShelterType>()

    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_shelter_type_is_weather_shelter) { applyAnswer(ShelterType.WEATHER_SHELTER) }
    )

    override val itemsPerRow = 3

    @Composable override fun ItemContent(item: ShelterType) {
        ImageWithLabel(painterResource(item.icon), stringResource(item.title))
    }

    override fun onClickOk(selectedItem: ShelterType) {
        applyAnswer(selectedItem)
    }
}

