package de.westnordost.streetcomplete.osm.cycleway

import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.cycleway.Cycleway.SEPARATE
import de.westnordost.streetcomplete.osm.oneway.Direction
import de.westnordost.streetcomplete.osm.oneway.isNotOnewayForCyclists
import de.westnordost.streetcomplete.osm.oneway.isOneway
import kotlinx.serialization.Serializable

/* Whether it is compulsory to use a separately mapped cycleway (bicycle=use_sidepath).

   This is a statement about the road as a whole, not about one of the sides: for each travel
   direction of the road (relative to the direction of the OSM way, OSM wiki Tag:bicycle=use_sidepath:
   "The cycleways OSM direction is not important"), is a cycleway compulsory to use? It is on the
   user to look at all cycleways, so neither sides nor directions of the cycleways are needed.

   - cycleway=separate does not imply anything about this, nothing is ever derived from it.
   - "No" and "can't tell" are the same, nothing is written. Quests are hidden instead, see
     SIDEPATH_USAGE_QUEST_NAME.
   - bicycle=optional_sidepath is never written. It and any other bicycle access value is
     protected: such a direction is not touched. */

/** Where the usage of a separate cycleway is compulsory (relative to the direction of the road
 *  way). [NONE] is only used to remove a use_sidepath that is not valid anymore, after
 *  confirmation by the user */
@Serializable
enum class RoadSidepath {
    /** compulsory in the direction of the OSM way: bicycle:forward=use_sidepath */
    FORWARD,
    /** compulsory against the direction of the OSM way: bicycle:backward=use_sidepath */
    BACKWARD,
    /** compulsory in both directions: bicycle=use_sidepath */
    BOTH,
    /** removes bicycle(:forward|backward)=use_sidepath */
    NONE;

    val directions: Set<Direction> get() = when (this) {
        FORWARD -> setOf(Direction.FORWARD)
        BACKWARD -> setOf(Direction.BACKWARD)
        BOTH -> setOf(Direction.FORWARD, Direction.BACKWARD)
        NONE -> emptySet()
    }
}

/** Countries for which compulsory cycleways by sign are verified in the original sources:
 *  - DE: StVO §2 (4): use is only compulsory if ordered by Zeichen 237, 240 or 241
 *  - NL: RVV 1990 art. 5: cyclists use the "verplicht fietspad" (signs G11, G12a)
 *  Wiki Tag:bicycle=use_sidepath also lists PL, FI and IT, but their laws were not verified. */
val SIDEPATH_USAGE_COUNTRIES = setOf("DE", "NL")

fun supportsSidepathUsage(countryCode: String?): Boolean = countryCode in SIDEPATH_USAGE_COUNTRIES

/** Name of the quest type that asks about this. Quest hidden by the user for an element (with the
 *  standard hide function, which is persisted) is not shown again. */
const val SIDEPATH_USAGE_QUEST_NAME = "AddSidepathUsage"

private const val USE_SIDEPATH = "use_sidepath"
private const val OPTIONAL_SIDEPATH = "optional_sidepath"

private val SIDEPATH_ROADS = setOf(
    "trunk", "trunk_link", "primary", "primary_link", "secondary", "secondary_link",
    "tertiary", "tertiary_link", "unclassified", "residential"
)

private val NO_BICYCLE = setOf("private", "no", "dismount")

private val BICYCLE_DIRECTIONS = listOf(Direction.FORWARD, Direction.BACKWARD)

private fun Direction.key() = when (this) {
    Direction.FORWARD -> "bicycle:forward"
    Direction.BACKWARD -> "bicycle:backward"
    Direction.BOTH -> throw IllegalArgumentException()
}

/** value of bicycle access for the given direction (directional key wins over bare key) */
private fun Map<String, String>.effectiveBicycle(direction: Direction): String? =
    this[direction.key()] ?: this["bicycle"]

fun hasUseSidepath(tags: Map<String, String>, direction: Direction): Boolean =
    tags.effectiveBicycle(direction) == USE_SIDEPATH

/** Whether anything about the usage of a sidepath is tagged already. use_sidepath, but also
 *  optional_sidepath, which must not be touched */
fun hasSidepathTagging(tags: Map<String, String>): Boolean =
    listOf("bicycle", "bicycle:forward", "bicycle:backward").any {
        tags[it] == USE_SIDEPATH || tags[it] == OPTIONAL_SIDEPATH
    }

