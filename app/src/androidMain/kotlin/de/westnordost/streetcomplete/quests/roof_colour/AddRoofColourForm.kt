package de.westnordost.streetcomplete.quests.roof_colour

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.quests.building_colour.getDrawable
import de.westnordost.streetcomplete.quests.building_colour.title
import de.westnordost.streetcomplete.quests.roof_shape.RoofShape
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.util.image.toPainter
import kotlinx.serialization.serializer

class AddRoofColourForm : AItemSelectQuestForm<RoofColour, RoofColour>() {

    override val items = RoofColour.entries
    override val serializer = serializer<RoofColour>()
    val iconResId by lazy {
        val shape = element.tags["roof:shape"]
        RoofShape.entries.firstOrNull { it.osmValue == shape }?.colorIconResId ?: R.drawable.ic_roof_colour_gabled
    }

    @Composable override fun ItemContent(item: RoofColour) {
        ImageWithLabel(
            item.getDrawable(LocalContext.current, iconResId).toPainter(),
            item.title
        )
    }

    override fun onClickOk(selectedItem: RoofColour) {
        applyAnswer(selectedItem)
    }
}
