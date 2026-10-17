package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.osm.ALL_PATHS
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.Tags

/** Direction along an OSM way. FORWARD is the direction in which the way is drawn, i.e. from its
 *  first to its last node. It has nothing to do with the viewing direction of the user, the
 *  compass direction or the left/right side of the way. Forms must therefore show it along the way
 *  geometry (e.g. with arrows on the way, like for oneways). */
enum class TrafficSignDirection(val osmSuffix: String) {
    FORWARD("forward"),
    BACKWARD("backward");

    val opposite: TrafficSignDirection get() = when (this) {
        FORWARD -> BACKWARD
        BACKWARD -> FORWARD
    }
}

/** The traffic signs surveyed for a way, for each direction of travel along the way (see
 *  [TrafficSignDirection]). Each direction is in one of three states:
 *  - signed: a [TrafficSignValue] with signs
 *  - explicitly no signs: [TrafficSignValue.NONE]
 *  - not surveyed: null. Nothing is said about this direction: neither its traffic sign value nor
 *    its access tags are changed. It never becomes "none" automatically.
 *
 *  If the resulting signs are the same in both directions, they are tagged as traffic_sign=*,
 *  otherwise as traffic_sign:forward=* and traffic_sign:backward=*. */
data class TrafficSignWaySigns(
    val forward: TrafficSignValue?,
    val backward: TrafficSignValue?,
) {
    /** whether both directions are surveyed and have the same signs */
    val isSameInBothDirections: Boolean get() =
        forward != null && backward != null && forward.toOsmValue() == backward.toOsmValue()

    operator fun get(direction: TrafficSignDirection): TrafficSignValue? = when (direction) {
        TrafficSignDirection.FORWARD -> forward
        TrafficSignDirection.BACKWARD -> backward
    }

    /** the same signs seen on the way if it was reversed */
    fun reversed(): TrafficSignWaySigns = TrafficSignWaySigns(backward, forward)

    companion object {
        fun both(value: TrafficSignValue) = TrafficSignWaySigns(value, value)
    }
}

/** The traffic signs currently tagged on a way, per direction. null if nothing is tagged for that
 *  direction. To prefill a form. */
fun parseTrafficSignWaySigns(
    tags: Map<String, String>,
    catalogue: TrafficSignCatalogue,
): Pair<TrafficSignValue?, TrafficSignValue?> {
    val both = tags["traffic_sign"]
    val forward = tags["traffic_sign:forward"] ?: both
    val backward = tags["traffic_sign:backward"] ?: both
    return forward?.let { parseTrafficSignValue(it, catalogue) } to
        backward?.let { parseTrafficSignValue(it, catalogue) }
}

/** Result of deriving the tags of a way from the traffic signs that were surveyed for it, see
 *  [deriveWayTags]. Nothing is changed until [applyTo] is called. */
data class TrafficSignWayTagging(
    /** resulting value of traffic_sign, or null if the signs differ per direction */
    val trafficSign: String?,
    /** resulting value of the signs in forward direction (if not surveyed: the existing one, may be
     *  null). Written to traffic_sign:forward if it differs from backward */
    val trafficSignForward: String?,
    /** resulting value of the signs in backward direction, see [trafficSignForward] */
    val trafficSignBackward: String?,
    val derivation: TrafficSignDerivation,
    /** all changes, including the ones of traffic_sign. Empty if the way is already tagged so */
    val changes: List<TrafficSignTagChange>,
    /** old and new value of highway=*, if it is changed */
    val highwayChange: Pair<String, String>?,
    /** existing tags that contradicted the signs and are changed or removed by [changes] */
    val resolvedConflicts: List<TrafficSignTagConflict>,
    /** existing tags that (may) contradict the signs but are left as they are because it is not
     *  clear how to resolve them. These need to be shown to the user */
    val unresolvedConflicts: List<TrafficSignTagConflict>,
    /** what could not be derived from the signs and why */
    val notDerived: List<TrafficSignNotDerived>,
) {
    fun applyTo(tags: Tags) {
        for (change in changes) {
            when (change) {
                is TrafficSignTagChange.Set -> tags[change.key] = change.value
                is TrafficSignTagChange.Remove -> tags.remove(change.key)
            }
        }
    }
}

enum class TrafficSignDerivation {
    /** all that the signs express is tagged, no conflicts remain */
    FULL,
    /** some tags are derived, but not everything could be derived or conflicts remain */
    PARTIAL,
    /** only traffic_sign (or traffic_sign:forward/backward) is tagged */
    TRAFFIC_SIGN_ONLY,
}

sealed interface TrafficSignTagChange {
    val key: String
    data class Set(override val key: String, val value: String, val previousValue: String?) : TrafficSignTagChange
    data class Remove(override val key: String, val previousValue: String) : TrafficSignTagChange
}

/** An existing tag that contradicts the signs. [direction] is set if this only concerns the signs
 *  of one direction. */
