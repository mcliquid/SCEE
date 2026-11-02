package de.westnordost.streetcomplete.overlays.street_parking

import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.traffic_calming.parseNarrowingTrafficCalming
import de.westnordost.streetcomplete.quests.parking_type.ParkingType
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.osm.traffic_calming.LaneNarrowingTrafficCalming
import de.westnordost.streetcomplete.osm.traffic_calming.icon
import de.westnordost.streetcomplete.osm.traffic_calming.title
import de.westnordost.streetcomplete.quests.parking_type.icon
import de.westnordost.streetcomplete.quests.parking_type.title
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** Which form the street parking overlay shows for an element (null = create a new node) */
enum class StreetParkingFormType { ROAD, PARKING_LOT, TRAFFIC_CALMING, CREATE }

fun getStreetParkingFormType(element: Element?): StreetParkingFormType? = when {
    element == null -> StreetParkingFormType.CREATE
    element.tags["highway"] in ALL_ROADS && element.tags["area"] != "yes" -> StreetParkingFormType.ROAD
    element.tags["amenity"] == "parking" -> StreetParkingFormType.PARKING_LOT
    parseNarrowingTrafficCalming(element.tags) != null -> StreetParkingFormType.TRAFFIC_CALMING
    else -> null
}

/** Quests (by name) offered in the parking lot form. Whether each one actually applies is decided by
 *  the quest itself, so e.g. capacity is only offered for public surface parking. */
val PARKING_LOT_QUEST_TYPE_NAMES = listOf(
    "AddParkingType",
    "AddParkingAccess",
    "AddParkingFee",
    "AddParkingCharge",
    "AddParkingCapacity",
    "AddDisabledParkingCapacity",
    "AddParkingOrientation",
    "AddOpeningHours",
)

/** Parking types that may be created as a free-standing node. Street-side and lane parking belong
 *  to the road (parking:* tags) or are mapped as areas along it, so they are not offered. */
val CREATABLE_PARKING_TYPES = listOf(ParkingType.SURFACE, ParkingType.MULTI_STOREY, ParkingType.UNDERGROUND)

fun createParkingNodeAction(position: LatLon, type: ParkingType): CreateNodeAction {
    require(type in CREATABLE_PARKING_TYPES)
    return CreateNodeAction(position, mapOf("amenity" to "parking", "parking" to type.osmValue))
}

/** Tags shown in the parking lot summary, in display order. Only tags that are set are shown. */
val PARKING_LOT_SUMMARY_KEYS: List<Pair<String, StringResource>> = listOf(
    "access" to Res.string.parking_lot_access,
    "fee" to Res.string.parking_lot_fee,
    "charge" to Res.string.parking_lot_charge,
    "capacity" to Res.string.parking_lot_capacity,
    "capacity:disabled" to Res.string.parking_lot_capacity_disabled,
    "maxstay" to Res.string.parking_lot_maxstay,
    "orientation" to Res.string.parking_lot_orientation,
    "opening_hours" to Res.string.parking_lot_opening_hours,
)

fun getParkingLotSummary(tags: Map<String, String>): List<Pair<StringResource, String>> =
    PARKING_LOT_SUMMARY_KEYS.mapNotNull { (key, label) -> tags[key]?.let { label to it } }

fun getParkingType(tags: Map<String, String>): ParkingType? =
    ParkingType.entries.find { it.osmValue == tags["parking"] }

/** What can be created with the crosshair in the street parking overlay */
@Serializable
sealed interface NewStreetParkingFeature {
    @Serializable @SerialName("traffic_calming")
    data class TrafficCalming(val value: LaneNarrowingTrafficCalming) : NewStreetParkingFeature
    @Serializable @SerialName("parking")
    data class Parking(val parkingType: ParkingType) : NewStreetParkingFeature

    companion object {
        val all: List<NewStreetParkingFeature> =
            CREATABLE_PARKING_TYPES.map { Parking(it) } +
            LaneNarrowingTrafficCalming.entries.map { TrafficCalming(it) }

        /** traffic calming needs to be placed on a road, parking lots don't */
        fun selectable(isOnRoad: Boolean): List<NewStreetParkingFeature> =
            if (isOnRoad) all else all.filterIsInstance<Parking>()
    }
}

val NewStreetParkingFeature.icon: DrawableResource get() = when (this) {
    is NewStreetParkingFeature.Parking -> parkingType.icon
    is NewStreetParkingFeature.TrafficCalming -> value.icon
}

val NewStreetParkingFeature.title: StringResource get() = when (this) {
    is NewStreetParkingFeature.Parking -> parkingType.title
    is NewStreetParkingFeature.TrafficCalming -> value.title
}
