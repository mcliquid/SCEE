package de.westnordost.streetcomplete.quests.surface

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_track_surface.BOTH_CYCLEWAY_SURFACE_KEY
import de.westnordost.streetcomplete.osm.cycleway_track_surface.CyclewayTrackSurfaceAnswer
import de.westnordost.streetcomplete.osm.cycleway_track_surface.CyclewayTrackSurfacePart
import de.westnordost.streetcomplete.osm.cycleway_track_surface.canApplyCyclewayTrackSurfaceAnswer
import de.westnordost.streetcomplete.osm.cycleway_track_surface.resolveCyclewayTrackSurfaceParts
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.icon
import de.westnordost.streetcomplete.osm.surface.title
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleItemSelectDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.common.quest.LocalMapTilt
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import de.westnordost.streetcomplete.ui.common.street_side_select.StreetSideForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private fun autoSubmitCyclewayTrackSurface(parts: List<CyclewayTrackSurfacePart>): Boolean =
    parts.count { it.needsSurvey } == 1 && parts.single { it.needsSurvey }.sourceKey != BOTH_CYCLEWAY_SURFACE_KEY

@Composable
fun AddRoadAttachedCyclewaySurfaceForm(
    on: (QuestAction<CyclewayTrackSurfaceAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    val parts = remember(element) { resolveCyclewayTrackSurfaceParts(element.tags) }
    var draft by rememberSerializable(element) { mutableStateOf(CyclewayTrackSurfaceAnswer(emptyMap())) }
    var selectedKeys by rememberSerializable(element) { mutableStateOf<List<String>>(emptyList()) }
    var confirmSplit by remember { mutableStateOf(false) }

    if (autoSubmitCyclewayTrackSurface(parts)) {
        val part = parts.single { it.needsSurvey }
        ItemSelectQuestForm(
            on = { action ->
                when (action) {
                    is Answer -> on(Answer(CyclewayTrackSurfaceAnswer(mapOf(part.key to action.value))))
                    is Action -> on(action)
                }
            },
            items = Surface.getSelectableValuesForWays(countryInfo.countryCode),
            itemContent = { surface ->
                val icon = surface.icon
                if (icon != null) {
                    ImageWithLabel(painterResource(icon), stringResource(surface.title))
                }
            },
            itemsPerRow = 3,
            submitOnSelection = true,
            title = stringResource(Res.string.quest_roadAttachedCyclewaySurface_title) + " — " + partLabel(part),
            headerContent = {
                TrackPartSides(parts, draft, geometry, countryInfo) {}
                ExistingTrackPartValue(part)
            },
        )
    } else {
        QuestForm(
            on = on,
            isComplete = canApplyCyclewayTrackSurfaceAnswer(draft, parts),
            hasChanges = draft.values.isNotEmpty(),
            onClickOk = {
                if (draft.splitBoth) confirmSplit = true else on(Answer(draft))
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.quest_partSmoothness_partial_hint))
                TrackPartSides(parts, draft, geometry, countryInfo) { key -> selectedKeys = listOf(key) }
                parts.filter { it.needsSurvey }.forEach { part ->
                    OutlinedButton(
                        onClick = { selectedKeys = listOf(part.key) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(partLabel(part))
                            ExistingTrackPartValue(part)
                            Text(
                                draft.values[part.key]?.let { stringResource(it.title) }
                                    ?: stringResource(Res.string.quest_partSmoothness_unanswered)
                            )
                        }
                    }
                    if (part.key in draft.values) {
                        TextButton(onClick = { draft = draft.copy(values = draft.values - part.key) }) {
                            Text(stringResource(Res.string.quest_partSmoothness_clear, partLabel(part)))
                        }
                    }
                }
                val openParts = parts.filter { it.needsSurvey }
                if (openParts.size > 1) {
                    TextButton(onClick = { selectedKeys = openParts.map { it.key } }) {
                        Text(stringResource(Res.string.quest_partSmoothness_same))
                    }
                }
                if (parts.any { it.sourceKey == BOTH_CYCLEWAY_SURFACE_KEY && it.needsSurvey }) {
                    Text(stringResource(Res.string.quest_partSmoothness_both_hint))
                    TextButton(onClick = { draft = draft.copy(splitBoth = !draft.splitBoth) }) {
                        Text(stringResource(
                            if (draft.splitBoth) Res.string.quest_partSmoothness_keep_both
                            else Res.string.quest_partSmoothness_split_both
                        ))
                    }
                }
            }
        }
    }

    if (selectedKeys.isNotEmpty()) {
        val selected = parts.filter { it.key in selectedKeys && it.needsSurvey }
        if (selected.isNotEmpty()) {
            val titleParts = selected.map { part ->
                when (part.key) {
                    "cycleway:left:surface" -> stringResource(Res.string.quest_partSmoothness_left)
                    "cycleway:right:surface" -> stringResource(Res.string.quest_partSmoothness_right)
                    else -> stringResource(Res.string.quest_partSmoothness_cycle)
                }
            }
            SimpleItemSelectDialog(
                onDismissRequest = { selectedKeys = emptyList() },
                columns = SimpleGridCells.Fixed(3),
                items = Surface.getSelectableValuesForWays(countryInfo.countryCode),
                title = titleParts.joinToString(" + "),
                onSelected = { surface ->
                    draft = draft.copy(values = draft.values + selected.associate { it.key to surface })
                    selectedKeys = emptyList()
                },
                itemContent = { surface ->
                    val icon = surface.icon
                    if (icon != null) {
                        ImageWithLabel(painterResource(icon), stringResource(surface.title))
                    }
                },
            )
        }
    }

    if (confirmSplit) {
        AlertDialog(
            onDismissRequest = { confirmSplit = false },
            title = { Text(stringResource(Res.string.quest_partSmoothness_split_both)) },
            text = {
                Column {
                    Text(stringResource(Res.string.quest_partSmoothness_split_preview))
                    parts.forEach { part ->
                        draft.values[part.key]?.let {
                            Text(partLabel(part) + ": " + stringResource(it.title))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmSplit = false; on(Answer(draft)) }) {
                    Text(stringResource(Res.string.quest_partSmoothness_confirm_split))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSplit = false }) {
                    Text(stringResource(Res.string.quest_partSmoothness_back))
                }
            },
        )
    }
}

