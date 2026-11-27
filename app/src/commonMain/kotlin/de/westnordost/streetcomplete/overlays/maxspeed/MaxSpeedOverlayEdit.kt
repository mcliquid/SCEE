package de.westnordost.streetcomplete.overlays.maxspeed

import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedIssue
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedUnit
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedValue
import de.westnordost.streetcomplete.osm.maxspeed.Speed
import de.westnordost.streetcomplete.osm.maxspeed.interpretMaxSpeed
import de.westnordost.streetcomplete.osm.maxspeed.parseMaxSpeedValue

/** What the overlay form can edit for a way. Only plain numeric limits (km/h or mph) are editable;
 *  everything else is shown read-only and must be changed via the tag editor, so that no
 *  information is lost by a simplified edit. */
sealed interface MaxSpeedEditState {
    /** only `maxspeed` (or nothing) is tagged */
    data class General(val speed: Speed?) : MaxSpeedEditState

    /** `maxspeed:forward` and/or `maxspeed:backward` are tagged, possibly in addition to `maxspeed` */
    data class Directional(val forward: Speed?, val backward: Speed?) : MaxSpeedEditState

    /** something the form cannot represent safely, e.g. `none`, `walk`, `DE:urban`, lanes */
    data class NotEditable(val reason: Reason) : MaxSpeedEditState

    enum class Reason { SPECIAL_VALUE, LANES, UNSUPPORTED_UNIT, TYPE_CONFLICT, IMPLICIT_TYPE }
}

private const val FORWARD = "maxspeed:forward"
private const val BACKWARD = "maxspeed:backward"

fun getMaxSpeedEditState(tags: Map<String, String>): MaxSpeedEditState {
    if (tags.keys.any { it.startsWith("maxspeed:lanes") }) {
        return MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.LANES)
    }
    // which of the contradicting type codes to remove is up to the user, not the form
    if (MaxSpeedIssue.TYPE_CONFLICT in interpretMaxSpeed(tags).issues) {
        return MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.TYPE_CONFLICT)
    }
    // A type code like DE:urban or DE:zone30 says where the current limit comes from. A new number
    // would make it outdated, but the form cannot tell whether there now is a sign, so neither
    // removing the code nor writing `sign` is safe -> tag editor
    if (TYPE_KEYS.any { key -> tags[key]?.let { parseMaxSpeedValue(it) is MaxSpeedValue.Code } == true }) {
        return MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.IMPLICIT_TYPE)
    }
    val general = tags["maxspeed"]?.let { toSpeed(it) ?: return notEditable(it) }
    val forward = tags[FORWARD]?.let { toSpeed(it) ?: return notEditable(it) }
    val backward = tags[BACKWARD]?.let { toSpeed(it) ?: return notEditable(it) }
    return if (FORWARD in tags || BACKWARD in tags) {
        MaxSpeedEditState.Directional(forward ?: general, backward ?: general)
    } else {
        MaxSpeedEditState.General(general)
    }
}

/** Returns the tag changes to get from [tags] to [newState], or null if nothing changes.
 *  Tags that are not touched (conditional, advisory, lanes, ...) are always preserved. */
fun createMaxSpeedTagChanges(tags: Map<String, String>, newState: MaxSpeedEditState): StringMapChanges? {
    val oldState = getMaxSpeedEditState(tags)
    if (oldState == newState) return null
    val builder = StringMapChangesBuilder(tags)
    when (newState) {
        is MaxSpeedEditState.General -> {
            require(oldState is MaxSpeedEditState.General) { "cannot change way to general limit" }
            val speed = requireNotNull(newState.speed?.takeIf { it.isComplete() })
            builder["maxspeed"] = speed.toOsmString()
        }
        is MaxSpeedEditState.Directional -> {
            require(oldState is MaxSpeedEditState.Directional) { "cannot change way to directional limits" }
            setDirection(builder, tags, FORWARD, BACKWARD, oldState.forward, newState.forward)
            setDirection(builder, tags, BACKWARD, FORWARD, oldState.backward, newState.backward)
        }
        is MaxSpeedEditState.NotEditable -> throw IllegalArgumentException()
    }
    return if (builder.hasChanges) builder.create() else null
}

private fun setDirection(
    builder: StringMapChangesBuilder,
    tags: Map<String, String>,
    key: String,
    otherKey: String,
    old: Speed?,
    new: Speed?,
) {
    if (old == new) return
    val speed = requireNotNull(new?.takeIf { it.isComplete() })
    // the value was taken from the general maxspeed, which is only used for this direction because
    // the other one has its own override -> edit the general value
    val target = if (key !in tags && "maxspeed" in tags && otherKey in tags) "maxspeed" else key
    builder[target] = speed.toOsmString()
}

/** keys that describe the type of the current limit. Values that are no code (`sign`, free text,
 *  URLs) do not block editing and are kept. Not `zone:maxspeed` and `zone:traffic`: they describe
 *  the zone the way belongs to, which stays valid with a different (e.g. lower) local limit */
private val TYPE_KEYS = listOf("maxspeed:type", "source:maxspeed")

/** Tags shown in the form: everything that concerns the speed limit, incl. type and zone */
fun isMaxSpeedRelatedKey(key: String): Boolean =
    key == "maxspeed" || key.startsWith("maxspeed:") || key.startsWith("source:maxspeed") ||
        key == "zone:maxspeed" || key == "zone:traffic"

/** What the form shows about the tags: exactly the [changes] that will be saved, and all other
 *  speed limit related tags, which stay as they are */
data class MaxSpeedTagPreview(
    val changes: List<StringMapEntryChange>,
    val unchanged: List<Pair<String, String>>,
)

fun createMaxSpeedTagPreview(tags: Map<String, String>, changes: StringMapChanges?): MaxSpeedTagPreview {
    val changeList = changes?.changes.orEmpty().sortedBy { it.key }
    val changedKeys = changeList.mapTo(HashSet()) { it.key }
    val unchanged = tags.entries
        .filter { isMaxSpeedRelatedKey(it.key) && it.key !in changedKeys }
        .sortedBy { it.key }
        .map { it.key to it.value }
    return MaxSpeedTagPreview(changeList, unchanged)
}

private fun notEditable(value: String): MaxSpeedEditState.NotEditable =
    MaxSpeedEditState.NotEditable(
        if (parseMaxSpeedValue(value) is MaxSpeedValue.Numeric) MaxSpeedEditState.Reason.UNSUPPORTED_UNIT
        else MaxSpeedEditState.Reason.SPECIAL_VALUE
    )

private fun toSpeed(value: String): Speed? {
    val numeric = parseMaxSpeedValue(value) as? MaxSpeedValue.Numeric ?: return null
    val unit = when (numeric.unit) {
        MaxSpeedUnit.KMH -> SpeedMeasurementUnit.KILOMETERS_PER_HOUR
        MaxSpeedUnit.MPH -> SpeedMeasurementUnit.MILES_PER_HOUR
        MaxSpeedUnit.KNOTS -> return null
    }
    return Speed(numeric.value, unit)
}

/** Common speed limits offered as quick selection */
fun commonSpeedLimits(unit: SpeedMeasurementUnit): List<Int> = when (unit) {
    SpeedMeasurementUnit.KILOMETERS_PER_HOUR -> listOf(5, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 120, 130)
    SpeedMeasurementUnit.MILES_PER_HOUR -> listOf(5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70)
}
