package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway_separate.*
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import kotlinx.serialization.Serializable

private const val LAST_PICKED_KEY = "AddCyclewaySeparationForm"

@Serializable
private data class SeparationLastPicked(
    val answer: CyclewaySeparationAnswer,
    val needsLeft: Boolean,
    val needsRight: Boolean,
    val leftValue: String?,
    val rightValue: String?,
    val travelDirection: Boolean,
)

private fun separationLastPicked(tags: Map<String, String>, answer: CyclewaySeparationAnswer): SeparationLastPicked? {
    if (tags["highway"] != "cycleway" || !isCyclewaySeparationEligible(tags)) return null
    val state = parseCyclewaySeparationState(tags) ?: return null
    if (state.needsLeftSurvey != (answer.left != null) || state.needsRightSurvey != (answer.right != null)) return null
    return SeparationLastPicked(answer, state.needsLeftSurvey, state.needsRightSurvey,
        state.leftValue, state.rightValue, separationReferencesTravelDirection(tags))
}

internal fun saveSeparationLastPicked(preferences: Preferences, tags: Map<String, String>, answer: CyclewaySeparationAnswer) {
    separationLastPicked(tags, answer)?.let { preferences.setLastPicked(LAST_PICKED_KEY, listOf(it)) }
}

internal fun loadSeparationLastPicked(preferences: Preferences, tags: Map<String, String>): CyclewaySeparationAnswer? {
    val stored = preferences.getLastPicked<SeparationLastPicked>(LAST_PICKED_KEY).firstOrNull() ?: return null
    return stored.answer.takeIf { stored == separationLastPicked(tags, it) }
}

/** History is stored in tag orientation, then mapped to the current geometry for display. */
internal fun separationSelectionsForAnswer(answer: CyclewaySeparationAnswer, tags: Map<String, String>): Sides<CyclewaySeparation?> {
    fun forUiSide(side: Side) = when (tagSideForUiSide(side, tags)) {
        Side.LEFT -> answer.left
        Side.RIGHT -> answer.right
    }
    return Sides(forUiSide(Side.LEFT), forUiSide(Side.RIGHT))
}
