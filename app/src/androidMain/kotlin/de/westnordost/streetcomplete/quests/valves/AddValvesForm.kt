package de.westnordost.streetcomplete.quests.valves

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemsSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddValvesForm : AItemsSelectQuestForm<Valves, Set<Valves>>() {

    override val items = Valves.entries
    override val itemsPerRow = 2
    override val moveFavoritesToFront = false
    override val serializer = serializer<Valves>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        imageSelector.cellLayoutId = R.layout.cell_icon_select_with_label_below
    }

    @Composable override fun ItemContent(item: Valves) {
        ImageWithLabel(painterResource(item.iconResId), stringResource(item.titleResId))
    }

    override fun onClickOk(selectedItems: Set<Valves>) {
        applyAnswer(selectedItems)
    }
}
