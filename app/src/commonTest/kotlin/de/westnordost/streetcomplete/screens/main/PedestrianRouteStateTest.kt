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

    @Test fun `new request wins and removal cancels pending result`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = mutableListOf<CompletableDeferred<PedestrianRoute>>()
        val state = PedestrianRouteState(scope) { points ->
            assertEquals(listOf(start, end), points)
            CompletableDeferred<PedestrianRoute>().also { pending.add(it) }.await()
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
                1 -> route
                2 -> throw RoutingException(RoutingError.Network)
                else -> pending.await()
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
