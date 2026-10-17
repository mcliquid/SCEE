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
import de.westnordost.streetcomplete.osm.traffic_sign.confirmedChanges
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagChange
import de.westnordost.streetcomplete.osm.traffic_sign.TagProposal
import de.westnordost.streetcomplete.osm.traffic_sign.ProposalKind
import de.westnordost.streetcomplete.osm.traffic_sign.ConfirmedChanges
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import de.westnordost.streetcomplete.ui.util.ClipCirclePainter
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.traffic_signs.TrafficSignCatalogues
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignCatalogue
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignGroup
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignPickerKind
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
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.common.traffic_sign.TrafficSignGroupImage
import de.westnordost.streetcomplete.ui.common.traffic_sign.TrafficSignValueImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.produceState
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignAccess
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWayTagging
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns

/* The editor for the signs of both directions of a way. Shared by the traffic sign overlay and the
 * AddTrafficSignType quest, so that there is only one implementation of the picker, the images, the
 * direction selection and the preview. */

sealed interface TrafficSignCatalogueState {
    data object Loading : TrafficSignCatalogueState
    data object Failed : TrafficSignCatalogueState
    data class Ready(val catalogue: TrafficSignCatalogue) : TrafficSignCatalogueState
}

/** Loads the catalogue of [country] off the main thread (the first access loads it synchronously) */
@Composable
fun rememberTrafficSignCatalogueState(
    catalogues: TrafficSignCatalogues,
    country: String,
): TrafficSignCatalogueState {
    val state by produceState<TrafficSignCatalogueState>(TrafficSignCatalogueState.Loading) {
        value = withContext(Dispatchers.Default) { catalogues.get(country) }
            ?.let { TrafficSignCatalogueState.Ready(it) } ?: TrafficSignCatalogueState.Failed
    }
    return state
}

/** The access tags ([tags]) that hint at a sign and apply to the [directions] */
class NoneAccessWarning(val directions: List<TrafficSignDirection>, val tags: Map<String, String>)

private const val CHOICE_SEPARATOR = "\u0001"

