package de.westnordost.streetcomplete.quests.artwork

import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinx.serialization.serializer
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel

class AddArtworkTypeForm : AItemSelectQuestForm<ArtworkType, ArtworkType>() {

    override val items = ArtworkType.entries
    override val itemsPerRow = 3
    override val serializer = serializer<ArtworkType>()

    @Composable override fun ItemContent(item: ArtworkType) {
        ImageWithLabel(painterResource(item.icon), stringResource(item.title))
    }

    override fun onClickOk(selectedItem: ArtworkType) {
        applyAnswer(selectedItem)
    }
}
