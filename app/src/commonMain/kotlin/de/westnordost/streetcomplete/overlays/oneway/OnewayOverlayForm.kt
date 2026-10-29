package de.westnordost.streetcomplete.overlays.oneway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.overlays.Action
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.osm.oneway.BicycleDirectionSource
import de.westnordost.streetcomplete.osm.oneway.OnewayIssue
import de.westnordost.streetcomplete.osm.oneway.TravelDirection
import de.westnordost.streetcomplete.osm.oneway.interpretOneway
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.theme.titleMedium
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Edits `oneway` and `oneway:bicycle`. Everything else (cycleway, access, conditional, ...) is
 *  kept and listed. Undocumented values and vehicle specific oneways are only shown, the tag editor
 *  stays available for those. */
@Composable
fun OnewayOverlayForm(on: (OverlayAction) -> Unit, element: Element) {
    val tags = element.tags
    val notEditable = remember(element) { getOnewayNotEditableReason(tags) }
    val interpretation = remember(element) { interpretOneway(tags) }
    val relatedTags = remember(element) { getRelatedOnewayTags(tags) }
    var state by remember(element) { mutableStateOf(getOnewayEditState(tags)) }
    val changes = remember(state) { if (notEditable == null) createOnewayTagChanges(tags, state) else null }
    val isConflicting = remember(changes) { changes != null && createsOnewayConflict(tags, changes) }

    OverlayForm(
        on = on,
        isComplete = changes != null && !isConflicting,
        hasChanges = changes != null,
        onClickOk = { changes?.let { on(Edit(UpdateElementTagsAction(element, it))) } },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.oneway_overlay_direction_hint), style = MaterialTheme.typography.body2)
            if (notEditable != null) {
                Text(stringResource(when (notEditable) {
                    OnewayNotEditableReason.UNKNOWN_VALUE -> Res.string.oneway_overlay_not_editable_value
                    OnewayNotEditableReason.VEHICLE_SPECIFIC -> Res.string.oneway_overlay_not_editable_vehicle
                }))
                Button(onClick = { on(Action.EditTags) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.quest_generic_answer_show_edit_tags))
                }
            } else {
                Text(stringResource(Res.string.oneway_overlay_general), style = MaterialTheme.typography.titleMedium)
                if (interpretation.isGeneralImplied && state.general == null) {
                    Text(stringResource(Res.string.oneway_overlay_roundabout_implied), style = MaterialTheme.typography.body2)
                }
                Choices(
                    options = listOf(
                        TravelDirection.FORWARD to Res.string.oneway_overlay_forward,
                        TravelDirection.BACKWARD to Res.string.oneway_overlay_backward,
                        TravelDirection.BOTH to Res.string.oneway_overlay_both,
                    ),
                    selected = state.general,
                    onSelect = { state = state.copy(general = it) },
                )
                Text(stringResource(Res.string.oneway_overlay_bicycle), style = MaterialTheme.typography.titleMedium)
                Choices(
                    options = listOf(
                        null to Res.string.oneway_overlay_bicycle_follow,
                        TravelDirection.FORWARD to Res.string.oneway_overlay_forward,
                        TravelDirection.BACKWARD to Res.string.oneway_overlay_backward,
                        TravelDirection.BOTH to Res.string.oneway_overlay_both,
                    ),
                    selected = state.bicycle,
                    onSelect = { state = state.copy(bicycle = it) },
                )
                if (state.bicycle == null) {
                    val source = when (interpretation.bicycleSource) {
                        BicycleDirectionSource.LEGACY_OPPOSITE, BicycleDirectionSource.INFERRED_CONTRAFLOW ->
                            Res.string.oneway_overlay_bicycle_inferred
                        else -> null
                    }
                    if (source != null) Text(stringResource(source), style = MaterialTheme.typography.body2)
                }
                if (interpretation.bicycle == null) {
                    Text(stringResource(Res.string.oneway_overlay_no_bicycle_access), style = MaterialTheme.typography.body2)
                }
            }
            if (isConflicting) {
                Text(stringResource(Res.string.oneway_overlay_would_conflict), color = MaterialTheme.colors.error)
            } else if (OnewayIssue.CONFLICT in interpretation.issues) {
                Text(stringResource(Res.string.oneway_overlay_conflict), color = MaterialTheme.colors.error)
            }
            if (interpretation.isConditional) {
                Text(stringResource(Res.string.oneway_overlay_conditional), style = MaterialTheme.typography.body2)
            }
            if (relatedTags.isNotEmpty()) {
                Text(
                    stringResource(Res.string.oneway_overlay_kept_tags) + "\n" +
                        relatedTags.joinToString("\n") { "${it.first} = ${it.second}" },
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

@Composable
private fun Choices(
    options: List<Pair<TravelDirection?, StringResource>>,
    selected: TravelDirection?,
    onSelect: (TravelDirection?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, text) in options) {
            if (value == selected) {
                Button(onClick = { onSelect(value) }) { Text(stringResource(text)) }
            } else {
                OutlinedButton(onClick = { onSelect(value) }) { Text(stringResource(text)) }
            }
        }
    }
}
