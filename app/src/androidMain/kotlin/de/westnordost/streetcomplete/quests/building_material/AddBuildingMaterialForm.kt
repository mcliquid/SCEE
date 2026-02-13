package de.westnordost.streetcomplete.quests.building_material

import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddBuildingMaterialForm : AItemSelectQuestForm<BuildingMaterial, BuildingMaterial>() {

    override val items = BuildingMaterial.entries
    override val serializer = serializer<BuildingMaterial>()

    @Composable override fun ItemContent(item: BuildingMaterial) {
        ImageWithLabel(painterResource(item.imageResId), stringResource(item.titleResId))
    }

    override val itemsPerRow = 3

    override fun onClickOk(selectedItem: BuildingMaterial) {
        applyAnswer(selectedItem)
    }
}
