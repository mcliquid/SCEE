package de.westnordost.streetcomplete.quests.via_ferrata_scale

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import kotlinx.serialization.serializer

class AddViaFerrataScaleForm : AItemSelectQuestForm<ViaFerrataScale, ViaFerrataScale>() {

    override val items = ViaFerrataScale.entries
    // optional: add quest_viaFerrataScale_hint text, but quest is already very long

    override val itemsPerRow = 1
    override val moveFavoritesToFront = false
    override val serializer = serializer<ViaFerrataScale>()

    @Composable override fun ItemContent(item: ViaFerrataScale) {
        ImageWithDescription(painterResource(item.imageResId), stringResource(item.titleResId), stringResource(item.descriptionResId))
    }

    override fun onClickOk(selectedItem: ViaFerrataScale) {
        applyAnswer(selectedItem)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        imageSelector.cellLayoutId = R.layout.cell_labeled_icon_select_via_ferrata_scale
    }
}