data class TrafficSignTagConflict(
    val key: String,
    val value: String,
    val reason: Reason,
    val direction: TrafficSignDirection? = null,
) {
    enum class Reason {
        /** e.g. bicycle=yes with DE:239 (without "Radfahrer frei") or foot=no with DE:240 */
        CONTRADICTS_SIGN,
        /** segregated=* on a way that is not a shared foot- and cycleway */
        SEGREGATION_NOT_APPLICABLE,
        /** e.g. footway:surface on a way that is not a segregated foot- and cycleway */
        SEGREGATED_PART_ATTRIBUTE,
        /** e.g. foot=yes on an exclusive cycleway (DE:237) */
        FOOT_ON_EXCLUSIVE_CYCLEWAY,
        /** e.g. vehicle=no or access=no with DE:260 (which allows bicycles) */
        MORE_RESTRICTIVE_GENERAL_ACCESS,
        /** e.g. hgv=delivery or taxi=yes with DE:250,1020-30: an exception that is not on the
         *  surveyed signs, or that cannot be compared to them */
        UNCLEAR_ACCESS,
        /** highway=cycleway implies bicycle=designated, which contradicts e.g. DE:250 */
        ACCESS_IMPLIED_BY_HIGHWAY,
        /** the signs are now tagged per direction instead of for both directions or vice versa:
         *  the existing traffic_sign, traffic_sign:forward or traffic_sign:backward is removed */
        TRAFFIC_SIGN_DIRECTION_CHANGED,
        /** the signs of both directions lead to different values of a property that can not be
         *  tagged per direction (e.g. segregated=*), so the existing value is kept */
        DIFFERS_BY_DIRECTION,
    }
}

/** Something that could not be derived from the signs. [direction] is set if this only concerns
 *  the signs of one direction. */
data class TrafficSignNotDerived(
    val reason: Reason,
    val detail: String? = null,
    val direction: TrafficSignDirection? = null,
) {
    enum class Reason {
        /** the traffic sign value could not be parsed, e.g. signs of another country */
        UNPARSEABLE_VALUE,
        /** the value contains signs that are not in the catalogue */
        UNKNOWN_SIGN,
        /** a variable value is missing or given for a sign without variable value */
        INVALID_VALUE,
        /** the combination of signs is not (yet) supported */
        UNSUPPORTED_COMBINATION,
        /** the way has no highway=* */
        NOT_A_HIGHWAY,
        /** the signs are not applicable to this kind of highway, e.g. DE:237 on highway=residential */
        UNSUPPORTED_HIGHWAY,
        /** the way is an area */
        AREA,
        /** the highway would need to be changed, but there is a sub-type like footway=sidewalk that
         *  can not be carried over */
        HIGHWAY_SUBTYPE_PRESENT,
        /** e.g. bicycle:conditional: it is not clear how the signs relate to the condition */
        CONDITIONAL_TAGS_PRESENT,
        /** e.g. motor_vehicle:lanes */
        LANE_TAGS_PRESENT,
        /** the additional sign indicates a different highway classification (e.g. "Anlieger frei"
         *  -> maybe highway=residential or service), which can not be derived from the sign alone */
        HIGHWAY_CLASSIFICATION_HINT,
        /** the signs of the two directions require different highway=* (e.g. DE:237 and DE:239) */
        CONFLICTING_HIGHWAY_CLASSIFICATION,
        /** the signs of one direction require a different highway=*, but highway=* can only be
         *  changed if the signs of both directions are foot- and cycleway signs: otherwise the
         *  access implied by highway=* would silently change for the other direction */
        HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS,
    }
}

/** Derive which tags should change on a way with the given tags if it is signed with this traffic
 *  sign value in both directions, see [TrafficSignWaySigns.deriveWayTags] */
fun TrafficSignValue.deriveWayTags(tags: Map<String, String>): TrafficSignWayTagging =
    TrafficSignWaySigns.both(this).deriveWayTags(tags)

/** Derive which tags should change on a way with the given tags if it is signed with these traffic
 *  signs. The given tags are not modified.
 *
 *  The traffic signs are always tagged, also if nothing else can be derived. Other tags are only
 *  derived for an explicitly supported set of signs (see [WAY_RULES]). A direction with "none"
 *  does not change any other tags.
 *
 *  The signs of each direction are evaluated on a view of the tags for that direction, in which
 *  access tags for that direction (e.g. bicycle:forward) replace the general ones (bicycle). The
 *  results are then merged again: access tags that differ per direction are tagged as
 *  key:forward / key:backward, everything else (highway, segregated, bicycle:signed, ...) can not be
 *  tagged per direction and must be the same for both directions. */
