package de.westnordost.streetcomplete.quests.cycleway_separation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparation
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationAnswer
import de.westnordost.streetcomplete.osm.cycleway_separate.SURVEYABLE_CYCLEWAY_SEPARATIONS
import de.westnordost.streetcomplete.osm.cycleway_separate.parseCyclewaySeparationState
import de.westnordost.streetcomplete.osm.cycleway_separate.tagSideForUiSide
import de.westnordost.streetcomplete.osm.cycleway_separate.uiSideForTagSide
import de.westnordost.streetcomplete.osm.get
import de.westnordost.streetcomplete.quests.cycleway_separation.CyclewaySeparationSidePresentation.*
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.TextWithHalo
import de.westnordost.streetcomplete.ui.common.dialogs.SimpleItemSelectDialog
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.LocalMapRotation
import de.westnordost.streetcomplete.ui.common.quest.LocalMapTilt
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import de.westnordost.streetcomplete.ui.common.street_side_select.StreetSideForm
import de.westnordost.streetcomplete.ui.theme.titleSmall
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddCyclewaySeparationForm(
    on: (QuestAction<CyclewaySeparationAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    val sideState = remember(element.tags) { parseCyclewaySeparationState(element.tags) }
        ?: return

    val tags = element.tags
    val initialSelections: Sides<CyclewaySeparation?> = remember(element.tags) {
        Sides(
            left = knownSelectionForUiSide(Side.LEFT, sideState, tags),
            right = knownSelectionForUiSide(Side.RIGHT, sideState, tags),
        )
    }

    var selections by remember(element.tags) { mutableStateOf(initialSelections) }
    var pickerSide by remember { mutableStateOf<Side?>(null) }

    val presentations = remember(sideState, tags, selections) {
        presentCyclewaySeparationSides(sideState, tags, selections)
    }

    val geometryRotation = remember(geometry) { geometry.getOrientationOrZero() }

    val hintText = buildString {
        append(stringResource(Res.string.quest_cycleway_separation_hint))
        append("\n\n")
        append(stringResource(Res.string.quest_street_side_puzzle_tutorial))
        append("\n\n")
        append(stringResource(Res.string.quest_split_way_description))
    }

    fun isSideComplete(uiSide: Side): Boolean {
        if (!sideNeedsInput(uiSide, sideState, tags)) return true
        return selections[uiSide] != null
    }

    QuestForm(
        on = on,
        isComplete = isSideComplete(Side.LEFT) && isSideComplete(Side.RIGHT),
        hasChanges = selections.left != initialSelections.left || selections.right != initialSelections.right,
        hintText = hintText,
        onClickOk = {
            on(Answer(buildAnswer(sideState, tags, selections)))
        },
        contentPadding = PaddingValues.Zero,
    ) {
        StreetSideForm(
            value = presentations,
            onValueChanged = { },
            getIllustrationPainter = { presentation, _ ->
                when (presentation) {
                    is LockedKnown -> painterResource(presentation.value.icon)
                    is Surveyable -> presentation.selection?.let { painterResource(it.icon) }
                    is LockedOther -> null
                    null -> null
                }
            },
            getCenterOverlay = { presentation, _ ->
                when (presentation) {
                    is LockedOther -> {
                        TextWithHalo(
                            text = stringResource(
                                Res.string.quest_cycleway_separation_already_mapped,
                                presentation.osmValue,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    else -> Unit
                }
            },
            onClickSide = { side ->
                if (sideNeedsInput(side, sideState, tags)) pickerSide = side
            },
            geometryRotation = geometryRotation,
            mapRotation = LocalMapRotation.current,
            mapTilt = LocalMapTilt.current,
            isLeftHandTraffic = countryInfo.isLeftHandTraffic,
            isLeftSideEnabled = sideNeedsInput(Side.LEFT, sideState, tags),
            isRightSideEnabled = sideNeedsInput(Side.RIGHT, sideState, tags),
        )
    }

    pickerSide?.let { uiSide ->
        SimpleItemSelectDialog(
            onDismissRequest = { pickerSide = null },
            columns = SimpleGridCells.Fixed(2),
            items = SURVEYABLE_CYCLEWAY_SEPARATIONS,
            onSelected = { chosen ->
                selections = when (uiSide) {
                    Side.LEFT -> selections.copy(left = chosen)
                    Side.RIGHT -> selections.copy(right = chosen)
                }
                pickerSide = null
            },
            itemContent = { item ->
                ImageWithLabel(
                    painter = painterResource(item.icon),
                    label = stringResource(item.title),
                )
            },
        )
    }
}

private fun sideNeedsInput(uiSide: Side, state: de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationSideState, tags: Map<String, String>): Boolean =
    when (tagSideForUiSide(uiSide, tags)) {
        Side.LEFT -> state.needsLeftSurvey
        Side.RIGHT -> state.needsRightSurvey
    }

private fun knownSelectionForUiSide(
    uiSide: Side,
    state: de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationSideState,
    tags: Map<String, String>,
): CyclewaySeparation? {
    if (sideNeedsInput(uiSide, state, tags)) return null
    val raw = when (tagSideForUiSide(uiSide, tags)) {
        Side.LEFT -> state.leftValue
        Side.RIGHT -> state.rightValue
    } ?: return null
    return CyclewaySeparation.fromSingleOsmValue(raw)
}

internal fun buildAnswer(
    state: de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationSideState,
    tags: Map<String, String>,
    selections: Sides<CyclewaySeparation?>,
): CyclewaySeparationAnswer {
    fun selectionForTagSide(tagSide: Side): CyclewaySeparation? {
        val uiSide = uiSideForTagSide(tagSide, tags)
        return selections[uiSide]
    }
    return CyclewaySeparationAnswer(
        left = if (state.needsLeftSurvey) selectionForTagSide(Side.LEFT) else null,
        right = if (state.needsRightSurvey) selectionForTagSide(Side.RIGHT) else null,
    )
}