/** The state of the editor. Nothing is written anywhere: [tagging] is only the proposal. */
class TrafficSignWayEditorState(
    val catalogue: TrafficSignCatalogue,
    val original: WaySignSelection,
    val selection: WaySignSelection,
    val tagging: TrafficSignWayTagging,
    val needsNoneConfirmation: Boolean,
    /** the tags of the way, to look for access tags that are at odds with "no signs" */
    val tags: Map<String, String> = emptyMap(),
    /** what the user chose for proposals: "id<SEP>fingerprint<SEP>1|0". A choice is only valid for
     *  the proposal it was made for (same fingerprint) */
    private val choiceList: List<String> = emptyList(),
    private val setChoices: (List<String>) -> Unit = {},
    private val setEncoded: (TrafficSignDirection?, String) -> Unit,
) {
    val isEdited: Boolean get() = selection.forward.encode() != original.forward.encode() || selection.backward.encode() != original.backward.encode()
    val hasChanges: Boolean get() = isEdited && confirmedChanges.applied.isNotEmpty()

    /* ------------------------- tags that follow from the signs: proposals ------------------------- */

    val proposals: List<TagProposal> get() = tagging.proposals

    private val choices: Map<String, Pair<String, Boolean>> get() = choiceList.mapNotNull {
        val parts = it.split(CHOICE_SEPARATOR)
        if (parts.size == 3) parts[0] to (parts[1] to (parts[2] == "1")) else null
    }.toMap()

    /** whether the user wants the proposal: the choice for exactly this proposal, otherwise the
     *  default (only proposals that add tags are pre-selected) */
    fun isAccepted(proposal: TagProposal): Boolean =
        choices[proposal.id]?.takeIf { it.first == proposal.fingerprint }?.second ?: proposal.isPreselected

    fun setProposalAccepted(proposal: TagProposal, accepted: Boolean) {
        val others = choiceList.filter { !it.startsWith(proposal.id + CHOICE_SEPARATOR) }
        setChoices(others + listOf(listOf(proposal.id, proposal.fingerprint, if (accepted) "1" else "0").joinToString(CHOICE_SEPARATOR)))
    }

    /** id -> fingerprint of the accepted proposals: this is what is confirmed and what the engine
     *  checks again when it writes */
    val confirmed: Map<String, String> get() = proposals.filter { isAccepted(it) }.associate { it.id to it.fingerprint }

    val confirmedChanges: ConfirmedChanges get() = tagging.confirmedChanges(confirmed)

    /** the proposals that are written (accepted, and the one they depend on is written too) */
    val appliedProposals: List<TagProposal> get() = confirmedChanges.let { c -> proposals.filter { p -> p.changes.all { it in c.applied } } }

    fun isEffective(proposal: TagProposal) = proposal in appliedProposals

    /** the preview for the dialog: what is not already listed as proposal */
    val confirmationPreview: TrafficSignPreviewModel get() = tagging.toPreviewModel().copy(adjusted = emptyList(), highwayChange = null)

    /** Set if "no signs" is newly chosen for a direction and the way has access tags that hint at
     *  a sign (see [TrafficSignAccess]): a hint only, the answer can be saved nevertheless. An
     *  existing "none" that is left as it is never leads to this. */
    val noneAccessWarning: NoneAccessWarning? get() {
        val directions = TrafficSignDirection.entries.filter {
            selection[it] == DirectionSelection.None && original[it] != DirectionSelection.None
        }
        if (directions.isEmpty()) return null
        val relevant = TrafficSignAccess.relevantTags(tags, directions)
        return if (relevant.isEmpty()) null else NoneAccessWarning(directions, relevant)
    }

    /** the signs to give to the tagging engine: only what the user changed */
    val waySigns: TrafficSignWaySigns get() = selection.toWaySigns(original)

    /** Existing values that cannot be read (other country, mixed countries, broken syntax) and are
     *  about to be replaced, with the value as it is tagged. Nothing of them is reused. */
    val replacedUnreadable: List<Pair<TrafficSignDirection, String>> get() =
        TrafficSignDirection.entries.mapNotNull { d ->
            val old = original[d]
            // "not surveyed" in the selection means: leave the existing value alone
            if (old is DirectionSelection.Signed && old.value.isUnparseable &&
                selection[d] != DirectionSelection.NotSurveyed && selection[d].encode() != old.encode()
            ) {
                d to old.encode()
            } else {
                null
            }
        }

    /** [direction] = null: both */
    internal fun set(direction: TrafficSignDirection?, selection: DirectionSelection) =
        setEncoded(direction, selection.encode())

    /** Whether the user should be asked before saving: relevant changes to other tags, replaced
     *  signs, or things that need checking. A plain new entry does not need a second confirmation. */
    val needsConfirmation: Boolean get() = needsNoneConfirmation || noneAccessWarning != null || replacedUnreadable.isNotEmpty() ||
        replacesSigns() || tagging.resolvedConflicts.any { it.key.startsWith("traffic_sign") } || tagging.unresolvedConflicts.isNotEmpty() ||
        // a conflict or removal that the user chose, or a change of the way type
        appliedProposals.any { it.kind != ProposalKind.NEW || it.changesWayType }

    private fun replacesSigns(): Boolean = TrafficSignDirection.entries.any { d ->
        val old = original[d]
        val new = selection[d]
        old is DirectionSelection.Signed && new is DirectionSelection.Signed &&
            !old.value.toOsmValue().equals("yes", ignoreCase = true) && old.value.toOsmValue() != new.value.toOsmValue()
    }
}

@Composable
fun rememberTrafficSignWayEditorState(way: Way, catalogue: TrafficSignCatalogue): TrafficSignWayEditorState {
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
    val tagging = remember(selection, way.tags, catalogue) { selection.toWaySigns(original).deriveWayTags(way.tags) }
    val needsNone = remember(selection, way.tags, catalogue) { selection.replacesSignsWithNone(way.tags, catalogue) }
    var choices by rememberSaveable { mutableStateOf(listOf<String>()) }
    return TrafficSignWayEditorState(catalogue, original, selection, tagging, needsNone, way.tags, choices, { choices = it }) { direction, encoded ->
        when (direction) {
            TrafficSignDirection.FORWARD -> forwardEncoded = encoded
            TrafficSignDirection.BACKWARD -> backwardEncoded = encoded
            null -> { forwardEncoded = encoded; backwardEncoded = encoded }
        }
    }
}

/** Shows the way and arrows for both directions on the map while the editor is shown */
@Composable
fun TrafficSignDirectionMarkers(geometry: ElementGeometry) {
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
}

