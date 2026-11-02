package de.westnordost.streetcomplete.overlays.street_parking

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.ContentAlpha
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.createChanges
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestController
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.visiblequests.VisibleEditTypeSource
import de.westnordost.streetcomplete.quests.parking_type.icon
import de.westnordost.streetcomplete.quests.parking_type.title
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.bottom_sheet.TagEditorQuestFormContainer
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Read-only summary of a parking lot plus the parking quests that apply to it. Answering one of
 *  those quests is saved as a normal tag edit of this overlay. */
@Composable
fun ParkingLotOverlayForm(
    on: (OverlayAction) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    questTypeRegistry: QuestTypeRegistry = koinInject(),
    visibleEditTypeSource: VisibleEditTypeSource = koinInject(),
    osmQuestController: OsmQuestController = koinInject(),
) {
    val questTypes = remember {
        PARKING_LOT_QUEST_TYPE_NAMES
            .mapNotNull { questTypeRegistry.getByName(it) as? OsmElementQuestType<*> }
            .filter { visibleEditTypeSource.isVisible(it) }
    }
    val quests by produceState<List<OsmQuest>?>(null, element) {
        value = withContext(Dispatchers.IO) {
            osmQuestController.createQuestsForElement(element, geometry, questTypes)
        }
    }
    var shownQuest by remember(element) { mutableStateOf<OsmQuest?>(null) }

    val quest = shownQuest
    if (quest != null) {
        TagEditorQuestFormContainer(
            questType = quest.type,
            onDismiss = { shownQuest = null },
            questElement = element,
            geometry = geometry,
            countryInfo = countryInfo,
        ) { newTags ->
            val changes = newTags.createChanges(element.tags).create()
            if (!changes.isEmpty()) on(Edit(UpdateElementTagsAction(element, changes)))
        }
        return
    }

    OverlayForm(
        on = on,
        isComplete = false,
        hasChanges = false,
        onClickOk = {},
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val parkingType = getParkingType(element.tags)
            if (parkingType != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(painterResource(parkingType.icon), null, Modifier.size(48.dp))
                    Text(stringResource(parkingType.title), style = MaterialTheme.typography.subtitle1)
                }
            }
            for ((label, value) in getParkingLotSummary(element.tags)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(stringResource(label))
                    }
                    Text(value)
                }
            }
            val q = quests
            if (q != null) {
                if (q.isEmpty()) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(stringResource(Res.string.parking_lot_nothing_to_add), style = MaterialTheme.typography.body2)
                    }
                } else {
                    Text(stringResource(Res.string.parking_lot_add_info), style = MaterialTheme.typography.body2)
                    FlowRow(verticalArrangement = Arrangement.Center) {
                        for (it in q) {
                            IconButton({ shownQuest = it }, Modifier.size(56.dp)) {
                                Image(painterResource(it.type.icon), stringResource(it.type.title))
                            }
                        }
                    }
                }
            }
        }
    }
}
