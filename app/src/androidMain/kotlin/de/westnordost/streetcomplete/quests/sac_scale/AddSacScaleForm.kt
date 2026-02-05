package de.westnordost.streetcomplete.quests.sac_scale

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import kotlinx.serialization.serializer

class AddSacScaleForm : AItemSelectQuestForm<SacScale, SacScale>() {

    override val items = SacScale.entries

    override val itemsPerRow = 1

    override val moveFavoritesToFront = false

    override val serializer = serializer<SacScale>()

    @Composable override fun ItemContent(item: SacScale) {
        ImageWithDescription(
            painterResource(item.imageResId),
            stringResource(item.titleResId),
            stringResource(item.descriptionResId)
        )
    }

    override fun onClickOk(selectedItem: SacScale) {
        applyAnswer(selectedItem)
    }
}