fun TrafficSignWaySigns.deriveWayTags(tags: Map<String, String>): TrafficSignWayTagging {
    val original = tags.toMap()
    val directions = TrafficSignDirection.entries
    val views = directions.associateWith { directionalView(original, it) }
    val notDerivedByDir = directions.associateWith { ArrayList<TrafficSignNotDerived>() }
    val globalNotDerived = ArrayList<TrafficSignNotDerived>()

    val rules = directions.associateWith { dir ->
        val value = this[dir]
        if (value == null || value.isNone) null else value.findRule(notDerivedByDir.getValue(dir))
    }
    // first pass: which rules are applicable with the current highway=*
    val derived = directions.associateWith { dir ->
        rules[dir]?.derive(views.getValue(dir), null, notDerivedByDir.getValue(dir))
    }.toMutableMap()

    // decide on a common highway=*
    val highway = original["highway"]
    val applicableRules = directions.mapNotNull { dir -> rules[dir]?.takeIf { derived[dir] != null } }
    val requiredHighways = applicableRules.mapNotNull { it.requiredHighway }.toSet()
    if (requiredHighways.size > 1) {
        globalNotDerived.add(TrafficSignNotDerived(
            TrafficSignNotDerived.Reason.CONFLICTING_HIGHWAY_CLASSIFICATION,
            requiredHighways.sorted().joinToString(",")
        ))
        for (dir in directions) derived[dir] = null
    } else if (requiredHighways.size == 1) {
        val commonHighway = requiredHighways.single()
        val allArePathRules = directions.all { dir -> rules[dir]?.isPathRule == true && derived[dir] != null }
        if (commonHighway != highway && !allArePathRules) {
            for (dir in directions) {
                if (rules[dir]?.requiredHighway == null || derived[dir] == null) continue
                notDerivedByDir.getValue(dir).add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS, commonHighway))
                derived[dir] = null
            }
        } else {
            // second pass: shared and segregated foot- and cycleways adopt the common highway
            for (dir in directions) {
                if (derived[dir] == null) continue
                val secondPassNotDerived = ArrayList<TrafficSignNotDerived>()
                derived[dir] = rules.getValue(dir)!!.derive(views.getValue(dir), commonHighway, secondPassNotDerived)
                if (derived[dir] == null) notDerivedByDir.getValue(dir).addAll(secondPassNotDerived)
            }
        }
    }

    // merge
    val resultViews = directions.associateWith { dir -> derived[dir]?.tags ?: views.getValue(dir) }
    val unresolvedByDir = directions.associateWith { dir -> derived[dir]?.unresolved.orEmpty() }
    val resolvedByDir = directions.associateWith { dir -> derived[dir]?.resolved.orEmpty() }
    val mergeConflicts = ArrayList<TrafficSignTagConflict>()
    val definedKeys = directions.associateWith { dir -> derived[dir]?.definedKeys.orEmpty() }
    val result = mergeDirections(original, resultViews, definedKeys, mergeConflicts)

    // traffic signs
    val resolvedTrafficSign = ArrayList<TrafficSignTagConflict>()
    // a direction that is not surveyed keeps the signs it has now (also if they are only tagged in
    // the general traffic_sign)
    val oldForwardValue = original["traffic_sign:forward"] ?: original["traffic_sign"]
    val oldBackwardValue = original["traffic_sign:backward"] ?: original["traffic_sign"]
    val forwardValue = forward?.toOsmValue() ?: oldForwardValue
    val backwardValue = backward?.toOsmValue() ?: oldBackwardValue
    val isSame = forwardValue != null && forwardValue == backwardValue
    val newTrafficSignTags = when {
        forward == null && backward == null -> original.filterKeys { it in TRAFFIC_SIGN_KEYS }
        isSame -> mapOf("traffic_sign" to forwardValue!!)
        else -> listOfNotNull(
            forwardValue?.let { "traffic_sign:forward" to it },
            backwardValue?.let { "traffic_sign:backward" to it },
        ).toMap()
    }
    for (key in TRAFFIC_SIGN_KEYS) {
        val newValue = newTrafficSignTags[key]
        if (newValue != null) {
            result[key] = newValue
        } else {
            val oldValue = original[key] ?: continue
            result.remove(key)
            // only a conflict if the removed value does not still apply to its direction(s)
            val stillApplies = when (key) {
                // the general value only applied to directions without own value
                "traffic_sign" ->
                    ("traffic_sign:forward" in original || oldValue == forwardValue) &&
                    ("traffic_sign:backward" in original || oldValue == backwardValue)
                "traffic_sign:forward" -> oldValue == forwardValue
                else -> oldValue == backwardValue
            }
            if (!stillApplies) {
                resolvedTrafficSign.add(TrafficSignTagConflict(key, oldValue, TrafficSignTagConflict.Reason.TRAFFIC_SIGN_DIRECTION_CHANGED))
            }
        }
    }

    val notDerived = globalNotDerived +
        mergeByDirection(notDerivedByDir) { it.direction }.map { (item, dir) -> item.copy(direction = dir) }
    val unresolved = mergeByDirection(unresolvedByDir) { it.direction }.map { (c, dir) -> c.copy(direction = dir) } + mergeConflicts
    val resolved = mergeByDirection(resolvedByDir) { it.direction }.map { (c, dir) -> c.copy(direction = dir) } + resolvedTrafficSign

    val signedDirections = directions.filter { this[it]?.isNone == false }
    val derivedDirections = directions.filter { derived[it] != null }
    val derivation = when {
        derivedDirections.isEmpty() -> TrafficSignDerivation.TRAFFIC_SIGN_ONLY
        derivedDirections.size == signedDirections.size && notDerived.isEmpty() && unresolved.isEmpty() -> TrafficSignDerivation.FULL
        else -> TrafficSignDerivation.PARTIAL
    }
    val newHighway = result["highway"]
    return TrafficSignWayTagging(
        trafficSign = if (isSame) forwardValue else null,
        trafficSignForward = forwardValue,
        trafficSignBackward = backwardValue,
        derivation = derivation,
        changes = diff(original, result),
        highwayChange = if (highway != null && newHighway != null && highway != newHighway) highway to newHighway else null,
        resolvedConflicts = resolved,
        unresolvedConflicts = unresolved,
        notDerived = notDerived,
    )
}

