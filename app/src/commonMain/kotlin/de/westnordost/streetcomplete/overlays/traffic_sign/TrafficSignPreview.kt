package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWayTagging
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/* The consequences of the signs, in words. No tag keys or values are ever shown: the model below
 * does not even contain them. */

/** What a tag stands for, in words */
enum class PreviewSubject {
    BICYCLE, FOOT, MOTOR, VEHICLE, ACCESS, SEGREGATION, PART_DETAILS, SIGN_ENTRY, WAY_TYPE, OTHER
}

private val MOTOR_KEYS = setOf(
    "motor_vehicle", "motorcar", "motorcycle", "moped", "mofa", "hgv", "bus", "psv", "taxi",
    "agricultural", "forestry", "goods", "hazmat", "carpool", "tourist_bus", "speed_pedelec",
    "atv", "snowmobile", "trailer", "caravan",
)

fun previewSubjectOf(key: String): PreviewSubject {
    val base = key.substringBefore(':')
    return when {
        base == "bicycle" -> PreviewSubject.BICYCLE
        base == "foot" -> PreviewSubject.FOOT
        base == "vehicle" -> PreviewSubject.VEHICLE
        base == "access" -> PreviewSubject.ACCESS
        base in MOTOR_KEYS -> PreviewSubject.MOTOR
        base == "segregated" -> PreviewSubject.SEGREGATION
        base == "footway" || base == "cycleway" -> PreviewSubject.PART_DETAILS
        base == "traffic_sign" -> PreviewSubject.SIGN_ENTRY
        base == "highway" -> PreviewSubject.WAY_TYPE
        else -> PreviewSubject.OTHER
    }
}

data class PreviewConflict(
    val subject: PreviewSubject,
    val reason: TrafficSignTagConflict.Reason,
    val direction: TrafficSignDirection?,
)

data class PreviewNotDerived(
    val reason: TrafficSignNotDerived.Reason,
    val direction: TrafficSignDirection?,
)

data class TrafficSignPreviewModel(
    val derivation: TrafficSignDerivation,
    /** old and new way type as plain highway values; only mapped to words when shown */
    val highwayChange: Pair<String, String>?,
    /** changes made to existing tags to make them fit the signs */
    val adjusted: List<PreviewConflict>,
    /** existing tags that may contradict the signs and are left alone: cannot be resolved automatically */
    val needsCheck: List<PreviewConflict>,
    val notDerived: List<PreviewNotDerived>,
)

/** whether there is anything to tell about the consequences of the signs */
val TrafficSignPreviewModel.hasContent: Boolean get() =
    highwayChange != null || adjusted.isNotEmpty() || needsCheck.isNotEmpty() || notDerived.isNotEmpty()

fun TrafficSignWayTagging.toPreviewModel() = TrafficSignPreviewModel(
    derivation = derivation,
    highwayChange = highwayChange,
    adjusted = resolvedConflicts.map { PreviewConflict(previewSubjectOf(it.key), it.reason, it.direction) }.distinct(),
    needsCheck = unresolvedConflicts.map { PreviewConflict(previewSubjectOf(it.key), it.reason, it.direction) }.distinct(),
    notDerived = notDerived.map { PreviewNotDerived(it.reason, it.direction) }.distinct(),
)

@Composable
fun TrafficSignPreview(model: TrafficSignPreviewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        model.highwayChange?.let { (old, new) ->
            Text(
                stringResource(
                    Res.string.traffic_sign_overlay_way_type_change,
                    stringResource(wayTypeName(old)),
                    stringResource(wayTypeName(new)),
                ),
                style = MaterialTheme.typography.body2,
            )
        }
        if (model.adjusted.isNotEmpty()) {
            Section(Res.string.traffic_sign_overlay_header_adjusted) {
                for (c in model.adjusted) Line(conflictText(c))
            }
        }
        if (model.needsCheck.isNotEmpty()) {
            Section(Res.string.traffic_sign_overlay_header_check, warning = true) {
                for (c in model.needsCheck) Line(conflictText(c))
            }
        }
        if (model.notDerived.isNotEmpty()) {
            Section(Res.string.traffic_sign_overlay_header_not_derived) {
                for (n in model.notDerived) Line(withDirection(stringResource(n.reason.text), n.direction))
            }
        }
    }
}

@Composable
private fun Section(header: StringResource, warning: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(header),
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Bold,
            color = if (warning) MaterialTheme.colors.error else MaterialTheme.colors.onSurface,
        )
        content()
    }
}

@Composable
private fun Line(text: String) {
    Text("• $text", style = MaterialTheme.typography.body2)
}

@Composable
private fun conflictText(c: PreviewConflict): String =
    withDirection(
        stringResource(
            Res.string.traffic_sign_overlay_subject_reason,
            stringResource(c.subject.text),
            stringResource(c.reason.text),
        ),
        c.direction
    )

@Composable
private fun withDirection(text: String, direction: TrafficSignDirection?): String =
    if (direction == null) text
    else stringResource(Res.string.traffic_sign_overlay_line_with_direction, text, stringResource(direction.label))

val TrafficSignDirection.label: StringResource get() = when (this) {
    TrafficSignDirection.FORWARD -> Res.string.traffic_sign_overlay_direction_forward
    TrafficSignDirection.BACKWARD -> Res.string.traffic_sign_overlay_direction_backward
}

