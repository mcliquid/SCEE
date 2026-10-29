package de.westnordost.streetcomplete.osm.cycleway_track_surface

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.getLastCheckDateKeys
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.applyTo
import de.westnordost.streetcomplete.osm.surface.parseSurface
import kotlinx.serialization.Serializable

const val BOTH_CYCLEWAY_SURFACE_KEY = "cycleway:both:surface"

private const val LEFT_SURFACE_KEY = "cycleway:left:surface"
private const val RIGHT_SURFACE_KEY = "cycleway:right:surface"

private val ROAD_HIGHWAYS = listOf(
    "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
    "unclassified", "residential", "living_street", "service", "pedestrian", "track", "busway",
)

private val EXCLUDED_HIGHWAYS = setOf("cycleway", "footway", "path", "steps", "construction", "proposed")

internal data class CyclewayTrackSurfacePart(
    val key: String,
    val existingValue: String?,
    val needsSurvey: Boolean,
    val sourceKey: String,
)

internal fun isCyclewayTrackSurfaceEligible(tags: Map<String, String>): Boolean =
    resolveCyclewayTrackSurfaceParts(tags).any { it.needsSurvey }

/** Resolves road-attached cycleway tracks missing a surveyable surface. Traffic direction never swaps sides. */
internal fun resolveCyclewayTrackSurfaceParts(tags: Map<String, String>): List<CyclewayTrackSurfacePart> {
    if (tags["area"] == "yes" || tags["indoor"]?.let { it != "no" } == true ||
        tags["conveying"]?.let { it != "no" } == true
    ) return emptyList()
    val highway = tags["highway"] ?: return emptyList()
    if (highway in EXCLUDED_HIGHWAYS) return emptyList()
    if (highway !in ROAD_HIGHWAYS) return emptyList()
    if (!isBicycleAccessible(tags)) return emptyList()
    if ("cycleway:lanes" in tags || "bicycle:lanes" in tags) return emptyList()

    val sideSurfaceKeys = listOf(LEFT_SURFACE_KEY, RIGHT_SURFACE_KEY)
    val bothSurface = tags[BOTH_CYCLEWAY_SURFACE_KEY]
    if (bothSurface != null && sideSurfaceKeys.any { tags[it] != null && tags[it] != bothSurface }) {
        return emptyList()
    }
    if (bothSurface != null && sideSurfaceKeys.none { it in tags } && isSurveyedSurface(bothSurface)) {
        return emptyList()
    }
    if (sideSurfaceKeys.all { it in tags }) return emptyList()

    fun side(name: String): CyclewayTrackSurfacePart? {
        val prefix = "cycleway:$name"
        val infrastructure = listOfNotNull(tags[prefix], tags["cycleway:both"]).distinct().singleOrNull()
            ?: return null
        if (tags["cycleway"]?.let { it != infrastructure } == true) return null
        if (infrastructure != "track") return null
        if (attribute(tags, prefix, ":segregated") == "no") return null
        val segregations = listOfNotNull(
            tags["$prefix:segregated"],
            tags["cycleway:both:segregated"],
            tags["cycleway:segregated"],
        )
        if (segregations.distinct().size > 1) return null

        val surface = attribute(tags, prefix, ":surface") ?: run {
            val key = "$prefix:surface"
            return CyclewayTrackSurfacePart(key, null, needsSurvey = true, sourceKey = key)
        }
        val parsed = parseSurface(surface)
        if (parsed == Surface.UNSUPPORTED) return null
        val key = "$prefix:surface"
        val sourceKey = when {
            tags[key] != null -> key
            tags[BOTH_CYCLEWAY_SURFACE_KEY] != null -> BOTH_CYCLEWAY_SURFACE_KEY
            tags["cycleway:surface"] != null -> "cycleway:surface"
            else -> key
        }
        if (parsed != null) {
            return CyclewayTrackSurfacePart(key, surface, needsSurvey = false, sourceKey = sourceKey)
        }
        return CyclewayTrackSurfacePart(key, surface, needsSurvey = true, sourceKey = sourceKey)
    }

    val parts = listOfNotNull(side("left"), side("right"))
    if (parts.none { it.needsSurvey }) return emptyList()
    if (bothSurface != null && parts.count { it.needsSurvey } != parts.size) return emptyList()
    return parts
}

private fun attribute(tags: Map<String, String>, prefix: String, suffix: String): String? {
    val values = listOfNotNull(
        tags["$prefix$suffix"],
        tags["cycleway:both$suffix"],
        tags["cycleway$suffix"],
    ).distinct()
    return values.singleOrNull()
}

private fun isSurveyedSurface(value: String): Boolean {
    val parsed = parseSurface(value) ?: return false
    return parsed != Surface.UNSUPPORTED
}

private fun isBicycleAccessible(tags: Map<String, String>): Boolean {
    val value = tags["bicycle"] ?: tags["vehicle"] ?: tags["access"]
    return value !in listOf("no", "private", "dismount")
}

@Serializable
data class CyclewayTrackSurfaceAnswer(
    val values: Map<String, Surface>,
    val splitBoth: Boolean = false,
)

internal fun canApplyCyclewayTrackSurfaceAnswer(
    answer: CyclewayTrackSurfaceAnswer,
    parts: List<CyclewayTrackSurfacePart>,
): Boolean {
    if (answer.values.isEmpty()) return false
    if (answer.values.any { (key, surface) ->
            surface.osmValue == null ||
                parts.none { it.key == key && it.needsSurvey }
        }) return false
    val bothParts = parts.filter { it.sourceKey == BOTH_CYCLEWAY_SURFACE_KEY && it.needsSurvey }
    if (bothParts.isNotEmpty()) {
        if (bothParts.size != 2 || !bothParts.all { it.key in answer.values }) return false
        if (!answer.splitBoth && answer.values.values.distinct().size != 1) return false
    } else if (answer.splitBoth) return false
    return true
}

fun applyCyclewayTrackSurfaceAnswer(tags: Tags, answer: CyclewayTrackSurfaceAnswer) {
    val parts = resolveCyclewayTrackSurfaceParts(tags)
    require(canApplyCyclewayTrackSurfaceAnswer(answer, parts)) {
        "Cycleway track surface answer does not match the surveyed parts"
    }
    val hasBothSource = parts.any { it.sourceKey == BOTH_CYCLEWAY_SURFACE_KEY && it.needsSurvey }
    val writeBoth = !answer.splitBoth &&
        answer.values.size == 2 &&
        answer.values.values.distinct().size == 1 &&
        answer.values.keys == setOf(LEFT_SURFACE_KEY, RIGHT_SURFACE_KEY) &&
        (hasBothSource || answer.values.keys.none { it in tags })

    if (answer.splitBoth) {
        tags.remove(BOTH_CYCLEWAY_SURFACE_KEY)
        (getLastCheckDateKeys(BOTH_CYCLEWAY_SURFACE_KEY) + sequenceOf("$BOTH_CYCLEWAY_SURFACE_KEY:date"))
            .forEach { tags.remove(it) }
    }

    if (writeBoth) {
        answer.values.values.first().applyTo(tags, "cycleway:both")
    } else {
        answer.values.forEach { (key, surface) ->
            val prefix = when (key) {
                LEFT_SURFACE_KEY -> "cycleway:left"
                RIGHT_SURFACE_KEY -> "cycleway:right"
                else -> error("Unexpected surface key $key")
            }
            surface.applyTo(tags, prefix)
        }
    }
}
