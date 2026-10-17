package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.overlays.Action
import de.westnordost.streetcomplete.data.overlays.Edit
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignGroup
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignPickerKind
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignValue
import de.westnordost.streetcomplete.osm.traffic_sign.WaySignSelection
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.directionArrows
import de.westnordost.streetcomplete.osm.traffic_sign.isCompatibleModifierOf
import de.westnordost.streetcomplete.osm.traffic_sign.parseTrafficSignValue
import de.westnordost.streetcomplete.osm.traffic_sign.toValueOrNull
import de.westnordost.streetcomplete.osm.traffic_sign.withMainSign
import de.westnordost.streetcomplete.osm.traffic_sign.withModifier
import de.westnordost.streetcomplete.osm.traffic_sign.withoutGroup
import de.westnordost.streetcomplete.osm.traffic_sign.withoutModifier
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.AlertDialog
import de.westnordost.streetcomplete.ui.common.dialogs.ScrollableAlertDialog
import de.westnordost.streetcomplete.ui.common.overlay.OverlayForm
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.common.traffic_sign.TrafficSignGroupImage
import de.westnordost.streetcomplete.ui.common.traffic_sign.TrafficSignValueImage
import de.westnordost.streetcomplete.util.ktx.isSplittable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val COUNTRY = "DE"

private sealed interface CatalogueState {
    data object Loading : CatalogueState
    data object Failed : CatalogueState
    data class Ready(val catalogue: TrafficSignCatalogue) : CatalogueState
}

@Composable
fun TrafficSignOverlayForm(
    on: (OverlayAction) -> Unit,
    way: Way,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    catalogues: TrafficSignCatalogues = koinInject(),
) {
    if (countryInfo.countryCode != COUNTRY) {
        OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_germany_only))
        }
        return
    }
    // the first access to the catalogue loads it synchronously
    val state by produceState<CatalogueState>(CatalogueState.Loading) {
        value = withContext(Dispatchers.Default) { catalogues.get(COUNTRY) }
            ?.let { CatalogueState.Ready(it) } ?: CatalogueState.Failed
    }
    when (val s = state) {
        CatalogueState.Loading -> OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_loading))
        }
        CatalogueState.Failed -> OverlayForm(on = on, isComplete = false, hasChanges = false, onClickOk = {}) {
            Text(stringResource(Res.string.traffic_sign_overlay_catalogue_missing))
        }
        is CatalogueState.Ready -> TrafficSignOverlayFormContent(on, way, geometry, s.catalogue)
    }
}