private val TRAFFIC_SIGN_KEYS = listOf("traffic_sign", "traffic_sign:forward", "traffic_sign:backward")

/** Access keys that may be tagged per direction, e.g. bicycle:forward. Lazy because the sets it
 *  consists of are defined further below */
private val DIRECTIONAL_KEYS: Set<String> by lazy {
    setOf("access", "foot", "horse", "vehicle", "emergency") + VEHICLE_TYPES + ACCESS_USE_CATEGORIES
}

/** whether [key] (without direction) is an access key that may be tagged per direction, e.g.
 *  bicycle or bicycle:conditional, but not e.g. bicycle:signed or bicycle:lanes */
private fun isDirectionalAccessKey(key: String): Boolean {
    val segments = key.split(':')
    return segments[0] in DIRECTIONAL_KEYS && segments.drop(1).all { it == "conditional" }
}

/** The tags as they apply for the given direction: For directional access keys, e.g.
 *  bicycle:forward=yes becomes bicycle=yes for the forward direction, bicycle:backward is dropped.
 *  The same for sub-keys, e.g. bicycle:forward:conditional becomes bicycle:conditional. */
private fun directionalView(tags: Map<String, String>, direction: TrafficSignDirection): Map<String, String> {
    val result = HashMap<String, String>()
    val overrides = HashMap<String, String>()
    for ((key, value) in tags) {
        if (key in TRAFFIC_SIGN_KEYS) continue
        val segments = key.split(':')
        val directionSegment = segments.drop(1).singleOrNull { it == "forward" || it == "backward" }
        val keyWithoutDirection = segments.filter { it != directionSegment }.joinToString(":")
        if (directionSegment == null || !isDirectionalAccessKey(keyWithoutDirection)) {
            result[key] = value
        } else if (directionSegment == direction.osmSuffix) {
            overrides[keyWithoutDirection] = value
        }
    }
    result.putAll(overrides)
    return result
}

/** Merge the derived tags of both directions into one set of tags */
private fun mergeDirections(
    original: Map<String, String>,
    views: Map<TrafficSignDirection, Map<String, String>>,
    definedKeys: Map<TrafficSignDirection, Set<String>>,
    conflicts: MutableList<TrafficSignTagConflict>,
): MutableMap<String, String> {
    val forward = views.getValue(TrafficSignDirection.FORWARD)
    val backward = views.getValue(TrafficSignDirection.BACKWARD)
    val originalForward = directionalView(original, TrafficSignDirection.FORWARD)
    val originalBackward = directionalView(original, TrafficSignDirection.BACKWARD)
    val result = original.toMutableMap()

    val keys = (forward.keys + backward.keys + originalForward.keys + originalBackward.keys)
    for (key in keys) {
        val f = forward[key]
        val b = backward[key]
        if (isDirectionalAccessKey(key)) {
            // tags of directional keys of untouched directions are left exactly as they are
            if (f == originalForward[key] && b == originalBackward[key]) continue
            encodeDirectional(result, key, f, b, original[key],
                inheritedForward = { inheritedAccessValue(key, forward) },
                inheritedBackward = { inheritedAccessValue(key, backward) },
            )
        } else {
            val o = original[key]
            val definedForward = key in definedKeys.getValue(TrafficSignDirection.FORWARD)
            val definedBackward = key in definedKeys.getValue(TrafficSignDirection.BACKWARD)
            val merged = when {
                f == b -> f
                // a shared foot- and cycleway in one direction and e.g. a footway (or unchanged
                // segregated=no) in the other is not segregated
                key == "segregated" && "no" in setOf(f, b) && setOf(f, b).all { it == null || it == "no" } -> "no"
                // only the signs of one direction say something about it
                definedForward && !definedBackward -> f
                definedBackward && !definedForward -> b
                !definedForward && !definedBackward -> o
                else -> {
                    if (o != null) conflicts.add(TrafficSignTagConflict(key, o, TrafficSignTagConflict.Reason.DIFFERS_BY_DIRECTION))
                    else conflicts.add(TrafficSignTagConflict(key, f ?: b!!, TrafficSignTagConflict.Reason.DIFFERS_BY_DIRECTION))
                    o
                }
            }
            if (merged != null) result[key] = merged else result.remove(key)
        }
    }
    return result
}

/** Write the values [f] (forward) and [b] (backward) of [key], changing as little as possible: A
 *  general value that is still valid for one direction is kept. If the key was removed for the
 *  other direction (= it inherits the value of a more general access key, e.g. motor_vehicle from
 *  vehicle), the inherited value is written explicitly for that direction so that the general value
 *  can be kept. Only if that is not possible (the value is implied by highway=*), the general value
 *  is moved to the key of its direction. */
