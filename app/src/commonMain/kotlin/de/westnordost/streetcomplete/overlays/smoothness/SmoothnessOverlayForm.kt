package de.westnordost.streetcomplete.overlays.smoothness

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.osm.changeToSteps
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.icon
import de.westnordost.streetcomplete.osm.surface.parseSurface
import de.westnordost.streetcomplete.osm.surface.title
import de.westnordost.streetcomplete.overlays.surface.isBothFootAndBicycleTrafficFilter
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.quests.smoothness.SmoothnessAnswerItem
import de.westnordost.streetcomplete.quests.smoothness.hasSmoothnessProfile
import de.westnordost.streetcomplete.quests.smoothness.selectableSmoothnessChoices
import de.westnordost.streetcomplete.quests.smoothness.title
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.ConfirmDiscardDialog
import de.westnordost.streetcomplete.ui.common.dialogs.InfoDialog
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleItemSelectDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.ktx.couldBeSteps
import de.westnordost.streetcomplete.util.locale.getLanguagesForFeatureDictionary
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable fun SmoothnessOverlayForm(
    on: (OverlayAction) -> Unit,
    element: Element,
    countryInfo: CountryInfo,
    featureDictionary: FeatureDictionary = koinInject(),
) {
    val state = remember(element) { parseSmoothnessOverlayState(element.tags) }
    var draft by rememberSerializable(element) { mutableStateOf(SmoothnessOverlayDraft.of(state)) }
    var showObstacleHint by remember { mutableStateOf(false) }
    var showSurfaceDialog by remember { mutableStateOf(false) }

    val labels = remember {
        val languages = getLanguagesForFeatureDictionary()
        Pair(
            featureDictionary.getById("highway/footway", languages)?.name.orEmpty(),
            featureDictionary.getById("highway/cycleway", languages)?.name.orEmpty(),
        )
    }
    // like in the surface overlay: only where both foot and bicycle traffic is mapped, and never
    // against an explicit segregated=no
    val canSplit = !state.isSegregated && element.tags["segregated"] != "no" &&
        isBothFootAndBicycleTrafficFilter.matches(element)

    @Composable fun otherAnswers(): List<AnswerItem> = listOfNotNull(
        if (!draft.isSegregated && state.whole.surface != null) {
            AnswerItem(stringResource(Res.string.quest_smoothness_wrong_surface)) { showSurfaceDialog = true }
        } else null,
        if (!draft.isSegregated && canSplit) {
            AnswerItem(stringResource(Res.string.overlay_path_surface_segregated)) {
                draft = draft.requestModeSwitch()
            }
        } else null,
        if (draft.isSegregated && draft.canSwitchBack) {
            AnswerItem(stringResource(Res.string.overlay_smoothness_edit_whole)) {
                draft = draft.requestModeSwitch()
            }
        } else null,
        AnswerItem(stringResource(Res.string.quest_smoothness_obstacle)) { showObstacleHint = true },
        if (element.couldBeSteps()) {
            AnswerItem(stringResource(Res.string.quest_generic_answer_is_actually_steps)) {
                val changes = StringMapChangesBuilder(element.tags)
                changes.changeToSteps()
                on(Edit(UpdateElementTagsAction(element, changes.create())))
            }
        } else null,
    )

    OverlayForm(
        on = on,
        isComplete = draft.isComplete,
        hasChanges = draft.hasChanges,
        onClickOk = {
            draft.toAnswer()?.let { answer ->
                val changes = StringMapChangesBuilder(element.tags)
                answer.applyTo(changes)
                on(Edit(UpdateElementTagsAction(element, changes.create())))
            }
        },
        otherAnswers = { otherAnswers() },
    ) {
        if (draft.isSegregated) {
            PartsSmoothnessContent(state, draft, labels) { draft = it }
        } else {
            SingleSmoothnessContent(state, draft, onSetSurface = { showSurfaceDialog = true }) { draft = it }
        }
    }

    if (showSurfaceDialog) {
        SimpleItemSelectDialog(
            onDismissRequest = { showSurfaceDialog = false },
            columns = SimpleGridCells.Fixed(3),
            items = Surface.getSelectableValuesForWays(countryInfo.countryCode),
            onSelected = { draft = draft.selectNewSurface(it, parseSurface(state.whole.surface)) },
            itemContent = { item ->
                ImageWithLabel(
                    painter = item.icon?.let { painterResource(it) },
                    label = stringResource(item.title),
                    imageSize = DpSize(96.dp, 96.dp),
                )
            },
        )
    }

    if (draft.pendingModeSwitch) {
        ConfirmDiscardDialog(
            onDismissRequest = { draft = draft.cancelModeSwitch() },
            onConfirmed = { draft = draft.confirmModeSwitch() },
        )
    }

    if (showObstacleHint) {
        InfoDialog(
            onDismissRequest = { showObstacleHint = false },
            text = { Text(stringResource(Res.string.quest_smoothness_obstacle_hint)) }
        )
    }
}

