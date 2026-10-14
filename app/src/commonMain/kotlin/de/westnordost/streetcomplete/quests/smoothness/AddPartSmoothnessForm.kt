package de.westnordost.streetcomplete.quests.smoothness

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
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.dialogs.InfoDialog
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleItemSelectDialog
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
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

internal fun autoSubmitPartSmoothness(parts: List<SmoothnessPart>): Boolean =
    parts.size == 1 && parts.single().needsSurvey && parts.single().sourceKey != BOTH_SMOOTHNESS_KEY

@Composable
fun AddPartSmoothnessForm(
    on: (QuestAction<SmoothnessAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    val parts = remember(element) { resolveSmoothnessParts(element.tags, element.timestampEdited) }
    var draft by rememberSerializable(element) { mutableStateOf(PartSmoothnessAnswer(emptyMap())) }
    var selectedKeys by rememberSerializable(element) { mutableStateOf<List<String>>(emptyList()) }
    var confirmSplit by remember { mutableStateOf(false) }
    var showObstacleHint by remember { mutableStateOf(false) }
    val otherAnswers: @Composable () -> List<AnswerItem> = { listOf(
        AnswerItem(stringResource(Res.string.quest_smoothness_wrong_surface)) { on(Action.LeaveNote) },
        AnswerItem(stringResource(Res.string.quest_smoothness_obstacle)) { showObstacleHint = true },
    ) }

    if (autoSubmitPartSmoothness(parts)) {
        val part = parts.single()
        ItemSelectQuestForm(
            on = { action -> when (action) {
                is Answer -> on(Answer(PartSmoothnessAnswer(mapOf(part.key to action.value))))
                is Action -> on(action)
            } },
            items = smoothnessAnswersForSurface(part.surface),
            itemContent = { SmoothnessAnswerItem(it, part.surface) },
            itemsPerRow = 1,
            submitOnSelection = true,
            title = stringResource(Res.string.quest_partSmoothness_title) + " — " + partLabel(part),
            otherAnswers = otherAnswers,
            headerContent = {
                PartSides(parts, draft, geometry, countryInfo) {}
                ExistingPartValue(part)
            },
        )
    } else {
        QuestForm(
            on = on,
            isComplete = canApplyPartSmoothness(draft, parts),
            hasChanges = draft.values.isNotEmpty(),
            onClickOk = {
                if (draft.splitBoth) confirmSplit = true else on(Answer(draft))
            },
            otherAnswers = otherAnswers,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.quest_partSmoothness_partial_hint))
                PartSides(parts, draft, geometry, countryInfo) { key -> selectedKeys = listOf(key) }
                parts.forEach { part ->
                    OutlinedButton(
                        onClick = { selectedKeys = listOf(part.key) },
                        enabled = part.needsSurvey,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Text(partLabel(part))
                            ExistingPartValue(part)
                            Text(draft.values[part.key]?.let { stringResource(it.title) }
                                ?: stringResource(Res.string.quest_partSmoothness_unanswered))
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
                if (parts.any { it.sourceKey == BOTH_SMOOTHNESS_KEY }) {
                    Text(stringResource(Res.string.quest_partSmoothness_both_hint))
                    TextButton(onClick = { draft = draft.copy(splitBoth = !draft.splitBoth) }) {
                        Text(stringResource(if (draft.splitBoth) Res.string.quest_partSmoothness_keep_both
                            else Res.string.quest_partSmoothness_split_both))
                    }
                }
            }
        }
    }
    if (selectedKeys.isNotEmpty()) {
        val selected = parts.filter { it.key in selectedKeys && it.needsSurvey }
        if (selected.isNotEmpty()) {
            val choices = Smoothness.entries.filter { value -> selected.all { value in smoothnessAnswersForSurface(it.surface) } }
            SimpleItemSelectDialog(
                onDismissRequest = { selectedKeys = emptyList() },
                columns = SimpleGridCells.Fixed(1),
                items = choices,
                title = selected.map { partLabel(it) }.joinToString(" + "),
                onSelected = { value -> draft = draft.copy(values = draft.values + selected.associate { it.key to value }) },
                itemContent = { value ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.distinctBy { it.surface }.forEach { part ->
                            if (selected.size > 1) Text(partLabel(part) + " (${part.surface})")
                            SmoothnessAnswerItem(value, part.surface)
                        }
                    }
                },
            )
        }
    }
    if (confirmSplit) {
        AlertDialog(
            onDismissRequest = { confirmSplit = false },
            title = { Text(stringResource(Res.string.quest_partSmoothness_split_both)) },
            text = { Column {
                Text(stringResource(Res.string.quest_partSmoothness_split_preview))
                parts.forEach { part ->
                    draft.values[part.key]?.let { Text(partLabel(part) + ": " + stringResource(it.title)) }
                }
            } },
            confirmButton = { TextButton(onClick = { confirmSplit = false; on(Answer(draft)) }) {
                Text(stringResource(Res.string.quest_partSmoothness_confirm_split))
            } },
            dismissButton = { TextButton(onClick = { confirmSplit = false }) {
                Text(stringResource(Res.string.quest_partSmoothness_back))
            } },
        )
    }
    if (showObstacleHint) InfoDialog(onDismissRequest = { showObstacleHint = false },
        text = { Text(stringResource(Res.string.quest_smoothness_obstacle_hint)) })
}

