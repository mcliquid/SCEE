package de.westnordost.streetcomplete.overlays.oneway

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.oneway.OnewayIssue
import de.westnordost.streetcomplete.osm.oneway.TravelDirection
import de.westnordost.streetcomplete.osm.oneway.interpretOneway
import de.westnordost.streetcomplete.osm.oneway.parseOnewayValue

/** What the overlay form edits: only `oneway` and `oneway:bicycle`. All other tags are kept.
 *  [general] null = `oneway` not tagged, [bicycle] null = `oneway:bicycle` not tagged (bicycles
 *  follow the general restriction). */
data class OnewayEditState(
    val general: TravelDirection?,
    val bicycle: TravelDirection?,
)

enum class OnewayNotEditableReason {
    /** `oneway` or `oneway:bicycle` has an undocumented value, e.g. `reversible` */
    UNKNOWN_VALUE,
    /** `oneway:vehicle` / `oneway:motor_vehicle` are tagged: changing `oneway` would not do what
     *  the simple form suggests */
    VEHICLE_SPECIFIC,
}

fun getOnewayNotEditableReason(tags: Map<String, String>): OnewayNotEditableReason? = when {
    listOf("oneway", "oneway:bicycle").any { parseOnewayValue(tags[it]) == TravelDirection.UNKNOWN } ->
        OnewayNotEditableReason.UNKNOWN_VALUE
    "oneway:vehicle" in tags || "oneway:motor_vehicle" in tags ->
        OnewayNotEditableReason.VEHICLE_SPECIFIC
    else -> null
}

fun getOnewayEditState(tags: Map<String, String>): OnewayEditState =
    OnewayEditState(parseOnewayValue(tags["oneway"]), parseOnewayValue(tags["oneway:bicycle"]))

/** Returns the tag changes to get from [tags] to [newState], or null if nothing changes */
fun createOnewayTagChanges(tags: Map<String, String>, newState: OnewayEditState): StringMapChanges? {
    require(getOnewayNotEditableReason(tags) == null) { "oneway tagging of this way is not editable" }
    require(newState.general != TravelDirection.UNKNOWN && newState.bicycle != TravelDirection.UNKNOWN)
    val oldState = getOnewayEditState(tags)
    val builder = StringMapChangesBuilder(tags)
    if (newState.general != oldState.general) {
        // the form never offers to remove `oneway`
        builder["oneway"] = requireNotNull(newState.general).toOsmValue()
    }
    if (newState.bicycle != oldState.bicycle) {
        val bicycle = newState.bicycle
        if (bicycle == null) builder.remove("oneway:bicycle") else builder["oneway:bicycle"] = bicycle.toOsmValue()
    }
    return if (builder.hasChanges) builder.create() else null
}

/** Whether applying [changes] would create a contradiction that did not exist before (e.g.
 *  `oneway:bicycle=yes` while a contraflow cycle lane is tagged). Such edits are not offered,
 *  the conflicting tags must be resolved in the tag editor. */
fun createsOnewayConflict(tags: Map<String, String>, changes: StringMapChanges): Boolean {
    val newTags = tags.toMutableMap().also { changes.applyTo(it) }
    val before = OnewayIssue.CONFLICT in interpretOneway(tags).issues
    val after = OnewayIssue.CONFLICT in interpretOneway(newTags).issues
    return after && !before
}

private fun TravelDirection.toOsmValue(): String = when (this) {
    TravelDirection.FORWARD -> "yes"
    TravelDirection.BACKWARD -> "-1"
    TravelDirection.BOTH -> "no"
    TravelDirection.UNKNOWN -> throw IllegalArgumentException()
}

/** Tags that are related to oneways / bicycles and are kept unchanged by the form */
fun getRelatedOnewayTags(tags: Map<String, String>): List<Pair<String, String>> =
    tags.filterKeys { key ->
        (key.startsWith("oneway") && key != "oneway" && key != "oneway:bicycle") ||
        key.startsWith("cycleway") || key.startsWith("bicycle") ||
        key == "junction" || key == "access" || key == "vehicle"
    }.toList().sortedBy { it.first }
