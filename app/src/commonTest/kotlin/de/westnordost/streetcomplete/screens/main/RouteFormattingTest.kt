package de.westnordost.streetcomplete.screens.main

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteFormattingTest {
    @Test fun `minutes round and positive durations show at least one minute`() {
        assertEquals(0, routeMinutes(0.0))
        assertEquals(1, routeMinutes(0.1))
        assertEquals(1, routeMinutes(89.0))
        assertEquals(2, routeMinutes(90.0))
        assertEquals(54, routeMinutes(3239.0))
    }
}