internal fun wayTypeName(highway: String): StringResource = when (highway) {
    "path" -> Res.string.traffic_sign_overlay_highway_path
    "footway" -> Res.string.traffic_sign_overlay_highway_footway
    "cycleway" -> Res.string.traffic_sign_overlay_highway_cycleway
    else -> Res.string.traffic_sign_overlay_highway_other
}

private val PreviewSubject.text: StringResource get() = when (this) {
    PreviewSubject.BICYCLE -> Res.string.traffic_sign_overlay_subject_bicycle
    PreviewSubject.FOOT -> Res.string.traffic_sign_overlay_subject_foot
    PreviewSubject.MOTOR -> Res.string.traffic_sign_overlay_subject_motor
    PreviewSubject.VEHICLE -> Res.string.traffic_sign_overlay_subject_vehicle
    PreviewSubject.ACCESS -> Res.string.traffic_sign_overlay_subject_access
    PreviewSubject.SEGREGATION -> Res.string.traffic_sign_overlay_subject_segregation
    PreviewSubject.PART_DETAILS -> Res.string.traffic_sign_overlay_subject_part_details
    PreviewSubject.SIGN_ENTRY -> Res.string.traffic_sign_overlay_subject_sign_entry
    PreviewSubject.WAY_TYPE -> Res.string.traffic_sign_overlay_subject_way_type
    PreviewSubject.OTHER -> Res.string.traffic_sign_overlay_subject_other
}

private val TrafficSignTagConflict.Reason.text: StringResource get() = when (this) {
    TrafficSignTagConflict.Reason.CONTRADICTS_SIGN -> Res.string.traffic_sign_overlay_conflict_contradicts_sign
    TrafficSignTagConflict.Reason.SEGREGATION_NOT_APPLICABLE -> Res.string.traffic_sign_overlay_conflict_segregation_not_applicable
    TrafficSignTagConflict.Reason.SEGREGATED_PART_ATTRIBUTE -> Res.string.traffic_sign_overlay_conflict_segregated_part_attribute
    TrafficSignTagConflict.Reason.FOOT_ON_EXCLUSIVE_CYCLEWAY -> Res.string.traffic_sign_overlay_conflict_foot_on_exclusive_cycleway
    TrafficSignTagConflict.Reason.MORE_RESTRICTIVE_GENERAL_ACCESS -> Res.string.traffic_sign_overlay_conflict_more_restrictive_general_access
    TrafficSignTagConflict.Reason.UNCLEAR_ACCESS -> Res.string.traffic_sign_overlay_conflict_unclear_access
    TrafficSignTagConflict.Reason.ACCESS_IMPLIED_BY_HIGHWAY -> Res.string.traffic_sign_overlay_conflict_access_implied_by_highway
    TrafficSignTagConflict.Reason.TRAFFIC_SIGN_DIRECTION_CHANGED -> Res.string.traffic_sign_overlay_conflict_traffic_sign_direction_changed
    TrafficSignTagConflict.Reason.DIFFERS_BY_DIRECTION -> Res.string.traffic_sign_overlay_conflict_differs_by_direction
}

private val TrafficSignNotDerived.Reason.text: StringResource get() = when (this) {
    TrafficSignNotDerived.Reason.UNPARSEABLE_VALUE -> Res.string.traffic_sign_overlay_not_derived_unparseable_value
    TrafficSignNotDerived.Reason.UNKNOWN_SIGN -> Res.string.traffic_sign_overlay_not_derived_unknown_sign
    TrafficSignNotDerived.Reason.INVALID_VALUE -> Res.string.traffic_sign_overlay_not_derived_invalid_value
    TrafficSignNotDerived.Reason.UNSUPPORTED_COMBINATION -> Res.string.traffic_sign_overlay_not_derived_unsupported_combination
    TrafficSignNotDerived.Reason.NOT_A_HIGHWAY -> Res.string.traffic_sign_overlay_not_derived_not_a_highway
    TrafficSignNotDerived.Reason.UNSUPPORTED_HIGHWAY -> Res.string.traffic_sign_overlay_not_derived_unsupported_highway
    TrafficSignNotDerived.Reason.AREA -> Res.string.traffic_sign_overlay_not_derived_area
    TrafficSignNotDerived.Reason.HIGHWAY_SUBTYPE_PRESENT -> Res.string.traffic_sign_overlay_not_derived_highway_subtype_present
    TrafficSignNotDerived.Reason.CONDITIONAL_TAGS_PRESENT -> Res.string.traffic_sign_overlay_not_derived_conditional_tags_present
    TrafficSignNotDerived.Reason.LANE_TAGS_PRESENT -> Res.string.traffic_sign_overlay_not_derived_lane_tags_present
    TrafficSignNotDerived.Reason.HIGHWAY_CLASSIFICATION_HINT -> Res.string.traffic_sign_overlay_not_derived_highway_classification_hint
    TrafficSignNotDerived.Reason.CONFLICTING_HIGHWAY_CLASSIFICATION -> Res.string.traffic_sign_overlay_not_derived_conflicting_highway_classification
    TrafficSignNotDerived.Reason.HIGHWAY_CHANGE_NOT_IN_BOTH_DIRECTIONS -> Res.string.traffic_sign_overlay_not_derived_highway_change_not_in_both_directions
    TrafficSignNotDerived.Reason.ZONE_NOT_IN_BOTH_DIRECTIONS -> Res.string.traffic_sign_overlay_not_derived_zone_not_in_both_directions
}
