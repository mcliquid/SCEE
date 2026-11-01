package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import kotlinx.serialization.Serializable

private const val LAST_PICKED_KEY = "AddPartSmoothnessForm"

@Serializable
internal data class PartSmoothnessLastPicked(
    val answer: PartSmoothnessAnswer,
    val scope: Map<String, List<String>>,
    val segregation: Map<String, String>,
)

private fun partSmoothnessLastPicked(answer: PartSmoothnessAnswer, parts: List<SmoothnessPart>, tags: Map<String, String>) =
    PartSmoothnessLastPicked(
        answer,
        parts.associate { it.key to listOf(it.surface, it.sourceKey, it.infrastructure.orEmpty()) },
        tags.filterKeys { it == "segregated" || it.endsWith(":segregated") },
    )

internal fun savePartSmoothnessLastPicked(
    preferences: Preferences, answer: PartSmoothnessAnswer, parts: List<SmoothnessPart>, tags: Map<String, String>,
) {
    if (canApplyPartSmoothness(answer, parts)) {
        preferences.setLastPicked(LAST_PICKED_KEY, listOf(partSmoothnessLastPicked(answer, parts, tags)))
    }
}

internal fun loadPartSmoothnessLastPicked(
    preferences: Preferences, parts: List<SmoothnessPart>, tags: Map<String, String>,
): PartSmoothnessAnswer? {
    val stored = preferences.getLastPicked<PartSmoothnessLastPicked>(LAST_PICKED_KEY).firstOrNull() ?: return null
    // Require the same physical parts and surface choices; never translate left into right.
    return stored.answer.takeIf {
        stored == partSmoothnessLastPicked(it, parts, tags) && canApplyPartSmoothness(it, parts)
    }
}
