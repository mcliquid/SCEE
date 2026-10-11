package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.routing.PedestrianRoute
import de.westnordost.streetcomplete.data.search.*
import kotlinx.coroutines.*
import kotlin.test.*

class PlaceSearchStateTest {
    private val query = PhotonQuery("Berlin", LatLon(52.5, 13.4), 14.0, "de")
    private val place = PhotonPlace("Berlin", "Deutschland", query.center, PlaceType.City)

    @Test fun `debounce short input and identical query avoid redundant calls`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var calls = 0
        val done = CompletableDeferred<Unit>()
        val search = PlaceSearchState(scope) { calls++; done.complete(Unit); listOf(place) }
        try {
            search.open()
            search.update(query.copy(text = "Be"))
            delay(500)
            assertEquals(0, calls)
            search.update(query)
            delay(200)
            search.update(query.copy(text = "Paris"))
            delay(200)
            assertEquals(0, calls)
            withTimeout(2000) { done.await() }
            delay(20)
            search.update(query.copy(text = " Paris "))
            delay(500)
            assertEquals(1, calls)
            assertFalse(search.state.value.loading)
        } finally { scope.cancel() }
    }

    @Test fun `stale uncooperative response and close cannot publish results`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val search = PlaceSearchState(scope) {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            listOf(place)
        }
        try {
            search.open(); search.update(query)
            withTimeout(2000) { started.await() }
            search.update(query.copy(text = "Pa"))
            release.complete(Unit)
            delay(50)
            assertTrue(search.state.value.results.isEmpty())
            assertFalse(search.state.value.loading)
            search.update(query)
            search.close()
            delay(500)
            assertFalse(search.state.value.open)
            assertEquals("", search.state.value.query)
            assertTrue(search.state.value.results.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `errors and empty results stop loading`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var fail = true
        val search = PlaceSearchState(scope) { if (fail) throw SearchException(SearchError.Timeout) else emptyList() }
        try {
            search.open(); search.update(query)
            delay(550)
            assertEquals(SearchError.Timeout, search.state.value.error)
            assertFalse(search.state.value.loading)
            fail = false
            search.retry()
            delay(550)
            assertNull(search.state.value.error)
            assertTrue(search.state.value.results.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `old response cannot replace newer results`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val newer = place.copy(name = "Paris")
        val search = PlaceSearchState(scope) { input ->
            if (input.text == "Berlin") {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                listOf(place)
            } else listOf(newer)
        }
        try {
            search.open(); search.update(query)
            withTimeout(2000) { started.await() }
            search.update(query.copy(text = "Paris"))
            delay(550)
            assertEquals(listOf(newer), search.state.value.results)
            release.complete(Unit)
            delay(50)
            assertEquals(listOf(newer), search.state.value.results)
        } finally { release.complete(Unit); scope.cancel() }
    }

    @Test fun `select close and remove marker preserve existing route and alternatives`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val route = PedestrianRoute(listOf(query.center, LatLon(52.6, 13.5)), 100.0, 80.0)
        var routeCalls = 0
        val routing = PedestrianRouteState(scope) { routeCalls++; listOf(route, route.copy(distanceMeters = 120.0)) }
        val search = PlaceSearchState(scope) { listOf(place) }
        try {
            routing.start(route.points.first(), route.points.last()); routing.select(1)
            val before = routing.state.value
            search.open(); search.update(query); search.select(place)
            assertFalse(search.state.value.open)
            assertEquals(place, search.state.value.selected)
            search.open(); search.close()
            assertEquals(place, search.state.value.selected)
            search.removeMarker()
            assertNull(search.state.value.selected)
            assertEquals(before, routing.state.value)
            assertEquals(1, routeCalls)
        } finally { scope.cancel() }
    }
}
