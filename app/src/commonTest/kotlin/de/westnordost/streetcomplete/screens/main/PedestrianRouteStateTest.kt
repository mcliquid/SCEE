package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.routing.PedestrianRoute
import de.westnordost.streetcomplete.data.routing.RoutingError
import de.westnordost.streetcomplete.data.routing.RoutingException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PedestrianRouteStateTest {
    private val start = LatLon(52.5, 13.3)
    private val end = LatLon(52.6, 13.4)
    private val route = PedestrianRoute(listOf(start, end), 100.0, 80.0)

    @Test fun `selection preserves alternative metadata colors and needs no new request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val other = route.copy(points = listOf(start, LatLon(52.55, 13.3), end), distanceMeters = 110.0, durationSeconds = 95.0)
        var calls = 0
        val state = PedestrianRouteState(scope) {
            if (++calls == 1) listOf(route, other) else throw RoutingException(RoutingError.Network)
        }
        try {
            state.start(start, end)
            val color = de.westnordost.streetcomplete.screens.main.map.layers.pedestrianRouteColor(1)
            state.select(1)
            assertEquals(other, state.state.value.route)
            assertEquals(110.0, state.state.value.route!!.distanceMeters)
            assertEquals(95.0, state.state.value.route!!.durationSeconds)
            assertEquals(color, de.westnordost.streetcomplete.screens.main.map.layers.pedestrianRouteColor(state.state.value.selectedIndex))
            assertTrue(color != de.westnordost.streetcomplete.screens.main.map.layers.pedestrianRouteColor(0))
            assertEquals(1, calls)
            state.select(9)
            assertEquals(1, state.state.value.selectedIndex)
            state.start(start, end)
            assertEquals(listOf(route, other), state.state.value.routes)
            assertEquals(1, state.state.value.selectedIndex)
            assertEquals(RoutingError.Network, state.state.value.error)
            state.clear()
            assertTrue(state.state.value.routes.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `new request wins and removal cancels pending result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = mutableListOf<CompletableDeferred<PedestrianRoute>>()
        val state = PedestrianRouteState(scope) { points ->
            assertEquals(listOf(start, end), points)
            listOf(CompletableDeferred<PedestrianRoute>().also { pending.add(it) }.await())
        }
        try {
            state.start(start, end)
            assertTrue(state.state.value.loading)
            state.start(start, end)
            pending[1].complete(route)
            pending[0].complete(route.copy(distanceMeters = 999.0))
            assertEquals(route, state.state.value.route)
            state.start(start, end)
            state.clear()
            pending[2].complete(route)
            assertEquals(PedestrianRouteUiState(), state.state.value)
        } finally { scope.cancel() }
    }

    @Test fun `failure preserves previous route and screen disposal cancels request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val pending = CompletableDeferred<PedestrianRoute>()
        val state = PedestrianRouteState(scope) {
            when (++calls) {
                1 -> listOf(route)
                2 -> throw RoutingException(RoutingError.Network)
                else -> listOf(pending.await())
            }
        }
        state.start(start, end)
        state.start(start, end)
        assertEquals(route, state.state.value.route)
        assertEquals(RoutingError.Network, state.state.value.error)
        state.dismissError()
        assertNull(state.state.value.error)
        state.start(start, end)
        scope.cancel()
        pending.complete(route.copy(distanceMeters = 999.0))
        assertEquals(route, state.state.value.route)
    }
}
