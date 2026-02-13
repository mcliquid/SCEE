package de.westnordost.streetcomplete.quests.parking_orientation

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.DIAGONAL
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.PARALLEL
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.PERPENDICULAR
import de.westnordost.streetcomplete.util.ktx.asBitmapDrawable
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.DIAGONAL as DISPLAY_DIAGONAL
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.PARALLEL as DISPLAY_PARALLEL
import de.westnordost.streetcomplete.osm.street_parking.ParkingOrientation.PERPENDICULAR as DISPLAY_PERPENDICULAR
import de.westnordost.streetcomplete.osm.street_parking.ParkingPosition.HALF_ON_STREET as DISPLAY_HALF_ON_STREET
import de.westnordost.streetcomplete.osm.street_parking.ParkingPosition.OFF_STREET as DISPLAY_OFF_STREET
import de.westnordost.streetcomplete.osm.street_parking.ParkingPosition.ON_STREET as DISPLAY_ON_STREET
import de.westnordost.streetcomplete.osm.street_parking.ParkingPosition.STREET_SIDE as DISPLAY_STREET_SIDE
/*
val ParkingOrientation.getIcon(context, parkingTagValue: String?): Int get() {
    val orientationDisplayVal = when (this) {
        PARALLEL -> DISPLAY_PARALLEL
        DIAGONAL -> DISPLAY_DIAGONAL
        PERPENDICULAR -> DISPLAY_PERPENDICULAR
    }
    val positionDisplayVal = when (parkingTagValue) {
        "street_side" -> DISPLAY_STREET_SIDE
        "on_kerb" -> DISPLAY_OFF_STREET
        "half_on_kerb" -> DISPLAY_HALF_ON_STREET
        else -> DISPLAY_ON_STREET
    }
    return DrawableImage(StreetParkingDrawable(context, orientationDisplayVal, positionDisplayVal, false, 128, 128, R.drawable.ic_car1).asBitmapDrawable(context.resources))
}*/
val ParkingOrientation.title: Int get() = when (this) {
        PARALLEL ->      R.string.street_parking_parallel
        DIAGONAL ->      R.string.street_parking_diagonal
        PERPENDICULAR -> R.string.street_parking_perpendicular
    }

val ParkingOrientation.osmValue get() = when (this) {
    PARALLEL ->      "parallel"
    DIAGONAL ->      "diagonal"
    PERPENDICULAR -> "perpendicular"
}

