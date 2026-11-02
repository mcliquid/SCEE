package de.westnordost.streetcomplete.overlays.street_parking

import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.traffic_calming.parseNarrowingTrafficCalming
import de.westnordost.streetcomplete.quests.parking_type.ParkingType
import de.westnordost.streetcomplete.resources.*
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

/** A parking node created with the crosshair is only tagged as such; everything else (type,
 *  access, fee…) is asked afterwards by the parking quests */
fun createParkingNodeAction(position: LatLon): CreateNodeAction =
    CreateNodeAction(position, mapOf("amenity" to "parking"))

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
enum class NewStreetParkingFeature { PARKING, TRAFFIC_CALMING }

val NewStreetParkingFeature.icon: DrawableResource get() = when (this) {
    NewStreetParkingFeature.PARKING -> Res.drawable.quest_parking
    NewStreetParkingFeature.TRAFFIC_CALMING -> Res.drawable.quest_choker
}

val NewStreetParkingFeature.title: StringResource get() = when (this) {
    NewStreetParkingFeature.PARKING -> Res.string.street_parking_create_parking
    NewStreetParkingFeature.TRAFFIC_CALMING -> Res.string.street_parking_create_traffic_calming
}
