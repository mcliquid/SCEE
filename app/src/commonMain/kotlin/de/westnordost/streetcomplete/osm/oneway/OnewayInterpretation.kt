package de.westnordost.streetcomplete.osm.oneway

/** Direction in which travel is allowed, relative to the way's geometry (order of its nodes) */
enum class TravelDirection {
    FORWARD,
    BACKWARD,
    BOTH,
    /** the value is not one of the documented ones (e.g. `reversible`, `alternating`, typos) */
    UNKNOWN,
}

/** Where the bicycle direction was taken from */
enum class BicycleDirectionSource {
    /** `oneway:bicycle` */
    EXPLICIT,
    /** deprecated `cycleway=opposite*` */
    LEGACY_OPPOSITE,
    /** contraflow cycle lane/track on the road (`cycleway:<side>:oneway`) or
     *  `bicycle:backward/forward` against the oneway, but no `oneway:bicycle=no` */
    INFERRED_CONTRAFLOW,
    /** `oneway:vehicle` */
    VEHICLE,
    /** `oneway` or implied by `junction=roundabout` */
    GENERAL,
}

enum class OnewayIssue {
    /** e.g. `oneway:bicycle=yes` + `cycleway=opposite`, `oneway=yes` + `oneway:bicycle=-1`,
     *  `junction=roundabout` + `oneway=no` */
    CONFLICT,
    /** a oneway tag with an undocumented value */
    UNKNOWN_VALUE,
    /** `junction=circular` without `oneway`: usually oneway, but not implied */
    CIRCULAR_WITHOUT_ONEWAY,
}

data class OnewayInterpretation(
    /** `oneway`, or implied by `junction=roundabout` */
    val general: TravelDirection,
    /** `oneway:motor_vehicle` › `oneway:vehicle` › [general] */
    val motorVehicle: TravelDirection,
    /** effective allowed direction for bicycles, null if bicycles have no access */
    val bicycle: TravelDirection?,
    val bicycleSource: BicycleDirectionSource,
    val isGeneralImplied: Boolean,
    /** any `oneway*:conditional` that may change the direction for bicycles */
    val isConditional: Boolean,
    val issues: Set<OnewayIssue>,
) {
    val isGeneralOneway: Boolean get() = general == TravelDirection.FORWARD || general == TravelDirection.BACKWARD
}

/** Parses a value of `oneway` or `oneway:*`. `1`, `true` / `0`, `false` are deprecated but
 *  their meaning is unambiguous */
fun parseOnewayValue(value: String?): TravelDirection? = when (value) {
    null -> null
    "yes", "1", "true" -> TravelDirection.FORWARD
    "-1" -> TravelDirection.BACKWARD
    "no", "0", "false" -> TravelDirection.BOTH
    else -> TravelDirection.UNKNOWN
}

/** Interprets oneway tagging, with focus on bicycles. Precedence for bicycles (highest first):
 *  1. access (`bicycle=no`, or `vehicle`/`access=no` without `bicycle` permission)
 *  2. `oneway:bicycle`
 *  3. deprecated `cycleway=opposite*` (only on oneways)
 *  4. contraflow infrastructure on the road (`cycleway:<side>:oneway`) or `bicycle:<direction>` against
 *     the oneway (inferred, only on oneways)
 *  5. `oneway:vehicle`
 *  6. `oneway`, then `junction=roundabout` (implies oneway in way direction)
 *
 *  `oneway:motor_vehicle` never applies to bicycles. Conditional restrictions are not evaluated. */
