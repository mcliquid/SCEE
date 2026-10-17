package de.westnordost.streetcomplete.osm.traffic_sign

/** Which access tags hint at a traffic sign. Used both by the default element filter of the
 *  AddTrafficSignType quest (the regexes are written into the filter) and by the warning when
 *  "no signs" is about to be saved for a way that has such tags, so there is only one definition.
 *
 *  Not every access tag has a sign behind it and an access tag is never proof of one: this is only
 *  a hint, nothing is derived from it and no access tag is ever changed because of it.
 *
 *  What counts:
 *  - restrictions and special permissions: [SPECIAL_VALUES] on any of [KEYS]
 *    (also as list, "agricultural;forestry", and directional, e.g. "motor_vehicle:forward")
 *  - "yes" only for the special road users [YES_KEYS]: bus=yes, psv=yes, taxi=yes and hgv=yes are
 *    usually exceptions of a restriction
 *  What does not count: access=yes, vehicle/motor_vehicle/motorcar/motorcycle=yes, foot=yes,
 *  bicycle=yes (default values or redundant tags), *:conditional, *:lanes, other values like
 *  permissive, customers, permit, dismount, use_sidepath, discouraged (open decision). */
object TrafficSignAccess {
    /** access keys that may hint at a traffic sign. All except agricultural and forestry are in
     *  the access keys of the access manager (osm/AccessManagerDialog.kt, accessKeys); these two
     *  are tagged as keys for tractors and forestry vehicles, but are not offered there */
    val KEYS = listOf(
        "access", "vehicle", "motor_vehicle", "motorcar", "motorcycle", "bicycle", "foot",
        "hgv", "bus", "psv", "taxi", "agricultural", "forestry",
    )

    val SPECIAL_VALUES = listOf(
        "destination", "delivery", "agricultural", "forestry", "designated", "official", "no", "private",
    )

    val YES_KEYS = listOf("bus", "psv", "taxi", "hgv")

    private fun keyPattern(keys: List<String>) = "^(${keys.joinToString("|")})(:(forward|backward))?\$"
    private fun valuePattern(values: List<String>) = "(.*;)?(${values.joinToString("|")})(;.*)?"

    /** regex of the keys (with optional :forward / :backward), value regex of special values */
    val SPECIAL_KEY_PATTERN = keyPattern(KEYS)
    val SPECIAL_VALUE_PATTERN = valuePattern(SPECIAL_VALUES)
    val YES_KEY_PATTERN = keyPattern(YES_KEYS)
    val YES_VALUE_PATTERN = valuePattern(listOf("yes"))

    private val specialKey = Regex(SPECIAL_KEY_PATTERN)
    private val specialValue = Regex(SPECIAL_VALUE_PATTERN)
    private val yesKey = Regex(YES_KEY_PATTERN)
    private val yesValue = Regex(YES_VALUE_PATTERN)

    /** The part of an element filter that is true if the element has any tag that hints at a sign */
    val ELEMENT_FILTER_FRAGMENT =
        "~\"$SPECIAL_KEY_PATTERN\" ~ \"$SPECIAL_VALUE_PATTERN\" or ~\"$YES_KEY_PATTERN\" ~ \"$YES_VALUE_PATTERN\""

    fun isRelevant(key: String, value: String): Boolean =
        specialKey.matches(key) && specialValue.matches(value) ||
        yesKey.matches(key) && yesValue.matches(value)

    /** whether any access tag hints at a sign */
    fun hasRelevantTags(tags: Map<String, String>): Boolean =
        tags.any { (k, v) -> isRelevant(k, v) }

    /** The tags that hint at a sign and apply to at least one of the given [directions]: the general
     *  ones and the ones of these directions, but not the ones that are only for another direction */
    fun relevantTags(tags: Map<String, String>, directions: Collection<TrafficSignDirection>): Map<String, String> {
        val otherSuffixes = TrafficSignDirection.entries.filter { it !in directions }.map { ":${it.osmSuffix}" }
        return tags.filter { (k, v) -> isRelevant(k, v) && otherSuffixes.none { k.endsWith(it) } }
            .toSortedMap()
    }
}