@Composable
private fun TrafficSignOverlayFormContent(
    on: (OverlayAction) -> Unit,
    way: Way,
    geometry: ElementGeometry,
    catalogue: TrafficSignCatalogue,
) {
    val original = remember(way, catalogue) { WaySignSelection.of(way.tags, catalogue) }
    // the selection is saved as text (traffic_sign values), "" = not surveyed
    var forwardEncoded by rememberSaveable { mutableStateOf(original.forward.encode()) }
    var backwardEncoded by rememberSaveable { mutableStateOf(original.backward.encode()) }
    val selection = remember(forwardEncoded, backwardEncoded, catalogue) {
        WaySignSelection(
            DirectionSelection.decode(forwardEncoded, catalogue),
            DirectionSelection.decode(backwardEncoded, catalogue),
        )
    }
    val isEdited = forwardEncoded != original.forward.encode() || backwardEncoded != original.backward.encode()

    // the proposal is only computed; nothing is changed before the user confirms
    val tagging = remember(selection, way.tags, catalogue) { selection.toWaySigns().deriveWayTags(way.tags) }
    val hasChanges = isEdited && tagging.changes.isNotEmpty()
    val needsNoneConfirmation = remember(selection, way.tags, catalogue) {
        selection.replacesSignsWithNone(way.tags, catalogue)
    }

    var editedDirection by rememberSaveable { mutableStateOf<TrafficSignDirection?>(null) }
    var confirmNone by rememberSaveable { mutableStateOf(false) }

    val mapMarkersCallback = LocalMapMarkersCallback.current
    LaunchedEffect(geometry) {
        val markers = mutableListOf(Marker(geometry))
        val line = (geometry as? ElementPolylinesGeometry)?.polylines?.firstOrNull()
        if (line != null) {
            // Arrows at several places along the real geometry, each rotated like the way segment
            // it is on, so that they follow curves. Forward: solid, backward: hollow.
            for (direction in TrafficSignDirection.entries) {
                for (arrow in line.directionArrows(direction)) {
                    markers += Marker(
                        geometry = ElementPointGeometry(arrow.position),
                        icon = direction.icon,
                        rotation = arrow.bearing,
                    )
                }
            }
        }
        mapMarkersCallback?.invoke(markers)
    }
    DisposableEffect(Unit) {
        onDispose { mapMarkersCallback?.invoke(emptyList()) }
    }

    fun confirm() {
        val changes = StringMapChangesBuilder(way.tags)
        tagging.applyTo(changes)
        on(Edit(UpdateElementTagsAction(way, changes.create())))
    }

    OverlayForm(
        on = on,
        isComplete = isEdited,
        hasChanges = hasChanges,
        onClickOk = { if (needsNoneConfirmation) confirmNone = true else confirm() },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (direction in TrafficSignDirection.entries) {
                DirectionRow(
                    direction = direction,
                    selection = selection[direction],
                    hasExistingValue = !original[direction].isNotSurveyed(),
                    onClick = { editedDirection = direction },
                )
            }
            Text(
                stringResource(Res.string.traffic_sign_overlay_direction_hint),
                style = MaterialTheme.typography.caption,
            )
            if (isEdited) {
                Divider()
                Text(
                    stringResource(Res.string.traffic_sign_overlay_preview_title),
                    style = MaterialTheme.typography.subtitle2,
                )
                TrafficSignPreview(tagging.toPreviewModel())
            }
            Divider()
            Text(
                stringResource(Res.string.traffic_sign_overlay_scope_hint),
                style = MaterialTheme.typography.caption,
            )
            if (way.isSplittable()) {
                TextButton(onClick = { on(Action.SplitWay) }) {
                    Text(stringResource(Res.string.traffic_sign_overlay_split_way))
                }
            }
        }
    }

    editedDirection?.let { direction ->
        DirectionDialog(
            direction = direction,
            catalogue = catalogue,
            initial = selection[direction],
            hasExistingValue = !original[direction].isNotSurveyed(),
            onDismiss = { editedDirection = null },
            onConfirm = { newSelection, both ->
                editedDirection = null
                val encoded = newSelection.encode()
                if (both) {
                    forwardEncoded = encoded
                    backwardEncoded = encoded
                } else if (direction == TrafficSignDirection.FORWARD) {
                    forwardEncoded = encoded
                } else {
                    backwardEncoded = encoded
                }
            },
        )
    }

    if (confirmNone) {
        AlertDialog(
            onDismissRequest = { confirmNone = false },
            title = { Text(stringResource(Res.string.traffic_sign_overlay_confirm_none_title)) },
            text = { Text(stringResource(Res.string.traffic_sign_overlay_confirm_none_text)) },
            buttonRow = {
                TextButton(onClick = { confirmNone = false }) { Text(stringResource(Res.string.cancel)) }
                TextButton(onClick = { confirmNone = false; confirm() }) {
                    Text(stringResource(Res.string.traffic_sign_overlay_confirm_none_yes))
                }
            },
        )
    }
}

private fun DirectionSelection.isNotSurveyed() = this == DirectionSelection.NotSurveyed

private val TrafficSignDirection.icon: DrawableResource get() = when (this) {
    TrafficSignDirection.FORWARD -> Res.drawable.ic_traffic_sign_direction_forward
    TrafficSignDirection.BACKWARD -> Res.drawable.ic_traffic_sign_direction_backward
}

@Composable
private fun DirectionRow(
    direction: TrafficSignDirection,
    selection: DirectionSelection,
    hasExistingValue: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(direction.icon), null, Modifier.height(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(direction.label), fontWeight = FontWeight.Bold)
            when (selection) {
                DirectionSelection.NotSurveyed -> Text(
                    stringResource(
                        if (hasExistingValue) Res.string.traffic_sign_overlay_state_keep
                        else Res.string.traffic_sign_overlay_state_not_surveyed
                    ),
                    color = MaterialTheme.colors.error.takeUnless { hasExistingValue }
                        ?: MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.medium)
                )
                DirectionSelection.None -> Text(stringResource(Res.string.traffic_sign_overlay_state_none))
                is DirectionSelection.Signed -> {
                    TrafficSignValueImage(selection.value, signHeight = 56.dp)
                    if (selection.value.isUnparseable) {
                        Text(
                            stringResource(Res.string.traffic_sign_overlay_unreadable_value),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }
            }
        }
    }
}

