package de.westnordost.streetcomplete.quests.waterway_crossing

import androidx.compose.runtime.*
import androidx.compose.material.CircularProgressIndicator
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossing
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestSource
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.screens.main.bottom_sheet.WaterwayCrossingForm
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
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
    val quests: OsmQuestSource = koinInject()
    var crossing by remember(element, geometry) { mutableStateOf<WaterwayCrossing?>(null) }
    var loading by remember(element, geometry) { mutableStateOf(true) }
    LaunchedEffect(element, geometry) {
        crossing = withContext(Dispatchers.IO) {
            quests.resolveWaterwayCrossing(element.id, geometry.center).also { resolved ->
                // Shared resolve already ran detection; only ask getValidated to drop a stale pin.
                if (resolved == null) {
                    quests.getValidated(OsmQuest(AddWaterwayCrossing(), element.type, element.id, geometry))
                }
            }
        }
        loading = false
        if (crossing == null) on(Action.Dismiss)
    }
    val selected = crossing
    if (selected == null) {
        QuestForm(on = on, isComplete = false, onClickOk = {}) {
            if (loading) CircularProgressIndicator()
        }
    } else {
        WaterwayCrossingForm(selected, mapPosition,
            onDismiss = { on(Action.Dismiss) },
            onConfirmed = { on(Answer(it)) },
            onQuestAction = { on(it) })
    }
}