@Composable private fun partLabel(part: SmoothnessPart): String = stringResource(when (part.key) {
    "cycleway:left:smoothness" -> Res.string.quest_partSmoothness_left
    "cycleway:right:smoothness" -> Res.string.quest_partSmoothness_right
    "footway:smoothness" -> Res.string.quest_partSmoothness_foot
    else -> Res.string.quest_partSmoothness_cycle
})

@Composable private fun ExistingPartValue(part: SmoothnessPart) {
    part.existingValue?.let {
        Text(stringResource(Res.string.quest_partSmoothness_existing, it))
    }
}

/** The arrow follows the OSM geometry, independently of oneway and local traffic rules. */
@Composable private fun PartSides(
    parts: List<SmoothnessPart>,
    draft: PartSmoothnessAnswer,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
    onClick: (String) -> Unit,
) {
    val left = parts.find { it.key == "cycleway:left:smoothness" }
    val right = parts.find { it.key == "cycleway:right:smoothness" }
    if (left == null && right == null) return
    val orientation = remember(geometry) { geometry.getOrientationOrZero() }
    val rotation = LocalMapRotation.current
    Column {
        Text(stringResource(Res.string.quest_partSmoothness_direction))
        Box(Modifier.fillMaxWidth().height(160.dp)) {
            StreetSideForm(
                value = Sides(draft.values[left?.key], draft.values[right?.key]),
                onValueChanged = {},
                getIllustrationPainter = { _, side ->
                    val part = if (side == Side.LEFT) left else right
                    painterResource(if (part?.infrastructure == "lane") Res.drawable.cycleway_lane_white
                        else Res.drawable.cycleway_track)
                },
                onClickSide = { side -> (if (side == Side.LEFT) left else right)?.let { onClick(it.key) } },
                geometryRotation = orientation,
                mapRotation = rotation,
                mapTilt = LocalMapTilt.current,
                isLeftHandTraffic = countryInfo.isLeftHandTraffic,
                isLeftSideEnabled = left?.needsSurvey == true,
                isRightSideEnabled = right?.needsSurvey == true,
                isLeftSideVisible = left != null,
                isRightSideVisible = right != null,
                getFloatingPainter = { value, _ -> painterResource(value?.icon ?: Res.drawable.floating_question) },
            )
            Image(painterResource(Res.drawable.map_oneway_arrow),
                contentDescription = stringResource(Res.string.quest_partSmoothness_direction),
                modifier = Modifier.align(Alignment.Center).size(20.dp, 36.dp).rotate(orientation - rotation))
        }
    }
}
