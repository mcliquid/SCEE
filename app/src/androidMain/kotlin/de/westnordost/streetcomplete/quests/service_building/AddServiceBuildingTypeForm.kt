package de.westnordost.streetcomplete.quests.service_building

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.osm.building.description
import de.westnordost.streetcomplete.quests.AGroupedItemSelectQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithDescription
import kotlinx.serialization.serializer

class AddServiceBuildingTypeForm : AGroupedItemSelectQuestForm<ServiceBuildingTypeCategory, ServiceBuildingType, ServiceBuildingType>() {

    override val otherAnswers = listOf(
        AnswerItem(R.string.quest_disused) { applyAnswer(ServiceBuildingType.DISUSED) }
    )

    override val topItems = listOf(
        ServiceBuildingType.MINOR_SUBSTATION,
        ServiceBuildingType.GAS_PRESSURE_REGULATION,
        ServiceBuildingType.VENTILATION_SHAFT,
        ServiceBuildingType.WATER_WELL,
        ServiceBuildingType.HEATING,
    )

    override val groups = ServiceBuildingTypeCategory.entries

    override val serializer = serializer<ServiceBuildingType>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        element.tags["operator"]?.let { setTitle(resources.getString((questType as OsmElementQuestType<*>).getTitle(element.tags)) + " ($it)") }
    }

    @Composable override fun GroupContent(item: ServiceBuildingTypeCategory) {
        ImageWithDescription(
            painter = painterResource(item.iconResId),
            title = stringResource(item.titleResId),
            description = null,
            imageSize = DpSize(48.dp, 48.dp)
        )
    }

    @Composable override fun ItemContent(item: ServiceBuildingType) {
        ImageWithDescription(
            painter = painterResource(item.iconResId),
            title = stringResource(item.titleResId),
            description = item.descriptionResId?.let { stringResource(it) },
            imageSize = DpSize(48.dp, 48.dp),
        )
    }

    override fun onClickOk(value: ServiceBuildingType) {
        applyAnswer(value)
    }
}
