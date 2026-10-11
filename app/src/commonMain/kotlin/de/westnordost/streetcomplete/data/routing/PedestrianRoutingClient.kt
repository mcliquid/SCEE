package de.westnordost.streetcomplete.data.routing

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.TimeSource

data class PedestrianRoute(val points: List<LatLon>, val distanceMeters: Double, val durationSeconds: Double)

enum class RoutingError { Network, Timeout, Server, NoRoute, InvalidResponse }
class RoutingException(val error: RoutingError) : Exception(error.name)

/** Public FOSSGIS foot backend. This URL is the single configuration point for the service. */
const val PEDESTRIAN_ROUTING_BASE_URL = "https://routing.openstreetmap.de/routed-foot"

class PedestrianRoutingClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = PEDESTRIAN_ROUTING_BASE_URL,
) {
    private val mutex = Mutex()
    private var lastRequest: TimeSource.Monotonic.ValueTimeMark? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** Ordered positions: start, optional intermediate points, destination. */
    suspend fun route(positions: List<LatLon>): PedestrianRoute = withContext(Dispatchers.IO) {
        requestRoute(positions)
    }

    private suspend fun requestRoute(positions: List<LatLon>): PedestrianRoute = mutex.withLock {
        require(positions.size >= 2 && positions.all { it.isRoutingCoordinateValid() })
        lastRequest?.let { delay((1000 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)) }
        try {
            withTimeout(30_000) {
                val coordinates = positions.joinToString(";") { "${it.longitude},${it.latitude}" }
                val response = httpClient.get("${baseUrl.trimEnd('/')}/route/v1/foot/$coordinates") {
                    expectSuccess = false
                    header(HttpHeaders.UserAgent, "${ApplicationConstants.USER_AGENT} SCEE-mc-private-routing")
                    parameter("overview", "full")
                    parameter("geometries", "geojson")
                    parameter("steps", "false")
                }
                val body = response.bodyAsText()
                if (response.status.value !in 200..299) {
                    val code = runCatching { json.decodeFromString<Response>(body).code }.getOrNull()
                    throw RoutingException(if (code == "NoRoute" || code == "NoSegment") RoutingError.NoRoute else RoutingError.Server)
                }
                withContext(Dispatchers.Default) {
                    parse(body)
                }
            }
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw RoutingException(RoutingError.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RoutingException) {
            throw e
        } catch (e: Exception) {
            throw RoutingException(when (e) {
                is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> RoutingError.Timeout
                else -> RoutingError.Network
            })
        } finally {
            // Wait a full second after completion or cancellation before contacting the service again.
            lastRequest = TimeSource.Monotonic.markNow()
        }
    }

    private fun parse(body: String): PedestrianRoute {
        try {
            val response = json.decodeFromString<Response>(body)
            if (response.code == "NoRoute" || response.code == "NoSegment") throw RoutingException(RoutingError.NoRoute)
            if (response.code != "Ok") throw RoutingException(RoutingError.Server)
            val route = response.routes.firstOrNull() ?: throw RoutingException(RoutingError.InvalidResponse)
            if (route.geometry.type != "LineString") throw RoutingException(RoutingError.InvalidResponse)
            val points = route.geometry.coordinates.map {
                if (it.size != 2) throw RoutingException(RoutingError.InvalidResponse)
                LatLon(it[1], it[0])
            }
            if (points.size < 2 || points.distinct().size < 2 || points.any { !it.isRoutingCoordinateValid() }
                || !route.distance.isFinite() || route.distance <= 0
                || !route.duration.isFinite() || route.duration < 0
            ) throw RoutingException(RoutingError.InvalidResponse)
            return PedestrianRoute(points, route.distance, route.duration)
        } catch (e: RoutingException) {
            throw e
        } catch (e: Exception) {
            throw RoutingException(RoutingError.InvalidResponse)
        }
    }

    @Serializable private data class Response(val code: String, val routes: List<Route> = emptyList())
    @Serializable private data class Route(val geometry: Geometry, val distance: Double, val duration: Double)
    @Serializable private data class Geometry(val type: String, val coordinates: List<List<Double>>)
}

fun LatLon.isRoutingCoordinateValid(): Boolean =
    latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
