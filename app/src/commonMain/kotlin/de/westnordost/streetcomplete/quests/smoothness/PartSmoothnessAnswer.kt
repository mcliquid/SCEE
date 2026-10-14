package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.getLastCheckDateKeys
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import kotlinx.serialization.Serializable

/** Values are observations, never prefilled copies of existing tags. */
@Serializable
data class PartSmoothnessAnswer(
    val values: Map<String, Smoothness>,
    val splitBoth: Boolean = false,
) : SmoothnessAnswer

internal fun canApplyPartSmoothness(answer: PartSmoothnessAnswer, parts: List<SmoothnessPart>): Boolean {
    if (answer.values.isEmpty()) return false
    if (answer.values.any { (key, value) ->
            parts.none { it.key == key && it.needsSurvey && value in smoothnessAnswersForSurface(it.surface) }
        }) return false
    val bothParts = parts.filter { it.sourceKey == BOTH_SMOOTHNESS_KEY }
    if (bothParts.isNotEmpty()) {
        // A common tag describes both physical sides, even when only one is observable.
        if (bothParts.size != 2 || !bothParts.all { it.key in answer.values }) return false
        if (!answer.splitBoth && answer.values.values.distinct().size != 1) return false
    } else if (answer.splitBoth) return false
    return true
}

internal fun PartSmoothnessAnswer.applyToParts(tags: Tags, timestampEdited: Long) {
    val parts = resolveSmoothnessParts(tags, timestampEdited)
    require(canApplyPartSmoothness(this, parts)) { "Smoothness answer does not match the surveyed parts" }
    val hasBoth = parts.any { it.sourceKey == BOTH_SMOOTHNESS_KEY }
    val writeBoth = !splitBoth && values.size == 2 && values.values.distinct().size == 1 &&
        values.keys == setOf("cycleway:left:smoothness", "cycleway:right:smoothness") &&
        (hasBoth || values.keys.none { it in tags })
    if (splitBoth) {
        tags.remove(BOTH_SMOOTHNESS_KEY)
        (getLastCheckDateKeys(BOTH_SMOOTHNESS_KEY) + sequenceOf("$BOTH_SMOOTHNESS_KEY:date"))
            .forEach { tags.remove(it) }
    }
    val writes = if (writeBoth) mapOf(BOTH_SMOOTHNESS_KEY to values.values.first()) else values
    writes.forEach { (key, value) ->
        tags[key] = value.osmValue
        tags["check_date:$key"] = nowAsCheckDateString()
    }
}
