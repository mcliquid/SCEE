package de.westnordost.streetcomplete.overlays.maxspeed

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.overlays.Action
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedIssue
import de.westnordost.streetcomplete.osm.maxspeed.Speed
import de.westnordost.streetcomplete.osm.maxspeed.interpretMaxSpeed
import de.westnordost.streetcomplete.quests.max_speed.MaxSpeedInput
import de.westnordost.streetcomplete.quests.max_speed.MaxSpeedSign
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.theme.titleMedium
import org.jetbrains.compose.resources.stringResource

/** Form to directly edit the numeric speed limit of a way, per direction if it is tagged per
 *  direction. Values the form cannot represent (none, walk, DE:urban, lanes, ...) are only shown,
 *  the tag editor stays available for those. Conditional and advisory tags are never touched. */
@Composable
fun MaxSpeedOverlayForm(on: (OverlayAction) -> Unit, element: Element, countryInfo: CountryInfo) {
    val original = remember(element) { getMaxSpeedEditState(element.tags) }
    var state by remember(element) { mutableStateOf(original) }
    val interpretation = remember(element) { interpretMaxSpeed(element.tags) }
    val changes = remember(state) {
        if (isMaxSpeedEditComplete(original, state)) createMaxSpeedTagChanges(element.tags, state) else null
    }
    // derived from exactly the changes that are saved on OK
    val preview = remember(changes) { createMaxSpeedTagPreview(element.tags, changes) }

    OverlayForm(
        on = on,
        isComplete = changes != null,
        hasChanges = changes != null,
        onClickOk = { changes?.let { on(Edit(UpdateElementTagsAction(element, it))) } },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                is MaxSpeedEditState.General -> SpeedEditor(
                    title = null,
                    speed = s.speed,
                    onChange = { state = MaxSpeedEditState.General(it) },
                    countryInfo = countryInfo,
                )
                is MaxSpeedEditState.Directional -> {
                    SpeedEditor(
                        title = stringResource(Res.string.maxspeed_overlay_forward, s.forward?.toOsmStringOrNull() ?: "–"),
                        speed = s.forward,
                        onChange = { state = s.copy(forward = it) },
                        countryInfo = countryInfo,
                    )
                    SpeedEditor(
                        title = stringResource(Res.string.maxspeed_overlay_backward, s.backward?.toOsmStringOrNull() ?: "–"),
                        speed = s.backward,
                        onChange = { state = s.copy(backward = it) },
                        countryInfo = countryInfo,
                    )
                }
                is MaxSpeedEditState.NotEditable -> {
                    val value = maxSpeedLabel(interpretation) { countryInfo.zoneSpeedUnitOf(it) } ?: "–"
                    Text(stringResource(Res.string.maxspeed_overlay_value, value), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.maxspeed_overlay_not_editable))
                    Button(onClick = { on(Action.EditTags) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.quest_generic_answer_show_edit_tags))
                    }
                }
            }
            for (change in preview.changes) {
                val text = when (change) {
                    is StringMapEntryAdd ->
                        stringResource(Res.string.maxspeed_overlay_tag_added, "${change.key} = ${change.value}")
                    is StringMapEntryModify ->
                        stringResource(Res.string.maxspeed_overlay_tag_changed, "${change.key} = ${change.valueBefore} → ${change.value}")
                    // the form currently never removes tags (ways with type codes are not editable)
                    is StringMapEntryDelete ->
                        stringResource(Res.string.maxspeed_overlay_type_removed, "${change.key} = ${change.valueBefore}")
                }
                Text(text, modifier = Modifier.fillMaxWidth())
            }
            for (issue in interpretation.issues) {
                val text = when (issue) {
                    MaxSpeedIssue.ZONE_SPEED_MISMATCH -> Res.string.maxspeed_overlay_issue_zone_mismatch
                    MaxSpeedIssue.UNRESTRICTED_IN_ZONE -> Res.string.maxspeed_overlay_issue_unrestricted_in_zone
                    MaxSpeedIssue.INVALID_VALUE -> Res.string.maxspeed_overlay_issue_invalid
                    MaxSpeedIssue.TYPE_CONFLICT -> Res.string.maxspeed_overlay_issue_type_conflict
                }
                Text(stringResource(text), color = MaterialTheme.colors.error)
            }
            if (preview.unchanged.isNotEmpty()) {
                val title = if (changes != null) Res.string.maxspeed_overlay_kept_tags else Res.string.maxspeed_overlay_current_tags
                Text(
                    stringResource(title) + "\n" +
                        preview.unchanged.joinToString("\n") { (key, value) -> "$key = $value" },
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SpeedEditor(
    title: String?,
    speed: Speed?,
    onChange: (Speed) -> Unit,
    countryInfo: CountryInfo,
) {
    val current = speed ?: Speed(null, countryInfo.speedUnits.first())
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium)
        MaxSpeedInput(
            type = MaxSpeedSign.Type.NORMAL,
            speed = current,
            onChangeSpeed = onChange,
            countryInfo = countryInfo,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            for (value in commonSpeedLimits(current.unit)) {
                val selected = current.value == value
                val onClick = { onChange(current.copy(value = value)) }
                if (selected) {
                    Button(onClick = onClick) { Text("$value") }
                } else {
                    OutlinedButton(onClick = onClick) { Text("$value") }
                }
            }
        }
    }
}

/** Every value is either complete or unchanged (a missing direction may stay missing) */
internal fun isMaxSpeedEditComplete(original: MaxSpeedEditState, state: MaxSpeedEditState): Boolean =
    when (state) {
        is MaxSpeedEditState.General -> state.speed?.isComplete() == true
        is MaxSpeedEditState.Directional -> {
            val o = original as? MaxSpeedEditState.Directional
            (state.forward == o?.forward || state.forward?.isComplete() == true) &&
                (state.backward == o?.backward || state.backward?.isComplete() == true)
        }
        is MaxSpeedEditState.NotEditable -> false
    }

private fun Speed.toOsmStringOrNull(): String? = if (isComplete()) toOsmString() else null
