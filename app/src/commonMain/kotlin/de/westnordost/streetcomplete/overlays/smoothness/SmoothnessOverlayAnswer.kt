package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.removeCheckDatesForKey
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.applyTo
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.quests.smoothness.SmoothnessValueAnswer
import de.westnordost.streetcomplete.quests.smoothness.applyTo
import de.westnordost.streetcomplete.quests.smoothness.parseSmoothness
import kotlinx.serialization.Serializable

@Serializable
sealed interface SmoothnessOverlayAnswer

/** Smoothness of the whole way. [newSurface] is set if the user also specified the surface. A
 *  value equal to the current one means that the user confirmed it. */
@Serializable
data class SingleSmoothnessAnswer(
    val smoothness: Smoothness,
    val newSurface: Surface? = null,
) : SmoothnessOverlayAnswer

/** Smoothness of the foot- and cycleway parts. Only parts that the user changed or explicitly
 *  confirmed are set, untouched parts are null and stay unchanged. */
@Serializable
data class PartsSmoothnessAnswer(
    val footway: Smoothness? = null,
    val cycleway: Smoothness? = null,
) : SmoothnessOverlayAnswer

fun SmoothnessOverlayAnswer.applyTo(tags: Tags) {
    when (this) {
        is SingleSmoothnessAnswer -> {
            // changing the surface removes the smoothness of the old surface, so do it first
            newSurface?.applyTo(tags)
            // same tagging as the smoothness quests
            SmoothnessValueAnswer(smoothness).applyTo(tags)
        }
        is PartsSmoothnessAnswer -> {
            footway?.let { applyPartSmoothness(tags, "footway", it) }
            cycleway?.let { applyPartSmoothness(tags, "cycleway", it) }
            removeStaleCommonSmoothness(tags)
        }
    }
}

/** Like the part smoothness quest (PartSmoothnessAnswer): only the value and the check date of
 *  the answered part are written, never the check date of the other part or of the whole element,
 *  and no other metadata of the part (`<part>:smoothness:date`, `<part>:surface:grade`) is
 *  removed. Only the other spellings of this very check date are replaced, so that the part does
 *  not end up with two contradicting check dates. */
private fun applyPartSmoothness(tags: Tags, part: String, value: Smoothness) {
    val key = "$part:smoothness"
    tags[key] = value.osmValue
    tags.removeCheckDatesForKey(key)
    tags["check_date:$key"] = nowAsCheckDateString()
}

/** Once both parts of a segregated way have a smoothness, a common smoothness that disagrees with
 *  them is stale. A common smoothness is never created. With an explicit `segregated=no`, the
 *  common smoothness is the one that applies, so it is never removed. */
private fun removeStaleCommonSmoothness(tags: Tags) {
    if (tags["segregated"] == "no") return
    val footway = parseSmoothness(tags["footway:smoothness"]) ?: return
    val cycleway = parseSmoothness(tags["cycleway:smoothness"]) ?: return
    val common = tags["smoothness"] ?: return
    if (common == footway.osmValue && common == cycleway.osmValue) return
    tags.remove("smoothness")
    tags.remove("smoothness:date")
    tags.remove("source:smoothness")
    tags.removeCheckDatesForKey("smoothness")
}
