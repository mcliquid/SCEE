package de.westnordost.streetcomplete.quests.street_cabinet

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddStreetCabinetTypeForm : AItemSelectQuestForm<StreetCabinetType, StreetCabinetType>() {

    override val items = StreetCabinetType.entries
    override val itemsPerRow = 4
    override val moveFavoritesToFront = false
    override val serializer = serializer<StreetCabinetType>()

    @Composable override fun ItemContent(item: StreetCabinetType) {
        ImageWithLabel(painterResource(item.iconResId), stringResource(item.titleResId))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        element.tags["operator"]?.let { setTitle(resources.getString((questType as OsmElementQuestType<*>).getTitle(element.tags)) + " ($it)") }
    }

    override fun onClickOk(selectedItem: StreetCabinetType) {
        applyAnswer(selectedItem)
    }
}
