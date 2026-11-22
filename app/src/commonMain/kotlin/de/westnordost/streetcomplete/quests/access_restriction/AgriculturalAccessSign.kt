package de.westnordost.streetcomplete.quests.access_restriction

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource

/** German traffic-sign combinations that can be mapped unambiguously from
 *  `access=agricultural|forestry` (including `agricultural;forestry`). */
@Serializable
enum class AgriculturalAccessSign {
    /** DE:250 + agricultural/forestry exception → `vehicle=*` */
    NO_VEHICLES_WITH_EXCEPTION,
    /** DE:260 + agricultural/forestry exception → `motor_vehicle=*` */
    NO_MOTOR_VEHICLES_WITH_EXCEPTION,
    /** DE:250 + bicycle allowed + agricultural/forestry exception → `vehicle=*` + `bicycle=yes` */
    NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
    /** DE:240 + agricultural/forestry exception → `foot`/`bicycle`=designated, `segregated=no`, `access` unchanged */
    SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION,
}

val AgriculturalAccessSign.title: StringResource get() = when (this) {
    AgriculturalAccessSign.NO_VEHICLES_WITH_EXCEPTION ->
        Res.string.quest_accessRestriction_no_vehicles
    AgriculturalAccessSign.NO_MOTOR_VEHICLES_WITH_EXCEPTION ->
        Res.string.quest_accessRestriction_no_motor_vehicles
    AgriculturalAccessSign.NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION ->
        Res.string.quest_accessRestriction_no_vehicles_bicycles
    AgriculturalAccessSign.SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION ->
        Res.string.quest_accessRestriction_shared_foot_cycle
}

internal const val agriculturalAccessExcludedHighways =
    "footway|cycleway|bridleway|steps|pedestrian|motorway|motorway_link|construction|proposed|raceway|corridor|bus_guideway|escape|elevator|platform|rest_area|services"

private val excludedHighways = agriculturalAccessExcludedHighways.split('|').toSet()

/** Recheck road suitability when answering, in case downloaded tags have changed. */
internal fun isAgriculturalAccessRoad(tags: Map<String, String>): Boolean =
    tags["highway"] != null && tags["highway"] !in excludedHighways
        && tags["area"] != "yes" && tags["motorroad"] != "yes"
        && tags["bicycle_road"] != "yes" && tags["cyclestreet"] != "yes"
        && "barrier" !in tags

private val bicycleAlreadyAllowed = setOf("yes", "designated", "official")
private val footCycleRestrictive = setOf("no", "private", "destination", "agricultural", "forestry",
    "agricultural;forestry", "forestry;agricultural", "permissive", "use_sidepath", "dismount", "unknown")

/** OSM `traffic_sign` matches DE:240 plus the plate implied by [accessValue], with or without `DE:` on plates. */
internal fun trafficSignMatchesDe240Exception(trafficSign: String, accessValue: String): Boolean {
    val plate = additionalTrafficSignId(accessValue) ?: return false
    return trafficSign.contains("240") && (trafficSign.contains(plate) || trafficSign.contains("DE:$plate"))
}

/**
 * DE:240 + exception is fully captured. Modes alone are not enough — [CheckAgriculturalAccess] may
 * still need to record `traffic_sign=*` when missing (see quest hint: existing traffic_sign preserved).
 */
internal fun isDe240AgriculturalAccessComplete(tags: Map<String, String>): Boolean {
    val access = tags["access"].orEmpty()
    val trafficSign = tags["traffic_sign"] ?: return false
    return trafficSignMatchesDe240Exception(trafficSign, access)
        && tags["foot"] == "designated" && tags["bicycle"] == "designated" && tags["segregated"] == "no"
}

/** Keep conflicting or conditional restrictions for a separate, explicit survey. */
fun AgriculturalAccessSign.canApplyTo(tags: Map<String, String>): Boolean =
    isAgriculturalAccessRoad(tags)
        && additionalTrafficSignId(tags["access"].orEmpty()) != null
        && "vehicle" !in tags && "motor_vehicle" !in tags
        && tags.keys.none { key ->
            listOf("access:", "vehicle:", "motor_vehicle:", "bicycle:").any { key.startsWith(it) }
        }
        && (this != AgriculturalAccessSign.NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION
            || tags["bicycle"] == null || tags["bicycle"] in bicycleAlreadyAllowed)
        && (this != AgriculturalAccessSign.SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION
            || (!isDe240AgriculturalAccessComplete(tags)
                && tags["foot"] !in footCycleRestrictive && tags["bicycle"] !in footCycleRestrictive
                && tags["segregated"] != "yes"))