/** Roads on which bicycle=use_sidepath may be tagged at all */
internal fun isSidepathRoad(tags: Map<String, String>): Boolean {
    if (tags["highway"] !in SIDEPATH_ROADS) return false
    if (tags["area"] == "yes" || tags["motorroad"] == "yes" || tags["expressway"] == "yes") return false
    if (tags["bicycle_road"] == "yes" || tags["cyclestreet"] == "yes") return false
    val bicycle = tags["bicycle"]
    if (bicycle in NO_BICYCLE) return false
    if (bicycle == null) {
        val vehicle = tags["vehicle"]
        if (vehicle in NO_BICYCLE) return false
        if (vehicle == null && tags["access"] in NO_BICYCLE) return false
    }
    return true
}

/** The directions for which bicycle=use_sidepath may be written without overwriting any other
 *  access information:
 *  - not at all on roads where it does not apply or if any conditional bicycle tag exists
 *  - not for directions with another bicycle value (yes, no, designated, optional_sidepath, ...)
 *  - on oneways that are oneways for cyclists too, only the direction of the flow */
fun getWritableSidepathDirections(tags: Map<String, String>, isLeftHandTraffic: Boolean): Set<Direction> {
    if (!isSidepathRoad(tags)) return emptySet()
    if (tags.keys.any { it.startsWith("bicycle") && it.endsWith(":conditional") }) return emptySet()
    var result = BICYCLE_DIRECTIONS.filter {
        val value = tags.effectiveBicycle(it)
        value == null || value == USE_SIDEPATH
    }
    if (isOneway(tags) && !isNotOnewayForCyclists(tags, isLeftHandTraffic)) {
        result = result.filter { it == Direction.from(tags) }
    }
    return result.toSet()
}

/** The answers that may be given for the road. Empty if nothing may be written */
fun getSidepathAnswers(tags: Map<String, String>, isLeftHandTraffic: Boolean): List<RoadSidepath> {
    val directions = getWritableSidepathDirections(tags, isLeftHandTraffic)
    return when {
        Direction.FORWARD in directions && Direction.BACKWARD in directions ->
            listOf(RoadSidepath.FORWARD, RoadSidepath.BACKWARD, RoadSidepath.BOTH)
        Direction.FORWARD in directions -> listOf(RoadSidepath.FORWARD)
        Direction.BACKWARD in directions -> listOf(RoadSidepath.BACKWARD)
        else -> emptyList()
    }
}

/** The usage as currently tagged, null if not tagged with use_sidepath */
fun getExistingRoadSidepath(tags: Map<String, String>): RoadSidepath? {
    val forward = hasUseSidepath(tags, Direction.FORWARD)
    val backward = hasUseSidepath(tags, Direction.BACKWARD)
    return when {
        forward && backward -> RoadSidepath.BOTH
        forward -> RoadSidepath.FORWARD
        backward -> RoadSidepath.BACKWARD
        else -> null
    }
}

/** Whether giving [answer] removes an existing use_sidepath of the road and so, needs the
 *  confirmation of the user. Only directions that may be written are considered */
fun needsSidepathChangeConfirmation(
    tags: Map<String, String>,
    answer: RoadSidepath,
    isLeftHandTraffic: Boolean,
): Boolean =
    answer != RoadSidepath.NONE &&
    getWritableSidepathDirections(tags, isLeftHandTraffic).any {
        hasUseSidepath(tags, it) && it !in answer.directions
    }

private fun Sides<CyclewayAndDirection>.isAnySeparate(original: Sides<CyclewayAndDirection>?) =
    (left ?: original?.left)?.cycleway == SEPARATE || (right ?: original?.right)?.cycleway == SEPARATE

/** Whether the follow-up question about the usage of the separate cycleways should be asked: in
 *  supported countries, if there is a separate cycleway and an answer may be written */
fun needsSidepathQuestion(
    tags: Map<String, String>,
    original: Sides<CyclewayAndDirection>,
    sides: Sides<CyclewayAndDirection>,
    isLeftHandTraffic: Boolean,
    countryCode: String?,
): Boolean =
    supportsSidepathUsage(countryCode) &&
    sides.isAnySeparate(original) &&
    getSidepathAnswers(tags, isLeftHandTraffic).isNotEmpty()

/** Whether the user changed a separate cycleway to something else, so that there is none left but
 *  use_sidepath is still tagged. This contradicts, but is only removed after confirmation. */
