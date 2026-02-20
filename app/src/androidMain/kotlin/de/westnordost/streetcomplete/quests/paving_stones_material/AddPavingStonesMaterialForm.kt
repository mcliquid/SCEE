package de.westnordost.streetcomplete.quests.paving_stones_material

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class AddPavingStonesMaterialForm :
    AItemSelectQuestForm<PavingStonesMaterial, PavingStonesMaterial>() {

    override val items = PavingStonesMaterial.entries
    override val serializer = serializer<PavingStonesMaterial>()
    override val itemsPerRow = 3

    @Composable
    override fun ItemContent(item: PavingStonesMaterial) {
        ImageWithLabel(
            painter = painterResource(item.icon),
            label = stringResource(item.title)
        )
    }

    override fun onClickOk(selectedItem: PavingStonesMaterial) {
        applyAnswer(selectedItem)
    }
}