private fun encodeDirectional(
    result: MutableMap<String, String>,
    key: String,
    f: String?,
    b: String?,
    general: String?,
    inheritedForward: () -> String?,
    inheritedBackward: () -> String?,
) {
    val (base, rest) = splitKey(key)
    val forwardKey = joinKey(base, "forward", rest)
    val backwardKey = joinKey(base, "backward", rest)
    fun put(k: String, v: String?) { if (v != null) result[k] = v else result.remove(k) }
    val explicitF = f ?: inheritedForward()
    val explicitB = b ?: inheritedBackward()
    when {
        f == b -> { put(key, f); put(forwardKey, null); put(backwardKey, null) }
        general != null && general == f && explicitB != null -> { put(forwardKey, null); put(backwardKey, explicitB) }
        general != null && general == b && explicitF != null -> { put(forwardKey, explicitF); put(backwardKey, null) }
        else -> { put(key, null); put(forwardKey, f); put(backwardKey, b) }
    }
}

/** The value a vehicle type inherits from the more general access keys in the given (directional
 *  view of the) tags, e.g. vehicle=no for motorcar. null if nothing is tagged explicitly (then the
 *  access is implied by highway=*). Only for plain keys, not e.g. for bicycle:conditional */
private fun inheritedAccessValue(key: String, tags: Map<String, String>): String? {
    val parents = when (key) {
        in MOTOR_VEHICLE_TYPES -> listOf("motor_vehicle", "vehicle", "access")
        in VEHICLE_TYPES -> listOf("vehicle", "access")
        "vehicle", "foot", "horse" -> listOf("access")
        else -> return null
    }
    return parents.firstNotNullOfOrNull { tags[it] }
}

/** "bicycle:conditional" -> ("bicycle", "conditional"), "bicycle" -> ("bicycle", null) */
private fun splitKey(key: String): Pair<String, String?> {
    val i = key.indexOf(':')
    return if (i < 0) key to null else key.substring(0, i) to key.substring(i + 1)
}

private fun joinKey(base: String, direction: String, rest: String?): String =
    if (rest == null) "$base:$direction" else "$base:$direction:$rest"

/** Items found for both directions get direction null, the others the direction they were found for */
private fun <T> mergeByDirection(
    byDirection: Map<TrafficSignDirection, List<T>>,
    directionOf: (T) -> TrafficSignDirection?,
): List<Pair<T, TrafficSignDirection?>> {
    val forward = byDirection.getValue(TrafficSignDirection.FORWARD)
    val backward = byDirection.getValue(TrafficSignDirection.BACKWARD)
    val result = ArrayList<Pair<T, TrafficSignDirection?>>()
    for (item in forward) {
        result.add(item to if (item in backward) directionOf(item) else TrafficSignDirection.FORWARD)
    }
    for (item in backward) {
        if (item !in forward) result.add(item to TrafficSignDirection.BACKWARD)
    }
    return result
}

/** whether [key] is a conditional key of one of the [baseKeys], e.g. bicycle:conditional */
private fun isConditionalKeyOf(key: String, baseKeys: Set<String>): Boolean {
    val segments = key.split(':')
    return segments.size > 1 && segments[0] in baseKeys && "conditional" in segments
}

private fun diff(before: Map<String, String>, after: Map<String, String>): List<TrafficSignTagChange> {
    val changes = ArrayList<TrafficSignTagChange>()
    for ((key, value) in before) {
        val newValue = after[key]
        if (newValue == null) changes.add(TrafficSignTagChange.Remove(key, value))
        else if (newValue != value) changes.add(TrafficSignTagChange.Set(key, newValue, value))
    }
    for ((key, value) in after) {
        if (key !in before) changes.add(TrafficSignTagChange.Set(key, value, null))
    }
    return changes
}

/* ------------------------------------------ rules ----------------------------------------- */

private class Derived(
    val tags: Map<String, String>,
    val resolved: List<TrafficSignTagConflict>,
    val unresolved: List<TrafficSignTagConflict>,
    /** keys that can not be tagged per direction (e.g. highway, segregated) whose value is defined
     *  by the signs (also if it stays the same). Access keys are not included */
    val definedKeys: Set<String> = emptySet(),
)

private sealed interface WayRule {
    /** whether this is a rule for a foot- or cycleway sign */
    val isPathRule: Boolean
    /** the highway=* this sign requires, null if it does not require a specific one */
    val requiredHighway: String?
    /** Returns null if the rule is not applicable to the way, adding the reason to [notDerived].
     *  [targetHighway]: the highway=* to use if the sign does not require a specific one */
    fun derive(tags: Map<String, String>, targetHighway: String?, notDerived: MutableList<TrafficSignNotDerived>): Derived?
}

/** The supported sign combinations (exactly one main sign with exactly these additional signs) of
 *  the German catalogue. Anything else is only recorded in traffic_sign. */
