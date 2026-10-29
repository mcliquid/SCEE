package de.westnordost.streetcomplete.overlays.maxspeed

import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedUnit
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedValue
import de.westnordost.streetcomplete.osm.maxspeed.Speed
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

    enum class Reason { SPECIAL_VALUE, LANES, UNSUPPORTED_UNIT }
}

private const val FORWARD = "maxspeed:forward"
private const val BACKWARD = "maxspeed:backward"

fun getMaxSpeedEditState(tags: Map<String, String>): MaxSpeedEditState {
    if (tags.keys.any { it.startsWith("maxspeed:lanes") }) {
        return MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.LANES)
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
            removeOutdatedImplicitType(builder, tags)
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
    if (target == "maxspeed") removeOutdatedImplicitType(builder, tags)
}

/** A changed numeric limit makes an implicit type code (e.g. `DE:urban`, `DE:zone30`) outdated */
private fun removeOutdatedImplicitType(builder: StringMapChangesBuilder, tags: Map<String, String>) {
    for (key in OUTDATED_TYPE_KEYS) {
        val value = tags[key] ?: continue
        if (parseMaxSpeedValue(value) is MaxSpeedValue.Code) builder.remove(key)
    }
}

/** type keys that become outdated when the limit is changed */
fun getOutdatedTypeTags(tags: Map<String, String>): Map<String, String> =
    tags.filter { (key, value) -> key in OUTDATED_TYPE_KEYS && parseMaxSpeedValue(value) is MaxSpeedValue.Code }

private val OUTDATED_TYPE_KEYS = listOf("maxspeed:type", "source:maxspeed", "zone:maxspeed")

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
