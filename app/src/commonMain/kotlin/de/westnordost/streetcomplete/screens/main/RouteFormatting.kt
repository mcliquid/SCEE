package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.util.locale.NumberFormatter
import kotlin.math.roundToInt

fun routeMinutes(seconds: Double): Int =
    if (seconds > 0) (seconds / 60).roundToInt().coerceAtLeast(1) else 0

fun routeDistance(meters: Double): String = if (meters >= 1000) {
    NumberFormatter(minFractionDigits = 1, maxFractionDigits = 1).format(meters / 1000) + " km"
} else {
    NumberFormatter(maxFractionDigits = 0).format(meters.roundToInt()) + " m"
}
