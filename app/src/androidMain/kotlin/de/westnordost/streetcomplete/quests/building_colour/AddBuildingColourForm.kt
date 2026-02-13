package de.westnordost.streetcomplete.quests.building_colour

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.util.image.toPainter
import kotlinx.serialization.serializer

class AddBuildingColourForm : AItemSelectQuestForm<BuildingColour, BuildingColour>() {

    override val items = BuildingColour.entries
    override val serializer = serializer<BuildingColour>()

    @Composable override fun ItemContent(item: BuildingColour) {
        ImageWithLabel(
            item.getDrawable(LocalContext.current, R.drawable.ic_building_colour).toPainter(),
            item.title
        )
    }

    override fun onClickOk(selectedItem: BuildingColour) {
        applyAnswer(selectedItem)
    }
}
