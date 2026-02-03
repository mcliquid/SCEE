package de.westnordost.streetcomplete.quests.map

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import kotlinx.serialization.serializer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class AddMapTypeForm : AItemSelectQuestForm<MapType, MapType>() {

    override val items = MapType.entries

    override val itemsPerRow = 1
    override val moveFavoritesToFront = false
    override val serializer = serializer<MapType>()

    @Composable override fun ItemContent(item: MapType) {
        ImageWithDescription(painterResource(item.icon), stringResource(item.title), stringResource(item.description))
    }

    override fun onClickOk(selectedItem: MapType) {
        applyAnswer(selectedItem)
    }
}
