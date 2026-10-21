package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SceeMainSettingsTest {
    @Test fun `solved animation is on by default and can be disabled`() {
        val prefs = inMemoryPrefs()
        assertTrue(shouldShowSolvedAnimation(prefs))
        prefs.putBoolean(Prefs.SHOW_SOLVED_ANIMATION, false)
        assertFalse(shouldShowSolvedAnimation(prefs))
        prefs.putBoolean(Prefs.SHOW_SOLVED_ANIMATION, true)
        assertTrue(shouldShowSolvedAnimation(prefs))
    }

    @Test fun `navigation mode can be toggled by default`() {
        val prefs = inMemoryPrefs()
        assertTrue(canToggleNavigationMode(prefs, isNavigationMode = false))
        assertTrue(canToggleNavigationMode(prefs, isNavigationMode = true))
    }

    @Test fun `disabled navigation mode cannot be entered but can be left`() {
        val prefs = inMemoryPrefs()
        prefs.putBoolean(Prefs.DISABLE_NAVIGATION_MODE, true)
        assertFalse(canToggleNavigationMode(prefs, isNavigationMode = false))
        assertTrue(canToggleNavigationMode(prefs, isNavigationMode = true))
    }

    @Test fun `no location request without interval setting`() {
        assertNull(mainLocationRequest(inMemoryPrefs()))
    }

    @Test fun `location request uses interval in seconds`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.LOCATION_INTERVAL, 7)
        assertEquals(7.seconds, mainLocationRequest(prefs)!!.minimumInterval)
    }

    @Test fun `zero interval is still a request`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.LOCATION_INTERVAL, 0)
        assertEquals(0.seconds, mainLocationRequest(prefs)!!.minimumInterval)
    }
}

class LocationUpdateThrottleTest {
    /** a measurement taken at second [measuredAt]; "now" is second 100 */
    private fun mark(measuredAt: Int) = object : kotlin.time.TimeMark {
        override fun elapsedNow() = (100 - measuredAt).seconds
    }

    @Test fun `updates faster than the interval are dropped, based on measurement time`() {
        val t = LocationUpdateThrottle(10.seconds)
        assertTrue(t.accept(mark(0)))
        assertFalse(t.accept(mark(3)))
        assertFalse(t.accept(mark(9)))
        assertTrue(t.accept(mark(10)))
        assertFalse(t.accept(mark(15)))
        assertTrue(t.accept(mark(21)))
    }

    @Test fun `dropped updates do not move the reference`() {
        val t = LocationUpdateThrottle(10.seconds)
        assertTrue(t.accept(mark(0)))
        assertFalse(t.accept(mark(6)))
        assertTrue(t.accept(mark(10))) // 10 after the last accepted, not 4 after the dropped one
    }

    @Test fun `a stale first update does not delay the next real one`() {
        val t = LocationUpdateThrottle(10.seconds)
        assertTrue(t.accept(mark(-600))) // last known location from ten minutes ago
        assertTrue(t.accept(mark(1)))
    }

    @Test fun `reset accepts the next update`() {
        val t = LocationUpdateThrottle(10.seconds)
        assertTrue(t.accept(mark(0)))
        t.reset()
        assertTrue(t.accept(mark(1)))
    }

    @Test fun `without setting or with zero nothing is throttled`() {
        val none = mainLocationUpdateThrottle(inMemoryPrefs())
        assertTrue(none.accept(mark(0)))
        assertTrue(none.accept(mark(1)))
        val prefs = inMemoryPrefs().also { it.putInt(Prefs.LOCATION_INTERVAL, 0) }
        val zero = mainLocationUpdateThrottle(prefs)
        assertTrue(zero.accept(mark(0)))
        assertTrue(zero.accept(mark(0)))
    }

    @Test fun `the setting selects the interval`() {
        val prefs = inMemoryPrefs().also { it.putInt(Prefs.LOCATION_INTERVAL, 10) }
        val t = mainLocationUpdateThrottle(prefs)
        assertTrue(t.accept(mark(0)))
        assertFalse(t.accept(mark(4)))
        assertTrue(t.accept(mark(10)))
    }
}
