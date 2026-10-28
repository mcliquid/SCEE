package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparation
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationSideState
import de.westnordost.streetcomplete.osm.cycleway_separate.tagSideForUiSide
import de.westnordost.streetcomplete.ui.common.street_side_select.Side

/** How one side of the cycleway is shown in the street-side illustration (R14 only). */
sealed interface CyclewaySeparationSidePresentation {
    /** Side still needs a survey answer. */
    data class Surveyable(val selection: CyclewaySeparation?) : CyclewaySeparationSidePresentation

    /** Existing single documented value; read-only. */
    data class LockedKnown(val value: CyclewaySeparation) : CyclewaySeparationSidePresentation

    /** Existing value that is not a single catalog entry (compound, deprecated, experimental). */
    data class LockedOther(val osmValue: String) : CyclewaySeparationSidePresentation
}

internal fun presentCyclewaySeparationSides(
    state: CyclewaySeparationSideState,
    tags: Map<String, String>,
    selections: Sides<CyclewaySeparation?>,
): Sides<CyclewaySeparationSidePresentation> =
    Sides(
        left = presentationForUiSide(Side.LEFT, state, tags, selections),
        right = presentationForUiSide(Side.RIGHT, state, tags, selections),
    )

private fun presentationForUiSide(
    uiSide: Side,
    state: CyclewaySeparationSideState,
    tags: Map<String, String>,
    selections: Sides<CyclewaySeparation?>,
): CyclewaySeparationSidePresentation {
    val tagSide = tagSideForUiSide(uiSide, tags)
    val needsSurvey = when (tagSide) {
        Side.LEFT -> state.needsLeftSurvey
        Side.RIGHT -> state.needsRightSurvey
    }
    if (needsSurvey) return CyclewaySeparationSidePresentation.Surveyable(selections.let {
        when (uiSide) {
            Side.LEFT -> it.left
            Side.RIGHT -> it.right
        }
    })
    val raw = when (tagSide) {
        Side.LEFT -> state.leftValue
        Side.RIGHT -> state.rightValue
    } ?: return CyclewaySeparationSidePresentation.Surveyable(null)
    CyclewaySeparation.fromSingleOsmValue(raw)?.let {
        return CyclewaySeparationSidePresentation.LockedKnown(it)
    }
    return CyclewaySeparationSidePresentation.LockedOther(raw)
}

/** Raw OSM value shown for a locked side, or null if the side is still surveyable. */
fun lockedSeparationOsmValueForUiSide(
    uiSide: Side,
    state: CyclewaySeparationSideState,
    tags: Map<String, String>,
): String? = when (val p = presentationForUiSide(uiSide, state, tags, Sides(null, null))) {
    is CyclewaySeparationSidePresentation.LockedOther -> p.osmValue
    is CyclewaySeparationSidePresentation.LockedKnown -> p.value.osmValue
    is CyclewaySeparationSidePresentation.Surveyable -> null
}
