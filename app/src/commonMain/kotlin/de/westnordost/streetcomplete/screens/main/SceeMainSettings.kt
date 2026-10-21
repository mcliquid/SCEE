package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.edithistory.EditKey
import de.westnordost.streetcomplete.data.preferences.Preferences
import org.maplibre.compose.location.LocationRequest
import org.maplibre.compose.map.MapUiOptions
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

/** SCEE: the "show solved animation" setting */
fun shouldShowSolvedAnimation(prefs: Preferences): Boolean =
    prefs.getBoolean(Prefs.SHOW_SOLVED_ANIMATION, true)

/** SCEE: with "disable navigation mode", it cannot be entered, but an active one can still be left */
fun canToggleNavigationMode(prefs: Preferences, isNavigationMode: Boolean): Boolean =
    !prefs.getBoolean(Prefs.DISABLE_NAVIGATION_MODE, false) || isNavigationMode

/** SCEE: location request for the main map, null (= provider default) if no interval is set */
fun mainLocationRequest(prefs: Preferences): LocationRequest? =
    if (prefs.contains(Prefs.LOCATION_INTERVAL))
        LocationRequest { minimumInterval = prefs.getInt(Prefs.LOCATION_INTERVAL, 0).seconds }
    else null

/**
 * SCEE: [LocationRequest.minimumInterval] is only a hint to the platform ("preferred", may be ignored,
 * and other location subscribers of the app or other apps can make the system deliver faster).
 * So the interval setting is also enforced on the received updates: an update measured less than
 * [interval] after the last accepted one is dropped. Based on measurement time, not arrival time.
 */
class LocationUpdateThrottle(private val interval: Duration) {
    private var last: TimeMark? = null

    fun accept(measurementMark: TimeMark): Boolean {
        val previous = last
        if (previous != null && interval.isPositive() &&
            previous.elapsedNow() - measurementMark.elapsedNow() < interval
        ) return false
        last = measurementMark
        return true
    }

    /** e.g. after the location became unavailable, so that the next update is shown immediately */
    fun reset() { last = null }
}

/** No interval setting (or 0) means no throttling */
fun mainLocationUpdateThrottle(prefs: Preferences): LocationUpdateThrottle =
    LocationUpdateThrottle(
        if (prefs.contains(Prefs.LOCATION_INTERVAL)) prefs.getInt(Prefs.LOCATION_INTERVAL, 0).seconds
        else Duration.ZERO
    )

/** SCEE: the edit history opens with the newest edit selected, or without selection if "select first edit" is off */
fun editHistorySelection(newest: EditKey?, selectNewest: Boolean): MainSheetSelection.EditHistory? =
    if (newest == null) null else MainSheetSelection.EditHistory(if (selectNewest) newest else null)

/** SCEE: rotate gesture settings. Defaults are those of SCEE before the Compose map: no rotating while zooming, 1.5 degrees. */
data class RotateGestureSettings(val allowDuringZoom: Boolean, val startAngle: Double)

private const val DEFAULT_ROTATE_ANGLE_THRESHOLD = 1.5f

fun rotateGestureSettings(prefs: Preferences): RotateGestureSettings {
    val threshold = prefs.getFloat(Prefs.ROTATE_ANGLE_THRESHOLD, DEFAULT_ROTATE_ANGLE_THRESHOLD)
    return RotateGestureSettings(
        allowDuringZoom = prefs.getBoolean(Prefs.ROTATE_WHILE_ZOOMING, false),
        // the map throws for a negative or non-finite angle
        startAngle = (if (threshold.isFinite() && threshold >= 0f) threshold else DEFAULT_ROTATE_ANGLE_THRESHOLD).toDouble(),
    )
}

fun mainMapUiOptions(prefs: Preferences): MapUiOptions {
    val rotate = rotateGestureSettings(prefs)
    return MapUiOptions {
        bindings {
            transform {
                rotate {
                    allowDuringZoom = rotate.allowDuringZoom
                    startAngle = rotate.startAngle
                }
            }
        }
    }
}