/** The two direction rows, the direction hint, the preview of the consequences and the dialogs */
@Composable
fun TrafficSignWayEditor(
    state: TrafficSignWayEditorState,
    geometry: ElementGeometry,
    /** whether to show the way and the arrows of both directions on the map, with an explanation.
     *  The quest does not: it has the highlighted element and the global geometry arrows */
    showDirectionArrows: Boolean = true,
) {
    var editedDirection by rememberSaveable { mutableStateOf<TrafficSignDirection?>(null) }
    if (showDirectionArrows) TrafficSignDirectionMarkers(geometry)
    // like in the other forms (AddOnewayForm, SidepathChoiceGrid): the arrows in the form point the
    // way the way runs on the map, also if the map is rotated
    val geometryRotation = remember(geometry) { geometry.getOrientationOrZero() }
    val rotation = geometryRotation - LocalMapRotation.current

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (direction in TrafficSignDirection.entries) {
            DirectionRow(
                direction = direction,
                selection = state.selection[direction],
                hasExistingValue = !state.original[direction].isNotSurveyed(),
                onClick = { editedDirection = direction },
                rotation = rotation,
                // the same selection as "no signs" in the direction dialog; the confirmations
                // (access warning etc.) happen when the user presses OK
                onNone = { state.set(direction, DirectionSelection.None) },
            )
        }
        // one action for the common case: there are no signs in either direction
        TextButton(
            onClick = { state.set(null, DirectionSelection.None) },
            enabled = state.selection.forward != DirectionSelection.None || state.selection.backward != DirectionSelection.None,
        ) {
            Text(stringResource(Res.string.traffic_sign_overlay_none_both))
        }
        if (showDirectionArrows) {
            Text(
                stringResource(Res.string.traffic_sign_overlay_direction_hint),
                style = MaterialTheme.typography.caption,
            )
        }
        // the tags that will be saved, always shown before the user confirms
        if (state.isEdited && (state.tagging.changes.isNotEmpty() || state.confirmationPreview.hasContent)) {
            Divider()
            TrafficSignChangesPreview(state)
        }
    }

    editedDirection?.let { direction ->
        DirectionDialog(
            direction = direction,
            catalogue = state.catalogue,
            initial = state.selection[direction],
            hasExistingValue = !state.original[direction].isNotSurveyed(),
            onDismiss = { editedDirection = null },
            onConfirm = { newSelection, both ->
                editedDirection = null
                state.set(if (both) null else direction, newSelection)
            },
        )
    }
}

/** Asks for confirmation before saving: when signs are replaced by "none" and/or when other tags
 *  are changed or need checking, which is listed in [preview]. If [accessWarning] is set, the
 *  access tags that hint at a sign are shown and the tag editor can be opened ([onEditTags]). The
 *  answer can always be saved nevertheless; the dialog changes no tags. */
@Composable
fun TrafficSignConfirmDialog(
    showNoneWarning: Boolean,
    preview: TrafficSignPreviewModel?,
    unreadable: List<Pair<TrafficSignDirection, String>> = emptyList(),
    accessWarning: NoneAccessWarning? = null,
    /** the proposals that are saved together with the signs */
    proposals: List<TagProposal> = emptyList(),
    remainingExceptions: List<TrafficSignTagChange.Remove> = emptyList(),
    onEditTags: () -> Unit = {},
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val simpleNoneWarning = showNoneWarning && unreadable.isEmpty() && accessWarning == null && proposals.isEmpty()
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(stringResource(when {
                accessWarning != null -> Res.string.traffic_sign_access_warning_title
                simpleNoneWarning -> Res.string.traffic_sign_overlay_confirm_none_title
                else -> Res.string.traffic_sign_confirm_title
            }))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (accessWarning != null) {
                    val names = ArrayList<String>()
                    for (direction in accessWarning.directions) names.add(stringResource(direction.label))
                    val directions = names.joinToString(", ")
                    Text(stringResource(Res.string.traffic_sign_access_warning_text, directions))
                    for ((key, value) in accessWarning.tags) {
                        Text("$key=$value", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2)
                    }
                }
                for ((direction, value) in unreadable) {
                    Text(stringResource(Res.string.traffic_sign_confirm_unreadable, stringResource(direction.label), value))
                }
                if (showNoneWarning) Text(stringResource(Res.string.traffic_sign_overlay_confirm_none_text))
                if (proposals.isNotEmpty()) {
                    Text(stringResource(Res.string.traffic_sign_confirm_proposals), style = MaterialTheme.typography.body2)
                    for (p in proposals) for (change in p.changes) ChangeLine(change, p.kind)
                    if (remainingExceptions.isNotEmpty()) RemainingExceptions(remainingExceptions)
                }
                if (preview != null) TrafficSignPreview(preview)
            }
        },
        buttonRow = {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            if (accessWarning != null) {
                TextButton(onClick = onEditTags) { Text(stringResource(Res.string.traffic_sign_access_edit_tags)) }
            }
            TextButton(onClick = onConfirm) {
                Text(stringResource(when {
                    accessWarning != null -> Res.string.traffic_sign_access_save_anyway
                    simpleNoneWarning -> Res.string.traffic_sign_overlay_confirm_none_yes
                    else -> Res.string.traffic_sign_confirm_yes
                }))
            }
        },
    )
}