/**
 * Replace overly broad `access=agricultural|forestry` with the more specific tagging implied by
 * the selected German sign combination.
 *
 * Unrelated tags are left untouched, including existing `foot=*` / `bicycle=*` unless the selected
 * combination is the explicit bicycle-allowed sign and bicycle=* is absent. Conflicting
 * vehicle, motor_vehicle, bicycle and conditional restrictions are never overwritten.
 * Existing `traffic_sign=*` / `traffic_sign:*=*` is never overwritten; the access-restriction
 * group is written only when no traffic-sign tag exists yet.
 *
 * The existing agricultural/forestry value is kept as-is (including `agricultural;forestry`)
 * instead of asking the mapper to identify it separately.
 *
 * If `access` is missing or not one of the supported values, this is a no-op so we never invent
 * an exception value.
 */
fun AgriculturalAccessSign.applyTo(tags: Tags) {
    if (!canApplyTo(tags)) return
    val exception = tags["access"] ?: return
    if (additionalTrafficSignId(exception) == null) return

    if (this == AgriculturalAccessSign.SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION) {
        if (tags["foot"] != "designated") tags["foot"] = "designated"
        if (tags["bicycle"] != "designated") tags["bicycle"] = "designated"
        if (tags["segregated"] != "no") tags["segregated"] = "no"
        if (!hasExistingTrafficSign(tags)) {
            tags["traffic_sign"] = trafficSignTagValue(exception)!!
        }
        return
    }

    tags.remove("access")
    when (this) {
        AgriculturalAccessSign.NO_VEHICLES_WITH_EXCEPTION -> {
            tags["vehicle"] = exception
        }
        AgriculturalAccessSign.NO_MOTOR_VEHICLES_WITH_EXCEPTION -> {
            tags["motor_vehicle"] = exception
        }
        AgriculturalAccessSign.NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION -> {
            tags["vehicle"] = exception
            if (tags["bicycle"] !in bicycleAlreadyAllowed) {
                tags["bicycle"] = "yes"
            }
        }
    }
    if (!hasExistingTrafficSign(tags)) {
        tags["traffic_sign"] = trafficSignTagValue(exception)!!
    }
}

fun hasExistingTrafficSign(tags: Map<String, String>): Boolean =
    tags.keys.any { it == "traffic_sign" || it.startsWith("traffic_sign:") }

/** OSM additional-sign id matching the existing `access=*` value. */
fun additionalTrafficSignId(accessValue: String): String? = when (accessValue) {
    "agricultural" -> "1026-36"
    "forestry" -> "1026-37"
    "agricultural;forestry", "forestry;agricultural" -> "1026-38"
    else -> null
}

/** OSM `traffic_sign` value for this answer and the existing `access=*` exception wording. */
fun AgriculturalAccessSign.trafficSignTagValue(accessValue: String): String? {
    val additionalSign = additionalTrafficSignId(accessValue) ?: return null
    return when (this) {
        AgriculturalAccessSign.NO_VEHICLES_WITH_EXCEPTION ->
            "DE:250,$additionalSign"
        AgriculturalAccessSign.NO_MOTOR_VEHICLES_WITH_EXCEPTION ->
            "DE:260,$additionalSign"
        AgriculturalAccessSign.NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION ->
            // Related signs on one post are comma-separated; see Key:traffic_sign.
            "DE:250,$additionalSign,1022-10"
        AgriculturalAccessSign.SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION ->
            "DE:240,$additionalSign"
    }
}

/** Official German additional-sign wording, used only as a depiction of the sign. */
fun additionalSignText(accessValue: String): String = when (accessValue) {
    "forestry" -> "forstwirtschaft-\nlicher Verkehr\nfrei"
    "agricultural;forestry", "forestry;agricultural" ->
        "Land- und forst-\nwirtschaftlicher\nVerkehr frei"
    else -> "landwirtschaft-\nlicher Verkehr\nfrei"
}
