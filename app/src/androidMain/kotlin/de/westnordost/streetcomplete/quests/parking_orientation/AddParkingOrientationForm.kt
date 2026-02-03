package de.westnordost.streetcomplete.quests.parking_orientation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation
import de.westnordost.streetcomplete.osm.street_parking.ParkingPosition
import de.westnordost.streetcomplete.osm.street_parking.StreetParking
import de.westnordost.streetcomplete.osm.street_parking.painter
import de.westnordost.streetcomplete.quests.AItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import kotlinx.serialization.serializer

class AddParkingOrientationForm : AItemSelectQuestForm<ParkingOrientation, ParkingOrientation>() {
    override val items get() = ParkingOrientation.entries
    override val itemsPerRow = 3
    override val serializer = serializer<ParkingOrientation>()

    @Composable override fun ItemContent(item: ParkingOrientation) {
        val position = when (element.tags["parking"]) {
            "street_side" -> ParkingPosition.STREET_SIDE
            "on_kerb" -> ParkingPosition.OFF_STREET
            "half_on_kerb" -> ParkingPosition.HALF_ON_STREET
            else -> ParkingPosition.ON_STREET
        }

        ImageWithLabel(
            StreetParking.PositionAndOrientation(item, position)
                .painter(false, false),
            stringResource(item.title)
        )
    }
    override fun onClickOk(selectedItem: ParkingOrientation) {
        applyAnswer(selectedItem)
    }
}
