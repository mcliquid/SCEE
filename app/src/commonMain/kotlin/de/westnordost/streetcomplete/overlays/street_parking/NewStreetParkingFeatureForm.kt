package de.westnordost.streetcomplete.overlays.street_parking

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.item_select.ItemSelectGrid
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Form to create a new node at the crosshair: either a parking (placed freely at the crosshair) or
 *  a traffic calming, for which the unchanged [LaneNarrowingTrafficCalmingForm] is shown */
@Composable
fun NewStreetParkingFeatureForm(
    on: (OverlayAction) -> Unit,
    geometry: ElementGeometry,
) {
    var selected by rememberSaveable { mutableStateOf<NewStreetParkingFeature?>(null) }

    if (selected == NewStreetParkingFeature.TRAFFIC_CALMING) {
        LaneNarrowingTrafficCalmingForm(on, null, geometry)
        return
    }

    OverlayForm(
        on = on,
        isComplete = selected == NewStreetParkingFeature.PARKING,
        hasChanges = selected != null,
        onClickOk = { on(Edit(createParkingNodeAction(geometry.center))) },
    ) {
        ItemSelectGrid(
            columns = SimpleGridCells.Fixed(2),
            items = NewStreetParkingFeature.entries,
            selectedItem = selected,
            onSelect = { selected = it },
            modifier = Modifier,
        ) {
            ImageWithLabel(painterResource(it.icon), stringResource(it.title))
        }
    }
}