fun needsStaleSidepathRemoval(
    tags: Map<String, String>,
    original: Sides<CyclewayAndDirection>,
    sides: Sides<CyclewayAndDirection>,
): Boolean {
    if (sides.isAnySeparate(original)) return false
    val changedAway =
        (original.left?.cycleway == SEPARATE && sides.left != null && sides.left.cycleway != SEPARATE) ||
        (original.right?.cycleway == SEPARATE && sides.right != null && sides.right.cycleway != SEPARATE)
    return changedAway && BICYCLE_DIRECTIONS.any { hasUseSidepath(tags, it) }
}

/** The answer attached to the sides (it is the same for all sides) */
fun Sides<CyclewayAndDirection>.withRoadSidepath(value: RoadSidepath?): Sides<CyclewayAndDirection> =
    Sides(left?.copy(roadSidepath = value), right?.copy(roadSidepath = value))

val Sides<CyclewayAndDirection>.roadSidepath: RoadSidepath? get() =
    listOfNotNull(left?.roadSidepath, right?.roadSidepath).distinct().singleOrNull()

/* ---------------------------------------- creator ------------------------------------------- */

/** Applies only the answer attached to the sides to [tags], without touching any cycleway tag */
fun Sides<CyclewayAndDirection>.applySidepathUsageTo(tags: Tags, isLeftHandTraffic: Boolean) {
    val answer = roadSidepath ?: return
    tags.applyRoadSidepath(answer, isLeftHandTraffic)
}

/** Used by the cycleway creator. Decisions are based on the tags [original] before anything was
 *  changed, i.e. the same tags that the user has been asked about. */
internal fun Sides<CyclewayAndDirection>.applySidepathUsage(
    original: Map<String, String>,
    tags: Tags,
    isLeftHandTraffic: Boolean,
) {
    val answer = roadSidepath ?: return
    // not without a separate cycleway
    if (answer != RoadSidepath.NONE && !isAnySeparate(parseCyclewaySides(original, isLeftHandTraffic))) return
    tags.applyRoadSidepath(answer, isLeftHandTraffic, original)
}

/** Writes the answer to the tags. Only use_sidepath values are ever set or removed. Directions
 *  that are protected or not applicable are not touched. A direction that is not covered by the
 *  answer loses its use_sidepath: the answer is about the road as a whole. */
fun Tags.applyRoadSidepath(
    answer: RoadSidepath,
    isLeftHandTraffic: Boolean,
    decisionTags: Map<String, String> = toMap(),
) {
    if (answer == RoadSidepath.NONE) {
        for (direction in BICYCLE_DIRECTIONS) {
            if (hasUseSidepath(decisionTags, direction)) setSidepathValue(direction, false)
        }
        return
    }
    if (answer !in getSidepathAnswers(decisionTags, isLeftHandTraffic)) return
    val directions = getWritableSidepathDirections(decisionTags, isLeftHandTraffic)
    for (direction in BICYCLE_DIRECTIONS) {
        if (direction in directions) setSidepathValue(direction, direction in answer.directions)
    }
}

private fun Tags.setIfDifferent(key: String, value: String) {
    if (this[key] != value) this[key] = value
}

private fun Tags.removeIfUseSidepath(key: String) {
    if (this[key] == USE_SIDEPATH) remove(key)
}

/** Sets or removes use_sidepath for the direction. Other values, also of the other direction, stay
 *  as they are. If both directions are use_sidepath, the bare bicycle tag is used, else the
 *  directional ones. */
private fun Tags.setSidepathValue(direction: Direction, use: Boolean) {
    val value = if (use) USE_SIDEPATH else null
    val key = direction.key()
    val otherKey = direction.reverse().key()
    val bare = this["bicycle"]
    val effective = this[key] ?: bare
    // other access values are protected
    if (effective != null && effective != USE_SIDEPATH) return
    if (value == null && effective == null) return

    if (bare != null && bare != USE_SIDEPATH) {
        // e.g. bicycle=yes with a directional override. Only the directional tag may be touched
        if (value == null) remove(key) else setIfDifferent(key, value)
        return
    }

    val otherKeyValue = this[otherKey]
    val otherEffective = otherKeyValue ?: bare
    val otherIsUseOrNone = otherEffective == null || otherEffective == USE_SIDEPATH
    if (otherIsUseOrNone && value == otherEffective) {
        if (value == null) remove("bicycle") else setIfDifferent("bicycle", value)
        removeIfUseSidepath(key)
        removeIfUseSidepath(otherKey)
    } else {
        if (bare != null) remove("bicycle")
        // the other direction was covered by the bare tag, make it explicit
        if (otherKeyValue == null && otherEffective != null) setIfDifferent(otherKey, otherEffective)
        if (value == null) remove(key) else setIfDifferent(key, value)
    }
}
