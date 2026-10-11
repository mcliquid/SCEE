package de.westnordost.streetcomplete.data.routing

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.TimeSource

class PedestrianRoutingClientTest {
    private val positions = listOf(LatLon(52.5, 13.3), LatLon(52.6, 13.4))
    private val success = """{"code":"Ok","routes":[{"distance":100,"duration":80,"geometry":{"type":"LineString","coordinates":[[13.3,52.5],[13.4,52.6]]}}]}"""

    @Test fun `foot request preserves ordered coordinates and parses GeoJSON`() = runBlocking {
        val http = HttpClient(MockEngine { request ->
            assertTrue(request.url.encodedPath.endsWith("/route/v1/foot/13.3,52.5;13.4,52.6"))
            assertEquals("geojson", request.url.parameters["geometries"])
            assertEquals("full", request.url.parameters["overview"])
            assertTrue(request.headers["User-Agent"]!!.contains("SCEE-mc-private-routing"))
            respond(success)
        })
        try {
            val route = PedestrianRoutingClient(http).route(positions)
            assertEquals(positions, route.points)
            assertEquals(100.0, route.distanceMeters)
            assertEquals(80.0, route.durationSeconds)
        } finally { http.close() }
    }

    @Test fun `empty malformed and invalid geometry are rejected`() = runBlocking {
        for (body in listOf("", "{}", """{"code":"Ok","routes":[]}""",
            success.replace("[[13.3,52.5],[13.4,52.6]]", "[]"),
            success.replace("52.6", "152.6"),
            success.replace("LineString", "Polygon"))) {
            val http = HttpClient(MockEngine { respond(body) })
            try {
                assertEquals(RoutingError.InvalidResponse,
                    assertFailsWith<RoutingException> { PedestrianRoutingClient(http).route(positions) }.error)
            } finally { http.close() }
        }
    }

    @Test fun `no route and HTTP errors are classified`() = runBlocking {
        for ((body, status, expected) in listOf(
            Triple("""{"code":"NoRoute"}""", HttpStatusCode.OK, RoutingError.NoRoute),
            Triple("""{"code":"NoSegment"}""", HttpStatusCode.BadRequest, RoutingError.NoRoute),
            Triple("unavailable", HttpStatusCode.ServiceUnavailable, RoutingError.Server))) {
            val http = HttpClient(MockEngine { respond(body, status) })
            try {
                assertEquals(expected, assertFailsWith<RoutingException> { PedestrianRoutingClient(http).route(positions) }.error)
            } finally { http.close() }
        }
    }

    @Test fun `timeout is classified`() = runBlocking {
        val http = HttpClient(MockEngine { request -> throw HttpRequestTimeoutException(request) })
        try {
            assertEquals(RoutingError.Timeout, assertFailsWith<RoutingException> { PedestrianRoutingClient(http).route(positions) }.error)
        } finally { http.close() }
    }

    @Test fun `caller timeout remains cancellation`() = runBlocking<Unit> {
        val http = HttpClient(MockEngine { delay(500); respond(success) })
        try {
            assertFailsWith<CancellationException> {
                withTimeout(50) { PedestrianRoutingClient(http).route(positions) }
            }
        } finally { http.close() }
    }

    @Test fun `requests are spaced and intermediate coordinates remain ordered`() = runBlocking {
        var previous: TimeSource.Monotonic.ValueTimeMark? = null
        var calls = 0
        val http = HttpClient(MockEngine { request ->
            previous?.let { assertTrue(it.elapsedNow().inWholeMilliseconds >= 990) }
            previous = TimeSource.Monotonic.markNow()
            ++calls
            assertTrue(request.url.encodedPath.endsWith("13.3,52.5;13.35,52.55;13.4,52.6"))
            respond(success)
        })
        try {
            val client = PedestrianRoutingClient(http)
            val via = listOf(positions[0], LatLon(52.55, 13.35), positions[1])
            client.route(via)
            client.route(via)
            assertEquals(2, calls)
        } finally { http.close() }
    }

    @Test fun `network failure is classified and cancellation propagates`() = runBlocking<Unit> {
        val failed = HttpClient(MockEngine { throw IllegalStateException("network") })
        try {
            assertEquals(RoutingError.Network, assertFailsWith<RoutingException> { PedestrianRoutingClient(failed).route(positions) }.error)
        } finally { failed.close() }
        val cancelled = HttpClient(MockEngine { throw CancellationException("cancelled") })
        try {
            assertFailsWith<CancellationException> { PedestrianRoutingClient(cancelled).route(positions) }
        } finally { cancelled.close() }
    }
}