private val WAY_RULES: Map<String, Map<List<String>, WayRule>> = mapOf(
    "DE" to mapOf(
        listOf("237") to PathRule(PathSign.EXCLUSIVE_CYCLEWAY),
        listOf("239") to PathRule(PathSign.FOOTWAY),
        listOf("239", "1022-10") to PathRule(PathSign.FOOTWAY_BICYCLE_ALLOWED),
        listOf("240") to PathRule(PathSign.SHARED),
        listOf("241-30") to PathRule(PathSign.SEGREGATED),
        listOf("241-31") to PathRule(PathSign.SEGREGATED),
        listOf("250") to AccessBanRule("vehicle", null),
        listOf("250", "1020-30") to AccessBanRule("vehicle", "destination", "1020-30"),
        listOf("260") to AccessBanRule("motor_vehicle", null),
        listOf("260", "1020-30") to AccessBanRule("motor_vehicle", "destination", "1020-30"),
        listOf("260", "1026-36") to AccessBanRule("motor_vehicle", "agricultural", "1026-36"),
    )
)

/** the signs DE:* that are supported by [deriveWayTags], as main sign + additional signs */
val SUPPORTED_WAY_TRAFFIC_SIGNS: Map<String, Set<List<String>>> =
    WAY_RULES.mapValues { it.value.keys }

private fun TrafficSignValue.findRule(notDerived: MutableList<TrafficSignNotDerived>): WayRule? {
    if (isUnparseable) {
        notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.UNPARSEABLE_VALUE))
        return null
    }
    val unknown = parts.filter { !it.isRecognized }
    if (unknown.isNotEmpty()) {
        notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.UNKNOWN_SIGN, unknown.joinToString(",") { it.text }))
        return null
    }
    val invalid = parts.filter { it.isMissingValue || it.hasUnexpectedValue }
    if (invalid.isNotEmpty()) {
        notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.INVALID_VALUE, invalid.joinToString(",") { it.text }))
        return null
    }
    val group = groups.singleOrNull()
    val rule = if (country != null && group?.main != null && parts.none { it.isNamedValue }) {
        WAY_RULES[country]?.get(listOf(group.main.text) + group.modifiers.map { it.text })
    } else {
        null
    }
    if (rule == null) {
        notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.UNSUPPORTED_COMBINATION, toOsmValue()))
    }
    return rule
}

/* ---------------------------------- foot- and cycleways ----------------------------------- */

/** Mirrors the tagging of SeparateCycleway.applyTo (osm/cycleway_separate) for the respective
 *  situation, but does not touch sidewalks and check dates */
private enum class PathSign {
    /** DE:237 - like SeparateCycleway.EXCLUSIVE */
    EXCLUSIVE_CYCLEWAY,
    /** DE:239 - like SeparateCycleway.NON_DESIGNATED_ON_FOOTWAY */
    FOOTWAY,
    /** DE:239,1022-10 - like SeparateCycleway.ALLOWED_ON_FOOTWAY */
    FOOTWAY_BICYCLE_ALLOWED,
    /** DE:240 - like SeparateCycleway.NON_SEGREGATED */
    SHARED,
    /** DE:241 - like SeparateCycleway.SEGREGATED */
    SEGREGATED,
}

private val PATH_HIGHWAYS = setOf("path", "footway", "cycleway")

/** attributes that only make sense on segregated foot- and cycleways */
private val SEGREGATED_PART_ATTRIBUTES = listOf("surface", "smoothness", "width", "est_width", "lit", "colour", "surface:colour")
    .flatMap { listOf("footway:$it", "cycleway:$it") }

private class PathRule(private val sign: PathSign) : WayRule {
    override val isPathRule get() = true

    override val requiredHighway: String? get() = when (sign) {
        PathSign.EXCLUSIVE_CYCLEWAY -> "cycleway"
        PathSign.FOOTWAY, PathSign.FOOTWAY_BICYCLE_ALLOWED -> "footway"
        // a shared or segregated foot- and cycleway may be tagged as path, footway or cycleway
        // (see SeparateCyclewayParser), so it does not require a re-classification
        PathSign.SHARED, PathSign.SEGREGATED -> null
    }