private fun DirectionSelection.isNotSurveyed() = this == DirectionSelection.NotSurveyed

/** the standard icons for the direction of a way, like the other forms use them */
private val TrafficSignDirection.rowIcon: DrawableResource get() = when (this) {
    TrafficSignDirection.FORWARD -> Res.drawable.oneway_yes
    TrafficSignDirection.BACKWARD -> Res.drawable.oneway_yes_reverse
}

/** the arrows for the markers on the map (overlay) */
internal val TrafficSignDirection.icon: DrawableResource get() = when (this) {
    TrafficSignDirection.FORWARD -> Res.drawable.ic_traffic_sign_direction_forward
    TrafficSignDirection.BACKWARD -> Res.drawable.ic_traffic_sign_direction_backward
}

@Composable
private fun DirectionRow(
    direction: TrafficSignDirection,
    selection: DirectionSelection,
    hasExistingValue: Boolean,
    onClick: () -> Unit,
    onNone: () -> Unit,
    rotation: Float,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val painter = painterResource(direction.rowIcon)
        Image(
            painter = remember(painter) { ClipCirclePainter(painter) },
            contentDescription = null,
            modifier = Modifier.size(48.dp).rotate(rotation),
        )
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
        TextButton(onClick = onNone, enabled = selection != DirectionSelection.None) {
            Text(stringResource(Res.string.traffic_sign_overlay_state_none))
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


/* ------------------------------------ tags that will be saved ------------------------------------ */

/** The signs and the tags that follow from them, each group with a checkbox. New tags are selected,
 *  conflicts and removals are not and only done if the user selects them. */
@Composable
fun TrafficSignChangesPreview(state: TrafficSignWayEditorState) {
    val tagging = state.tagging
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tagging.signChanges.isNotEmpty()) {
            Text(stringResource(Res.string.traffic_sign_proposal_signs_header), style = MaterialTheme.typography.subtitle2)
            for (change in tagging.signChanges) ChangeLine(change, ProposalKind.NEW)
        }
        if (state.proposals.isNotEmpty()) {
            Text(stringResource(Res.string.traffic_sign_proposal_tags_header), style = MaterialTheme.typography.subtitle2)
            for (p in state.proposals) {
                val dependency = state.proposals.firstOrNull { it.id == p.dependsOn }
                val enabled = dependency == null || state.isEffective(dependency)
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = enabled && state.isAccepted(p),
                        onCheckedChange = { state.setProposalAccepted(p, it) },
                        enabled = enabled,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        p.highwayChange(tagging)?.let { (old, new) ->
                            Text(
                                stringResource(Res.string.traffic_sign_overlay_way_type_change, stringResource(wayTypeName(old)), stringResource(wayTypeName(new))),
                                style = MaterialTheme.typography.body2,
                            )
                        }
                        for (change in p.changes) ChangeLine(change, p.kind)
                        when (p.kind) {
                            ProposalKind.CONFLICT -> Text(stringResource(Res.string.traffic_sign_proposal_conflict), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                            ProposalKind.REMOVAL -> Text(stringResource(Res.string.traffic_sign_proposal_removal), style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                            ProposalKind.NEW -> {}
                        }
                    }
                }
            }
            val remaining = state.confirmedChanges.remainingExceptions
            if (remaining.isNotEmpty()) RemainingExceptions(remaining)
        }
        val preview = state.confirmationPreview
        if (preview.hasContent) TrafficSignPreview(preview)
    }
}

private fun TagProposal.highwayChange(tagging: TrafficSignWayTagging): Pair<String, String>? =
    if (changesWayType) tagging.highwayChange else null

@Composable
private fun ChangeLine(change: TrafficSignTagChange, kind: ProposalKind) {
    val text = when (change) {
        is TrafficSignTagChange.Set ->
            if (change.previousValue == null) "${change.key}=${change.value}"
            else stringResource(Res.string.traffic_sign_proposal_changed, change.key, change.value, change.previousValue)
        is TrafficSignTagChange.Remove -> stringResource(Res.string.traffic_sign_proposal_removed, change.key, change.previousValue)
    }
    Text(
        text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.body2,
        color = if (kind == ProposalKind.NEW) MaterialTheme.colors.onSurface else MaterialTheme.colors.error,
    )
}

/** tags that stay and still allow what the sign forbids */
@Composable
private fun RemainingExceptions(remaining: List<TrafficSignTagChange.Remove>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.traffic_sign_proposal_remaining), style = MaterialTheme.typography.caption)
        for (r in remaining) Text("${r.key}=${r.previousValue}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.body2)
    }
}
