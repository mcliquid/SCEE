package de.westnordost.streetcomplete.quests.waterway_crossing

import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.material.CircularProgressIndicator
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.findWaterwayCrossings
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossing
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.bottom_sheet.WaterwayCrossingForm
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@Composable
fun WaterwayCrossingQuestForm(
    on: (QuestAction<ElementEditAction>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    mapPosition: LatLon,
) {
    val source: MapDataWithEditsSource = koinInject()
    var crossing by remember(element, geometry) { mutableStateOf<WaterwayCrossing?>(null) }
    var loading by remember(element, geometry) { mutableStateOf(true) }
    LaunchedEffect(element, geometry) {
        crossing = withContext(Dispatchers.IO) {
            findWaterwayCrossings(source.getMapDataWithGeometry(geometry.center.enclosingBoundingBox(500.0)),
                geometry.center, 0.5).singleOrNull { it.road.id == element.id }
        }
        loading = false
    }
    val selected = crossing
    if (selected == null) {
        QuestForm(on = on, isComplete = false, onClickOk = {}) {
            if (loading) CircularProgressIndicator()
            else Text(stringResource(Res.string.waterway_crossing_unavailable))
        }
    } else {
        WaterwayCrossingForm(selected, mapPosition,
            onDismiss = { on(Action.Dismiss) },
            onConfirmed = { on(Answer(it)) },
            onQuestAction = { on(it) })
    }
}
