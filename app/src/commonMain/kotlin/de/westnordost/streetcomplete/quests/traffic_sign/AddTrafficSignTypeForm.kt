package de.westnordost.streetcomplete.quests.traffic_sign

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignCatalogueState
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignConfirmDialog
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignWayEditor
import de.westnordost.streetcomplete.overlays.traffic_sign.rememberTrafficSignCatalogueState
import de.westnordost.streetcomplete.overlays.traffic_sign.rememberTrafficSignWayEditorState
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val COUNTRY = "DE"

/** The quest form around the shared traffic sign editor of the traffic sign overlay */
@Composable
fun AddTrafficSignTypeForm(
    on: (QuestAction<TrafficSignAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    catalogues: TrafficSignCatalogues = koinInject(),
) {
    when (val s = rememberTrafficSignCatalogueState(catalogues, COUNTRY)) {
        TrafficSignCatalogueState.Loading -> QuestForm(on = on, isComplete = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_loading))
        }
        TrafficSignCatalogueState.Failed -> QuestForm(on = on, isComplete = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_catalogue_missing))
        }
        is TrafficSignCatalogueState.Ready -> {
            val state = rememberTrafficSignWayEditorState(element as Way, s.catalogue)
            var confirm by rememberSaveable { mutableStateOf(false) }
            fun answer() = on(Answer(TrafficSignAnswer(state.waySigns, state.confirmed)))

            QuestForm(
                on = on,
                isComplete = state.isEdited,
                hasChanges = state.hasChanges,
                // a plain new entry is saved directly
                onClickOk = { if (state.needsConfirmation) confirm = true else answer() },
            ) {
                TrafficSignWayEditor(state, geometry, showDirectionArrows = false)
            }
            if (confirm) {
                TrafficSignConfirmDialog(
                    showNoneWarning = state.needsNoneConfirmation,
                    preview = state.confirmationPreview,
                    unreadable = state.replacedUnreadable,
                    accessWarning = state.noneAccessWarning,
                    proposals = state.appliedProposals,
                    remainingExceptions = state.confirmedChanges.remainingExceptions,
                    // nothing is saved when the tag editor is opened
                    onEditTags = { confirm = false; on(Action.EditTags) },
                    onCancel = { confirm = false },
                    onConfirm = { confirm = false; answer() },
                )
            }
        }
    }
}
