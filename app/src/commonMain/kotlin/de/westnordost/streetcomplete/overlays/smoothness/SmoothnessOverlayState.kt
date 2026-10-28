package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.overlays.surface.SegregatedSurface
import de.westnordost.streetcomplete.overlays.surface.parseSurfaceOverlayAnswer
import de.westnordost.streetcomplete.osm.ALL_PATHS
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.quests.smoothness.parseSmoothness

/** A smoothness tag value as found in the data */
sealed interface ParsedSmoothness {
    data class Valid(val value: Smoothness) : ParsedSmoothness
    /** Any value that is not one of the documented smoothness values, e.g. `very_good` */
    data class Invalid(val raw: String) : ParsedSmoothness
}

fun parseSmoothnessValue(raw: String?): ParsedSmoothness? = when {
    raw == null -> null
    else -> parseSmoothness(raw)?.let { ParsedSmoothness.Valid(it) } ?: ParsedSmoothness.Invalid(raw)
}

val ParsedSmoothness?.validValue: Smoothness? get() = (this as? ParsedSmoothness.Valid)?.value

/** One smoothness that can be edited, together with the surface its examples depend on */
data class SmoothnessPartState(
    val smoothness: ParsedSmoothness?,
    /** surface tag that applies to this part, if any */
    val surface: String?,
    /** whether [surface] was taken from the surface of the whole way because the part has none */
    val surfaceFromWholeWay: Boolean = false,
)

data class SmoothnessOverlayState(
    /** whether foot- and cycleway part are edited separately */
    val isSegregated: Boolean,
    /** `smoothness` (and `surface`) of the whole way */
    val whole: SmoothnessPartState,
    val footway: SmoothnessPartState,
    val cycleway: SmoothnessPartState,
    val hasSidewalkTags: Boolean,
)

/** Foot- and cycleway parts are edited separately where the surface overlay does so, i.e.
 *  segregated paths or paths with separate surfaces, and additionally where separate part
 *  smoothness values are already mapped. An explicit `segregated=no` always means that the way
 *  is edited as a whole; contradicting part tags then need to be fixed in the tag editor. */
fun parseSmoothnessOverlayState(tags: Map<String, String>): SmoothnessOverlayState {
    val surface = tags["surface"]
    val footwaySurface = tags["footway:surface"]
    val cyclewaySurface = tags["cycleway:surface"]
    val isSegregated = tags["segregated"] != "no" && (
        parseSurfaceOverlayAnswer(tags) is SegregatedSurface ||
        tags["highway"] in ALL_PATHS && ("footway:smoothness" in tags || "cycleway:smoothness" in tags)
    )

    return SmoothnessOverlayState(
        isSegregated = isSegregated,
        whole = SmoothnessPartState(parseSmoothnessValue(tags["smoothness"]), surface),
        footway = SmoothnessPartState(
            parseSmoothnessValue(tags["footway:smoothness"]),
            footwaySurface ?: surface,
            footwaySurface == null && surface != null
        ),
        cycleway = SmoothnessPartState(
            parseSmoothnessValue(tags["cycleway:smoothness"]),
            cyclewaySurface ?: surface,
            cyclewaySurface == null && surface != null
        ),
        hasSidewalkTags = tags.keys.any { it == "sidewalk" || it.startsWith("sidewalk:") },
    )
}