@Composable private fun partLabel(part: CyclewayTrackSurfacePart): String = stringResource(
    when (part.key) {
        "cycleway:left:surface" -> Res.string.quest_partSmoothness_left
        "cycleway:right:surface" -> Res.string.quest_partSmoothness_right
        else -> Res.string.quest_partSmoothness_cycle
    }
)

@Composable private fun ExistingTrackPartValue(part: CyclewayTrackSurfacePart) {
    part.existingValue?.let {
        Text(stringResource(Res.string.quest_partSmoothness_existing, it))
    }
}

@Composable private fun TrackPartSides(
    parts: List<CyclewayTrackSurfacePart>,
    draft: CyclewayTrackSurfaceAnswer,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    onClick: (String) -> Unit,
) {
    val left = parts.find { it.key == "cycleway:left:surface" }
    val right = parts.find { it.key == "cycleway:right:surface" }
    if (left == null && right == null) return
    val orientation = remember(geometry) { geometry.getOrientationOrZero() }
    val rotation = LocalMapRotation.current
    Column {
        Text(stringResource(Res.string.quest_partSmoothness_direction))
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            StreetSideForm(
                value = Sides(draft.values[left?.key], draft.values[right?.key]),
                onValueChanged = {},
                getIllustrationPainter = { _, _ -> painterResource(Res.drawable.cycleway_track) },
                onClickSide = { side -> (if (side == Side.LEFT) left else right)?.takeIf { it.needsSurvey }?.let { onClick(it.key) } },
                geometryRotation = orientation,
                mapRotation = rotation,
                mapTilt = LocalMapTilt.current,
                isLeftHandTraffic = countryInfo.isLeftHandTraffic,
                isLeftSideEnabled = left?.needsSurvey == true,
                isRightSideEnabled = right?.needsSurvey == true,
                isLeftSideVisible = left != null,
                isRightSideVisible = right != null,
                getFloatingPainter = { value, _ ->
                    value?.icon?.let { painterResource(it) } ?: painterResource(Res.drawable.floating_question)
                },
            )
            Image(
                painterResource(Res.drawable.map_oneway_arrow),
                contentDescription = stringResource(Res.string.quest_partSmoothness_direction),
                modifier = Modifier.align(Alignment.Center).size(20.dp, 36.dp).rotate(orientation - rotation),
            )
        }
    }
}