@Composable private fun SingleSmoothnessContent(
    state: SmoothnessOverlayState,
    draft: SmoothnessOverlayDraft,
    onSetSurface: () -> Unit,
    onDraftChange: (SmoothnessOverlayDraft) -> Unit,
) {
    val surface = draft.newSurface?.osmValue ?: state.whole.surface
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SurfaceInfo(surface = surface, isNew = draft.newSurface != null, fromWholeWay = false)
        if (surface == null) {
            OutlinedButton(onClick = onSetSurface) {
                Text(stringResource(Res.string.overlay_smoothness_set_surface))
            }
        }
        SmoothnessSelector(
            smoothness = state.whole.smoothness,
            selection = draft.whole,
            surface = surface,
            onSelectionChange = { onDraftChange(draft.copy(whole = it)) },
        )
    }
}

@Composable private fun PartsSmoothnessContent(
    state: SmoothnessOverlayState,
    draft: SmoothnessOverlayDraft,
    labels: Pair<String, String>,
    onDraftChange: (SmoothnessOverlayDraft) -> Unit,
) {
    val common = state.whole.smoothness.validValue
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.hasSidewalkTags) {
            Text(
                stringResource(Res.string.overlay_smoothness_sidewalk_hint),
                style = MaterialTheme.typography.caption,
            )
        }
        SmoothnessPart(labels.first, state.footway, draft.footway, common) { onDraftChange(draft.copy(footway = it)) }
        SmoothnessPart(labels.second, state.cycleway, draft.cycleway, common) { onDraftChange(draft.copy(cycleway = it)) }
    }
}

@Composable private fun SmoothnessPart(
    label: String,
    part: SmoothnessPartState,
    selection: SmoothnessSelection,
    common: Smoothness?,
    onSelectionChange: (SmoothnessSelection) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.subtitle1,
        )
        SurfaceInfo(surface = part.surface, isNew = false, fromWholeWay = part.surfaceFromWholeWay)
        SmoothnessSelector(
            smoothness = part.smoothness,
            selection = selection,
            surface = part.surface,
            onSelectionChange = onSelectionChange,
        )
        // the common smoothness is only a suggestion, it is not taken over unless chosen
        if (common != null && selection.selected == null) {
            TextButton(onClick = { onSelectionChange(selection.select(common)) }) {
                Text(stringResource(Res.string.overlay_smoothness_use_common, stringResource(common.title)))
            }
        }
    }
}

@Composable private fun SurfaceInfo(surface: String?, isNew: Boolean, fromWholeWay: Boolean) {
    val style = MaterialTheme.typography.body2.copy(
        color = LocalContentColor.current.copy(alpha = ContentAlpha.medium)
    )
    if (surface == null) {
        Text(stringResource(Res.string.overlay_smoothness_surface_unknown), style = style)
        return
    }
    val parsed = parseSurface(surface)
    val name = if (parsed != null && parsed != Surface.UNSUPPORTED) stringResource(parsed.title) else surface
    val text = when {
        isNew -> Res.string.overlay_smoothness_surface_new
        fromWholeWay -> Res.string.overlay_smoothness_surface_whole_way
        else -> Res.string.overlay_smoothness_surface
    }
    Text(stringResource(text, name), style = style)
    if (!hasSmoothnessProfile(surface)) {
        Text(stringResource(Res.string.overlay_smoothness_surface_generic), style = style)
    }
}

/** Card showing the selected smoothness; tapping it opens the full-width selection for the
 *  given surface. */
@Composable private fun SmoothnessSelector(
    smoothness: ParsedSmoothness?,
    selection: SmoothnessSelection,
    surface: String?,
    onSelectionChange: (SmoothnessSelection) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (smoothness is ParsedSmoothness.Invalid) {
        Text(
            text = stringResource(Res.string.overlay_smoothness_invalid, smoothness.raw),
            color = MaterialTheme.colors.error,
            style = MaterialTheme.typography.body2,
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.2f), MaterialTheme.shapes.medium)
            .clickable { showDialog = true }
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val selected = selection.selected
        if (selected != null) {
            SmoothnessAnswerItem(selected, surface)
        } else {
            Text(
                text = stringResource(Res.string.overlay_smoothness_select),
                style = MaterialTheme.typography.body1,
            )
        }
    }
    if (selection.original != null && selection.selected == selection.original) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selection.confirmed) {
                Text(
                    text = stringResource(Res.string.overlay_smoothness_confirmed),
                    style = MaterialTheme.typography.body2,
                )
            } else {
                TextButton(onClick = { onSelectionChange(selection.confirm()) }) {
                    Text(stringResource(Res.string.overlay_smoothness_confirm))
                }
            }
        }
    }

    if (showDialog) {
        SimpleItemSelectDialog(
            onDismissRequest = { showDialog = false },
            columns = SimpleGridCells.Fixed(1),
            items = remember(surface, selection.original) {
                selectableSmoothnessChoices(surface, selection.original).map { it.value }
            },
            onSelected = { onSelectionChange(selection.select(it)) },
            itemContent = { SmoothnessAnswerItem(it, surface) },
        )
    }
}
