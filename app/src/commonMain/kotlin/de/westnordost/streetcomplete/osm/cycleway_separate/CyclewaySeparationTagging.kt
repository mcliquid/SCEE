package de.westnordost.streetcomplete.osm.cycleway_separate

import de.westnordost.streetcomplete.osm.FILTER_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.oneway.isOneway
import de.westnordost.streetcomplete.osm.oneway.isReversedOneway
import de.westnordost.streetcomplete.ui.common.street_side_select.Side

/** Broad filter for separately mapped exclusive cycleways (details in [isCyclewaySeparationEligible]). */
internal val CYCLEWAY_SEPARATION_BASE_FILTER = """
    ways with
      highway = cycleway
      and area != yes
      and placement != transition
      and cycleway !~ link|crossing|sidepath
      and ($FILTER_BICYCLE_ACCESSIBLE)
      and foot !~ yes|designated
      and segregated != yes
""".trimIndent()

enum class SeparationSideSituation {
    Missing,
    Answered,
    Conflict,
}

data class CyclewaySeparationSideState(
    val left: SeparationSideSituation,
    val right: SeparationSideSituation,
    val leftValue: String?,
    val rightValue: String?,
) {
    val needsLeftSurvey: Boolean get() = left == SeparationSideSituation.Missing
    val needsRightSurvey: Boolean get() = right == SeparationSideSituation.Missing
}

data class CyclewaySeparationAnswer(
    val left: CyclewaySeparation?,
    val right: CyclewaySeparation?,
)

/** Left/right on separate ways follow travel direction when the way is oneway for cyclists. */
fun separationReferencesTravelDirection(tags: Map<String, String>): Boolean = when (tags["oneway:bicycle"]) {
    "yes", "1", "true", "-1" -> true
    "no" -> false
    else -> isOneway(tags)
}

fun isSeparationReversedForTravel(tags: Map<String, String>): Boolean =
    tags["oneway:bicycle"] == "-1" || (tags["oneway:bicycle"] == null && isReversedOneway(tags))

/** Map UI side (OSM way geometry) to the tag suffix left/right. */
fun tagSideForUiSide(side: Side, tags: Map<String, String>): Side {
    if (!separationReferencesTravelDirection(tags)) return side
    return if (isSeparationReversedForTravel(tags)) side.opposite() else side
}

fun uiSideForTagSide(tagSide: Side, tags: Map<String, String>): Side {
    if (!separationReferencesTravelDirection(tags)) return tagSide
    return if (isSeparationReversedForTravel(tags)) tagSide.opposite() else tagSide
}

private fun Side.opposite(): Side = when (this) {
    Side.LEFT -> Side.RIGHT
    Side.RIGHT -> Side.LEFT
}

fun separationTagKey(tagSide: Side): String = when (tagSide) {
    Side.LEFT -> "separation:left"
    Side.RIGHT -> "separation:right"
}

fun parseCyclewaySeparationState(tags: Map<String, String>): CyclewaySeparationSideState? {
    if (
        tags.containsKey("separation") &&
        !tags.containsKey("separation:left") &&
        !tags.containsKey("separation:right") &&
        !tags.containsKey("separation:both")
    ) {
        return null
    }

    val both = tags["separation:both"]
    val leftRaw = tags["separation:left"]
    val rightRaw = tags["separation:right"]

    if (bothSideTaggingConflicts(both, leftRaw, rightRaw)) {
        return CyclewaySeparationSideState(
            left = SeparationSideSituation.Conflict,
            right = SeparationSideSituation.Conflict,
            leftValue = leftRaw ?: both,
            rightValue = rightRaw ?: both,
        )
    }

    return CyclewaySeparationSideState(
        left = sideSituation(leftRaw, both),
        right = sideSituation(rightRaw, both),
        leftValue = leftRaw ?: both,
        rightValue = rightRaw ?: both,
    )
}

fun isCyclewaySeparationEligible(tags: Map<String, String>): Boolean {
    val state = parseCyclewaySeparationState(tags) ?: return false // includes legacy `separation` shorthand
    if (state.left == SeparationSideSituation.Conflict || state.right == SeparationSideSituation.Conflict) {
        return false
    }
    return state.needsLeftSurvey || state.needsRightSurvey
}

private fun bothSideTaggingConflicts(both: String?, left: String?, right: String?): Boolean {
    if (both == null) return false
    if (left != null && left != both) return true
    if (right != null && right != both) return true
    return false
}

private fun sideSituation(sideValue: String?, bothValue: String?): SeparationSideSituation {
    if (sideValue != null) {
        if (sideValue.isEmpty()) return SeparationSideSituation.Missing
        if (bothValue != null && sideValue != bothValue) return SeparationSideSituation.Conflict
        return SeparationSideSituation.Answered
    }
    if (bothValue != null) return SeparationSideSituation.Answered
    return SeparationSideSituation.Missing
}

fun applyCyclewaySeparationAnswer(tags: Tags, answer: CyclewaySeparationAnswer) {
    val state = parseCyclewaySeparationState(tags) ?: return
    if (state.left == SeparationSideSituation.Conflict || state.right == SeparationSideSituation.Conflict) {
        return
    }
    if (state.needsLeftSurvey && answer.left != null && tags["separation:left"].isNullOrEmpty()) {
        tags["separation:left"] = answer.left.osmValue
    }
    if (state.needsRightSurvey && answer.right != null && tags["separation:right"].isNullOrEmpty()) {
        tags["separation:right"] = answer.right.osmValue
    }
}