fun interpretOneway(tags: Map<String, String>): OnewayInterpretation {
    val issues = mutableSetOf<OnewayIssue>()

    val explicitGeneral = parseOnewayValue(tags["oneway"])
    val isRoundabout = tags["junction"] == "roundabout"
    val general = explicitGeneral ?: if (isRoundabout) TravelDirection.FORWARD else TravelDirection.BOTH
    if (isRoundabout && explicitGeneral == TravelDirection.BOTH) issues.add(OnewayIssue.CONFLICT)
    if (tags["junction"] == "circular" && explicitGeneral == null) issues.add(OnewayIssue.CIRCULAR_WITHOUT_ONEWAY)

    val vehicle = parseOnewayValue(tags["oneway:vehicle"])
    val motorVehicle = parseOnewayValue(tags["oneway:motor_vehicle"]) ?: vehicle ?: general
    val explicitBicycle = parseOnewayValue(tags["oneway:bicycle"])

    val onewayKeys = listOf("oneway", "oneway:vehicle", "oneway:motor_vehicle", "oneway:bicycle")
    if (onewayKeys.any { parseOnewayValue(tags[it]) == TravelDirection.UNKNOWN }) {
        issues.add(OnewayIssue.UNKNOWN_VALUE)
    }

    // direction that bicycles would have without any bicycle specific tagging
    val bicycleBase = vehicle ?: general
    val isBicycleBaseOneway = bicycleBase == TravelDirection.FORWARD || bicycleBase == TravelDirection.BACKWARD
    val isLegacyOpposite = isBicycleBaseOneway && hasLegacyOpposite(tags)
    val isInferredContraflow = isBicycleBaseOneway && hasContraflow(tags, bicycleBase)

    if (explicitBicycle == TravelDirection.FORWARD || explicitBicycle == TravelDirection.BACKWARD) {
        if (hasLegacyOpposite(tags) || isInferredContraflow) issues.add(OnewayIssue.CONFLICT)
        // bicycles in the opposite direction of all other traffic
        if (isBicycleBaseOneway && explicitBicycle != bicycleBase) issues.add(OnewayIssue.CONFLICT)
    }

    val (bicycle, source) = when {
        explicitBicycle != null -> explicitBicycle to BicycleDirectionSource.EXPLICIT
        isLegacyOpposite -> TravelDirection.BOTH to BicycleDirectionSource.LEGACY_OPPOSITE
        isInferredContraflow -> TravelDirection.BOTH to BicycleDirectionSource.INFERRED_CONTRAFLOW
        vehicle != null -> vehicle to BicycleDirectionSource.VEHICLE
        else -> general to BicycleDirectionSource.GENERAL
    }

    val isConditional = listOf("oneway:conditional", "oneway:vehicle:conditional", "oneway:bicycle:conditional")
        .any { it in tags }

    return OnewayInterpretation(
        general = general,
        motorVehicle = motorVehicle,
        bicycle = if (isBicycleAccessible(tags)) bicycle else null,
        bicycleSource = source,
        isGeneralImplied = explicitGeneral == null && isRoundabout,
        isConditional = isConditional,
        issues = issues,
    )
}

/** Whether bicycles are (not explicitly not) allowed. Unknown access counts as accessible */
fun isBicycleAccessible(tags: Map<String, String>): Boolean {
    val bicycle = tags["bicycle"]
    if (bicycle != null) return bicycle !in NO_ACCESS
    if (tags["vehicle"] in NO_ACCESS) return false
    if (tags["access"] in NO_ACCESS && tags["vehicle"] == null) return false
    return tags["highway"] != "motorway"
}

private val NO_ACCESS = setOf("no", "private", "use_sidepath")

private fun hasLegacyOpposite(tags: Map<String, String>): Boolean =
    listOf("cycleway", "cycleway:left", "cycleway:right", "cycleway:both")
        .any { tags[it]?.startsWith("opposite") == true }

/** Bicycle infrastructure on the road that may be used against the (bicycle) oneway direction */
private fun hasContraflow(tags: Map<String, String>, onewayDirection: TravelDirection): Boolean {
    val contra = if (onewayDirection == TravelDirection.FORWARD) TravelDirection.BACKWARD else TravelDirection.FORWARD
    for (side in listOf("left", "right", "both")) {
        val cycleway = tags["cycleway:$side"] ?: continue
        if (cycleway in NO_CYCLEWAY_ON_ROAD) continue
        val direction = parseOnewayValue(tags["cycleway:$side:oneway"] ?: tags["cycleway:both:oneway"])
        if (direction == contra || direction == TravelDirection.BOTH) return true
    }
    val contraKey = if (contra == TravelDirection.FORWARD) "bicycle:forward" else "bicycle:backward"
    return tags[contraKey] in setOf("yes", "designated", "permissive")
}

/** values of cycleway:<side> that are no infrastructure on the road itself */
private val NO_CYCLEWAY_ON_ROAD = setOf("no", "none", "separate", "sidepath", "use_sidepath")
