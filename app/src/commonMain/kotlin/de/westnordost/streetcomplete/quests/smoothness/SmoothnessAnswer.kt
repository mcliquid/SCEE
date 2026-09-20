package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.changeToSteps
import de.westnordost.streetcomplete.osm.hasCheckDateForKey
import de.westnordost.streetcomplete.osm.removeCheckDatesForKey
import de.westnordost.streetcomplete.osm.surface.getKeysAssociatedWithSurface
import de.westnordost.streetcomplete.osm.updateCheckDateForKey
import de.westnordost.streetcomplete.osm.updateWithCheckDate

sealed interface SmoothnessAnswer

data class SmoothnessValueAnswer(val value: Smoothness) : SmoothnessAnswer

data object IsActuallyStepsAnswer : SmoothnessAnswer
data object WrongSurfaceAnswer : SmoothnessAnswer

/** Apply the smoothness answer. Optional [prefix] writes namespaced keys, e.g. `"cycleway"`
 *  for `cycleway:smoothness` / `cycleway:surface`, without touching generic or other prefixes. */
fun SmoothnessAnswer.applyTo(tags: Tags, prefix: String? = null) {
    val pre = if (prefix != null) "$prefix:" else ""
    tags.remove("${pre}smoothness:date")
    // similar tag as smoothness, will be wrong/outdated when smoothness is set
    tags.remove("${pre}surface:grade")
    when (this) {
        is SmoothnessValueAnswer -> {
            tags.updateWithCheckDate("${pre}smoothness", value.osmValue)
            if (tags.hasCheckDateForKey("${pre}surface")) {
                tags.updateCheckDateForKey("${pre}surface")
            }
        }
        is WrongSurfaceAnswer -> {
            tags.remove("${pre}surface")
            tags.removeCheckDatesForKey("${pre}surface")
            getKeysAssociatedWithSurface(pre).forEach { tags.remove(it) }
        }
        is IsActuallyStepsAnswer -> {
            tags.changeToSteps()
        }
    }
}