/** Dialog to enter the state and signs of one direction */
@Composable
private fun DirectionDialog(
    direction: TrafficSignDirection,
    catalogue: TrafficSignCatalogue,
    initial: DirectionSelection,
    hasExistingValue: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (DirectionSelection, applyToBoth: Boolean) -> Unit,
) {
    // 0 = not surveyed, 1 = none, 2 = signed
    var mode by rememberSaveable {
        mutableStateOf(when (initial) {
            DirectionSelection.NotSurveyed -> 0
            DirectionSelection.None -> 1
            is DirectionSelection.Signed -> 2
        })
    }
    // the draft is saved as text, too
    var draftEncoded by rememberSaveable {
        mutableStateOf((initial as? DirectionSelection.Signed)?.value?.takeIf { !it.isUnparseable }?.toOsmValue().orEmpty())
    }
    var both by rememberSaveable { mutableStateOf(false) }
    // (null = add main sign, n = add additional sign to group n)
    var adding by rememberSaveable { mutableStateOf<Int?>(null) }
    var showCatalogue by rememberSaveable { mutableStateOf(false) }

    val groups: List<TrafficSignGroup> = remember(draftEncoded, catalogue) {
        if (draftEncoded.isEmpty()) emptyList() else parseTrafficSignValue(draftEncoded, catalogue).groups
    }
    fun setGroups(newGroups: List<TrafficSignGroup>) {
        draftEncoded = newGroups.toValueOrNull(catalogue.country)?.toOsmValue().orEmpty()
    }
    val isUnusual = groups.any { g ->
        val main = g.main?.sign
        main != null && g.modifiers.any { m -> m.sign != null && !m.sign.isCompatibleModifierOf(main) }
    }

    val result: DirectionSelection? = when (mode) {
        0 -> DirectionSelection.NotSurveyed
        1 -> DirectionSelection.None
        // signs chosen: otherwise (e.g. an existing entry that can't be displayed) OK stays disabled
        else -> groups.toValueOrNull(catalogue.country)?.let { DirectionSelection.Signed(it) }
    }

    ScrollableAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(direction.label)) },
        content = {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModeRow(
                    selected = mode == 0,
                    label = stringResource(
                        if (hasExistingValue) Res.string.traffic_sign_overlay_state_keep
                        else Res.string.traffic_sign_overlay_state_not_surveyed
                    ),
                    description = stringResource(Res.string.traffic_sign_overlay_not_surveyed_description),
                    onClick = { mode = 0 },
                )
                ModeRow(
                    selected = mode == 1,
                    label = stringResource(Res.string.traffic_sign_overlay_state_none),
                    description = stringResource(Res.string.traffic_sign_overlay_none_description),
                    onClick = { mode = 1 },
                )
                ModeRow(
                    selected = mode == 2,
                    label = stringResource(Res.string.traffic_sign_overlay_state_signed),
                    description = null,
                    onClick = { mode = 2 },
                )
                if (mode == 2) {
                    if (initial is DirectionSelection.Signed && initial.value.isUnparseable && groups.isEmpty()) {
                        Text(
                            stringResource(Res.string.traffic_sign_overlay_unreadable_value),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                    groups.forEachIndexed { index, group ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TrafficSignGroupImage(group, catalogue.country, signHeight = 64.dp)
                            Row {
                                TextButton(onClick = { adding = index; showCatalogue = true }) {
                                    Text(stringResource(Res.string.traffic_sign_overlay_add_modifier))
                                }
                                TextButton(onClick = { setGroups(groups.withoutGroup(index)) }) {
                                    Text(stringResource(Res.string.traffic_sign_overlay_remove))
                                }
                            }
                            group.modifiers.forEachIndexed { modifierIndex, modifier ->
                                TextButton(onClick = { setGroups(groups.withoutModifier(index, modifierIndex)) }) {
                                    Text("${stringResource(Res.string.traffic_sign_overlay_remove)}: ${modifier.description}")
                                }
                            }
                        }
                        Divider()
                    }
                    TextButton(onClick = { adding = null; showCatalogue = true }) {
                        Text(stringResource(Res.string.traffic_sign_overlay_add_main))
                    }
                    if (isUnusual) {
                        Text(
                            stringResource(Res.string.traffic_sign_overlay_unusual_combination),
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { both = !both },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(both, { both = it })
                    Text(stringResource(Res.string.traffic_sign_overlay_same_both))
                }
            }
        },
        buttonRow = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) }
            TextButton(
                onClick = { result?.let { onConfirm(it, both) } },
                enabled = result != null,
            ) { Text(stringResource(Res.string.ok)) }
        },
    )

    if (showCatalogue) {
        val target = adding
        val mainSign = target?.let { groups.getOrNull(it)?.main?.sign }
        TrafficSignCatalogueDialog(
            catalogue = catalogue,
            kind = if (target == null) TrafficSignPickerKind.MAIN else TrafficSignPickerKind.MODIFIER,
            main = mainSign,
            onDismiss = { showCatalogue = false },
            onPick = { part ->
                showCatalogue = false
                setGroups(if (target == null) groups.withMainSign(part) else groups.withModifier(target, part))
                mode = 2
            },
        )
    }
}

@Composable
private fun ModeRow(selected: Boolean, label: String, description: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick)
        Column {
            Text(label, fontWeight = FontWeight.Bold)
            if (description != null) Text(description, style = MaterialTheme.typography.caption)
        }
    }
}
