package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.osm.getLastCheckDateKeys
import de.westnordost.streetcomplete.osm.toCheckDate
import de.westnordost.streetcomplete.util.ktx.systemTimeNow
import de.westnordost.streetcomplete.util.ktx.toLocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.time.Instant

internal const val BOTH_SMOOTHNESS_KEY = "cycleway:both:smoothness"

internal data class SmoothnessPart(
    val key: String,
    val surface: String,
    val existingValue: String?,
    val sourceKey: String,
    val needsSurvey: Boolean,
    val infrastructure: String? = null,
)

/** Only explicit parts are resolved; traffic direction never changes left/right. */
internal fun resolveSmoothnessParts(
    tags: Map<String, String>,
    timestampEdited: Long,
    today: LocalDate = systemTimeNow().toLocalDate(),
): List<SmoothnessPart> {
    if (tags["area"] == "yes" || tags["indoor"]?.let { it != "no" } == true ||
        tags["conveying"]?.let { it != "no" } == true
    ) return emptyList()
    val highway = tags["highway"] ?: return emptyList()
    if (!isPartAccessible(tags, "bicycle")) return emptyList()

    fun part(key: String, surface: String?, sourceKey: String = key): SmoothnessPart? {
        if (surface !in SURFACES_FOR_SMOOTHNESS) return null
        val value = tags[sourceKey]
        if (value != null && Smoothness.entries.none { it.osmValue == value }) return null
        val checkDates = getLastCheckDateKeys(sourceKey).mapNotNull { tags[it]?.toCheckDate() }.toList()
        // Never repair competing metadata as a side effect of answering a part.
        if (checkDates.distinct().size > 1) return null
        val date = checkDates.firstOrNull() ?: tags["$sourceKey:date"]?.toCheckDate()
            ?: Instant.fromEpochMilliseconds(timestampEdited).toLocalDate()
        val needsSurvey = value == null || date < today - DatePeriod(years = 4)
        // Updating only the canonical check_date must not introduce a conflict with an old alias.
        if (needsSurvey && getLastCheckDateKeys(sourceKey).any {
                it != "check_date:$sourceKey" && tags[it] != null
            }) return null
        return SmoothnessPart(key, surface!!, value, sourceKey, needsSurvey)
    }

    if (highway in listOf("path", "footway", "cycleway", "bridleway")) {
        if (tags["segregated"] != "yes" ||
            listOf("sidewalk", "sidewalk:left", "sidewalk:right", "sidewalk:both").any { it in tags } ||
            listOf("path", "footway", "cycleway", "bridleway").any { tags[it] in listOf("link", "crossing") } ||
            !isPartAccessible(tags, "foot")
        ) return emptyList()
        val hasBicycle = highway == "cycleway" ||
            (highway in listOf("path", "footway") && tags["bicycle"] != null) ||
            (highway == "bridleway" && tags["bicycle"] in listOf("yes", "designated"))
        val hasFoot = highway == "footway" || highway == "path" || tags["foot"] != null
        if (!hasBicycle || !hasFoot) return emptyList()
        val explicitFoot = tags["foot"] == "designated" || highway == "footway" ||
            (highway == "path" && tags["foot"] == "yes")
        return listOfNotNull(
            part("cycleway:smoothness", tags["cycleway:surface"]),
            if (explicitFoot) part("footway:smoothness", tags["footway:surface"]) else null,
        )
    }
    if (highway !in listOf("primary", "primary_link", "secondary", "secondary_link", "tertiary",
            "tertiary_link", "unclassified", "residential", "living_street", "service", "pedestrian", "track", "busway")
    ) return emptyList()
    // Unscoped smoothness and per-lane schemes cannot safely be refined here.
    if ("cycleway:smoothness" in tags || "cycleway:lanes" in tags || "bicycle:lanes" in tags) return emptyList()
    val sideKeys = listOf("cycleway:left:smoothness", "cycleway:right:smoothness")
    if (BOTH_SMOOTHNESS_KEY in tags && sideKeys.any { it in tags }) return emptyList()

    fun side(name: String): SmoothnessPart? {
        val prefix = "cycleway:$name"
        fun attribute(suffix: String): String? {
            val values = listOfNotNull(tags["$prefix$suffix"], tags["cycleway:both$suffix"], tags["cycleway$suffix"])
            return values.distinct().singleOrNull()
        }
        val explicitInfrastructure = listOfNotNull(tags[prefix], tags["cycleway:both"])
        val infrastructure = explicitInfrastructure.distinct().singleOrNull() ?: return null
        if (tags["cycleway"]?.let { it != infrastructure } == true) return null
        if (infrastructure !in listOf("track", "lane")) return null
        if (infrastructure == "track" && attribute(":segregated") == "no") return null
        // Contradictory segregation is also unsuitable, even if surface is explicit.
        val segregations = listOfNotNull(tags["$prefix:segregated"], tags["cycleway:both:segregated"], tags["cycleway:segregated"])
        if (segregations.distinct().size > 1) return null
        val key = "$prefix:smoothness"
        return part(key, attribute(":surface"), if (BOTH_SMOOTHNESS_KEY in tags) BOTH_SMOOTHNESS_KEY else key)
            ?.copy(infrastructure = infrastructure)
    }
    val parts = listOfNotNull(side("left"), side("right"))
    return if (BOTH_SMOOTHNESS_KEY in tags && parts.size != 2) emptyList() else parts
}

private fun isPartAccessible(tags: Map<String, String>, mode: String): Boolean {
    val value = tags[mode] ?: tags["vehicle"] ?: tags["access"]
    return value !in if (mode == "bicycle") listOf("no", "private", "dismount") else listOf("no", "private")
}