    override fun derive(tags: Map<String, String>, targetHighway: String?, notDerived: MutableList<TrafficSignNotDerived>): Derived? {
        val highway = tags["highway"]
        if (highway == null) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.NOT_A_HIGHWAY))
            return null
        }
        if (highway !in PATH_HIGHWAYS) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.UNSUPPORTED_HIGHWAY, highway))
            return null
        }
        if (tags["area"] == "yes") {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.AREA))
            return null
        }
        val conditional = tags.keys.filter { isConditionalKeyOf(it, setOf("bicycle", "foot", "access")) }
        if (conditional.isNotEmpty()) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.CONDITIONAL_TAGS_PRESENT, conditional.joinToString(",")))
            return null
        }
        val newHighway = requiredHighway ?: targetHighway ?: highway
        // e.g. footway=sidewalk is not valid for highway=cycleway and there is no 1:1 equivalent
        if (newHighway != highway && tags.containsKey(highway)) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.HIGHWAY_SUBTYPE_PRESENT, "$highway=${tags[highway]}"))
            return null
        }

        val t = tags.toMutableMap()
        val resolved = ArrayList<TrafficSignTagConflict>()
        val unresolved = ArrayList<TrafficSignTagConflict>()
        fun resolve(key: String, reason: TrafficSignTagConflict.Reason) {
            resolved.add(TrafficSignTagConflict(key, tags.getValue(key), reason))
        }

        t["highway"] = newHighway
        val bicycle = tags["bicycle"]
        val foot = tags["foot"]
        when (sign) {
            PathSign.EXCLUSIVE_CYCLEWAY -> {
                if (bicycle != null && bicycle != "designated") {
                    if (bicycle in RESTRICTIVE_VALUES) resolve("bicycle", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    t["bicycle"] = "designated"
                }
                if (tags["bicycle:signed"] == "no") t["bicycle:signed"] = "yes"
                if (foot == "designated") {
                    resolve("foot", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    t.remove("foot")
                } else if (foot != null && foot !in RESTRICTIVE_VALUES) {
                    unresolved.add(TrafficSignTagConflict("foot", foot, TrafficSignTagConflict.Reason.FOOT_ON_EXCLUSIVE_CYCLEWAY))
                }
            }
            PathSign.FOOTWAY, PathSign.FOOTWAY_BICYCLE_ALLOWED -> {
                if (foot != null && foot != "designated") {
                    if (foot in RESTRICTIVE_VALUES) resolve("foot", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    t["foot"] = "designated"
                }
                if (sign == PathSign.FOOTWAY_BICYCLE_ALLOWED) {
                    // "Radfahrer frei" allows cycling, but does not designate the way for cyclists
                    if (bicycle != null && (bicycle in RESTRICTIVE_VALUES || bicycle == "designated")) {
                        resolve("bicycle", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    }
                    t["bicycle"] = "yes"
                    // SeparateCyclewayParser only recognizes a footway on which cycling is allowed
                    // by sign if bicycle:signed=yes
                    t["bicycle:signed"] = "yes"
                } else {
                    // without "Radfahrer frei", cycling on a footway is not allowed
                    if (bicycle != null && bicycle in PERMISSIVE_VALUES) {
                        resolve("bicycle", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                        t.remove("bicycle")
                    }
                    t.remove("bicycle:signed")
                }
            }
            PathSign.SHARED, PathSign.SEGREGATED -> {
                if ((newHighway != "cycleway" || bicycle != null) && bicycle != "designated") {
                    if (bicycle != null && bicycle in RESTRICTIVE_VALUES) resolve("bicycle", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    t["bicycle"] = "designated"
                }
                if ((newHighway != "footway" || foot != null) && foot != "designated") {
                    if (foot != null && foot in RESTRICTIVE_VALUES) resolve("foot", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                    t["foot"] = "designated"
                }
                if (tags["bicycle:signed"] == "no") t["bicycle:signed"] = "yes"
            }
        }

        // segregated=* is only applicable to shared foot- and cycleways
        val segregated = tags["segregated"]
        when (sign) {
            PathSign.SHARED -> {
                if (segregated != null && segregated != "no") resolve("segregated", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                t["segregated"] = "no"
            }
            PathSign.SEGREGATED -> {
                if (segregated != null && segregated != "yes") resolve("segregated", TrafficSignTagConflict.Reason.CONTRADICTS_SIGN)
                t["segregated"] = "yes"
            }
            else -> if (segregated != null) {
                resolve("segregated", TrafficSignTagConflict.Reason.SEGREGATION_NOT_APPLICABLE)
                t.remove("segregated")
            }
        }
        // footway:surface etc. are not removed automatically because they may still describe the
        // way correctly in parts, but are reported
        if (sign != PathSign.SEGREGATED) {
            for (key in SEGREGATED_PART_ATTRIBUTES) {
                val value = tags[key] ?: continue
                unresolved.add(TrafficSignTagConflict(key, value, TrafficSignTagConflict.Reason.SEGREGATED_PART_ATTRIBUTE))
            }
        }
        val definesBicycleSigned = sign == PathSign.FOOTWAY || sign == PathSign.FOOTWAY_BICYCLE_ALLOWED ||
            t["bicycle:signed"] != tags["bicycle:signed"]
        val definedKeys = setOf("highway", "segregated") + (if (definesBicycleSigned) setOf("bicycle:signed") else emptySet())
        return Derived(t, resolved, unresolved, definedKeys)
    }
}

/* -------------------------------------- access bans --------------------------------------- */

/** values of access tags that do not allow more than a ban (with any exception) */
private val RESTRICTIVE_VALUES = setOf("no", "private", "dismount", "use_sidepath")
/** values of access tags that clearly allow more than a ban */
private val PERMISSIVE_VALUES = setOf("yes", "designated", "permissive", "destination")

/** Vehicle types below motor_vehicle in the access hierarchy, see
 *  https://wiki.openstreetmap.org/wiki/Key:access#Land-based_transportation
 *  Use categories like agricultural, forestry, psv, taxi or emergency are not included:
 *  They are exceptions that may stem from additional signs, see [ACCESS_USE_CATEGORIES].
 *  small_electric_vehicle is not included either because whether it counts as motor vehicle for a
 *  ban by sign depends on the national law. */
private val MOTOR_VEHICLE_TYPES = setOf(
    "motorcycle", "moped", "speed_pedelec", "mofa", "motorcar", "motorhome", "tourist_bus", "coach",
    "goods", "hgv", "hgv_articulated", "bdouble", "bus", "minibus", "auto_rickshaw", "nev",
    "golf_cart", "microcar", "atv", "ohv", "snowmobile",
)

private val VEHICLE_TYPES = setOf(
    "bicycle", "electric_bicycle", "carriage", "trailer", "caravan", "motor_vehicle",
) + MOTOR_VEHICLE_TYPES

private val ACCESS_USE_CATEGORIES = setOf(
    "agricultural", "forestry", "psv", "taxi", "share_taxi", "hazmat", "disabled", "hov", "carpool",
    "car_sharing",
)

/** A ban for [key] (vehicle or motor_vehicle) with optionally one exception, e.g. DE:250,1020-30
 *  -> vehicle=destination */
private class AccessBanRule(
    private val key: String,
    private val exception: String?,
    /** additional sign that hints at a different highway classification */
    private val classificationHintSign: String? = null,
) : WayRule {
    // getters, because the rules are created before the sets below are initialized
    private val subKeys get() = if (key == "vehicle") VEHICLE_TYPES else MOTOR_VEHICLE_TYPES
    private val parentKeys get() = if (key == "vehicle") listOf("access") else listOf("vehicle", "access")
    private val value get() = exception ?: "no"

    override val isPathRule get() = false
    override val requiredHighway: String? get() = null

    override fun derive(tags: Map<String, String>, targetHighway: String?, notDerived: MutableList<TrafficSignNotDerived>): Derived? {
        val highway = tags["highway"]
        if (highway == null) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.NOT_A_HIGHWAY))
            return null
        }
        if (highway !in ALL_ROADS && highway !in ALL_PATHS) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.UNSUPPORTED_HIGHWAY, highway))
            return null
        }
        // the ban may relate to conditional or lane-dependent access restrictions in ways that can
        // not be derived from the signs alone. (Directional access tags are resolved before, see
        // directionalView)
        val affectedKeys = setOf(key, "access") + parentKeys + subKeys + ACCESS_USE_CATEGORIES
        val conditional = ArrayList<String>()
        val lanes = ArrayList<String>()
        for (k in tags.keys) {
            val base = k.substringBefore(':')
            if (base == k || base !in affectedKeys) continue
            val suffixes = k.substringAfter(':').split(':')
            when {
                "conditional" in suffixes -> conditional.add(k)
                "lanes" in suffixes -> lanes.add(k)
            }
        }
        if (conditional.isNotEmpty()) notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.CONDITIONAL_TAGS_PRESENT, conditional.joinToString(",")))
        if (lanes.isNotEmpty()) notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.LANE_TAGS_PRESENT, lanes.joinToString(",")))
        if (conditional.isNotEmpty() || lanes.isNotEmpty()) return null

        val t = tags.toMutableMap()
        val resolved = ArrayList<TrafficSignTagConflict>()
        val unresolved = ArrayList<TrafficSignTagConflict>()

        val previous = tags[key]
        if (previous != null && previous != value) {
            resolved.add(TrafficSignTagConflict(key, previous, TrafficSignTagConflict.Reason.CONTRADICTS_SIGN))
        }
        t[key] = value

        // more specific vehicle types that are allowed more than the sign allows are removed so
        // that they inherit the ban. More restrictive ones are kept, they may stem from other signs
        val allowed = RESTRICTIVE_VALUES + setOfNotNull(exception)
        val removable = PERMISSIVE_VALUES - allowed
        for (subKey in subKeys) {
            val subValue = tags[subKey] ?: continue
            val values = subValue.split(';').map { it.trim() }
            when {
                values.all { it in allowed } -> {}
                values.all { it in allowed || it in removable } -> {
                    resolved.add(TrafficSignTagConflict(subKey, subValue, TrafficSignTagConflict.Reason.CONTRADICTS_SIGN))
                    t.remove(subKey)
                }
                else -> unresolved.add(TrafficSignTagConflict(subKey, subValue, TrafficSignTagConflict.Reason.UNCLEAR_ACCESS))
            }
        }
        // exceptions for use categories that are not on the signs, e.g. taxi=yes
        for (useKey in ACCESS_USE_CATEGORIES) {
            val useValue = tags[useKey] ?: continue
            if (useKey == exception) continue
            if (useValue.split(';').all { it.trim() in RESTRICTIVE_VALUES }) continue
            unresolved.add(TrafficSignTagConflict(useKey, useValue, TrafficSignTagConflict.Reason.UNCLEAR_ACCESS))
        }
        // e.g. access=no is more restrictive than DE:260, which allows bicycles
        for (parentKey in parentKeys) {
            val parentValue = tags[parentKey] ?: continue
            if (parentValue in RESTRICTIVE_VALUES) {
                unresolved.add(TrafficSignTagConflict(parentKey, parentValue, TrafficSignTagConflict.Reason.MORE_RESTRICTIVE_GENERAL_ACCESS))
            }
        }
        if (key == "vehicle" && highway == "cycleway" && "bicycle" !in tags) {
            unresolved.add(TrafficSignTagConflict("highway", highway, TrafficSignTagConflict.Reason.ACCESS_IMPLIED_BY_HIGHWAY))
        }
        if (classificationHintSign != null) {
            notDerived.add(TrafficSignNotDerived(TrafficSignNotDerived.Reason.HIGHWAY_CLASSIFICATION_HINT, classificationHintSign))
        }
        return Derived(t, resolved, unresolved)
    }
}
