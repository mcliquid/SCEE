package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.ktx.systemTimeNow
import de.westnordost.streetcomplete.util.ktx.toLocalDate
import kotlinx.datetime.Month

fun isWinter(location: LatLon?): Boolean {
    if (location == null) return false
    val now = systemTimeNow().toLocalDate()
    val winterSeason: List<Month> = if (location.latitude > 0)
        listOf(
            Month.NOVEMBER,
            Month.DECEMBER,
            Month.JANUARY,
            Month.FEBRUARY,
            Month.MARCH,
            Month.APRIL
        )
    else
        listOf(
            Month.JUNE,
            Month.JULY,
            Month.AUGUST,
            Month.SEPTEMBER,
            Month.OCTOBER
        )
    return now.month in winterSeason
}
