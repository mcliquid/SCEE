package de.westnordost.streetcomplete.quests.trail_visibility

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import kotlinx.serialization.serializer

class AddTrailVisibilityForm : AItemSelectQuestForm<TrailVisibility, TrailVisibility>() {

    override val items = TrailVisibility.entries

    override val itemsPerRow = 2
    override val moveFavoritesToFront = false
    override val serializer = serializer<TrailVisibility>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        imageSelector.cellLayoutId = R.layout.cell_labeled_icon_select_trail_visibility
    }

    @Composable override fun ItemContent(item: TrailVisibility) {
        ImageWithDescription(null, stringResource(item.titleResId), stringResource(item.descriptionResId))
    }

    override fun onClickOk(selectedItem: TrailVisibility) {
        applyAnswer(selectedItem)
    }
}
