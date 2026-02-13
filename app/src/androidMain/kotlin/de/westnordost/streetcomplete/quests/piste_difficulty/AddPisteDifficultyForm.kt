package de.westnordost.streetcomplete.quests.piste_difficulty

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddPisteDifficultyForm : AItemSelectQuestForm<PisteDifficulty, PisteDifficulty>() {

    override val items get() = PisteDifficulty.entries.filter { it.isAvailable(countryInfo.countryCode) }
    override val itemsPerRow = 2
    override val moveFavoritesToFront = false
    override val serializer = serializer<PisteDifficulty>()

    @Composable override fun ItemContent(item: PisteDifficulty) {
        ImageWithLabel(painterResource(item.getIcon(countryInfo.countryCode)), stringResource(item.title))
    }

    override fun onClickOk(selectedItem: PisteDifficulty) {
        applyAnswer(selectedItem)
    }
}
