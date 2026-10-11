package de.westnordost.streetcomplete.data.search

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.TimeSource
import kotlin.math.roundToInt
import kotlin.math.round

data class PhotonQuery(val text: String, val center: LatLon, val zoom: Double, val language: String)

/** The public instance rejects other languages instead of falling back itself. */
fun photonLanguage(language: String): String = language.lowercase().substringBefore('-').substringBefore('_')
    .takeIf { it in setOf("de", "en", "fr") } ?: "default"

enum class PlaceType { Attraction, Station, Stop, Address, Street, City, Region, Country, District, Cafe, Museum, Place }
data class PhotonPlace(val name: String, val context: String, val position: LatLon, val type: PlaceType) {
    val zoom: Double get() = when (type) {
        PlaceType.City -> 12.0
        PlaceType.Region -> 8.0
        PlaceType.Country -> 5.0
        PlaceType.District -> 14.0
        PlaceType.Street -> 16.0
        else -> 18.0
    }
}
enum class SearchError { Network, Timeout, Server, InvalidResponse }
class SearchException(val error: SearchError) : Exception(error.name)

/** Shared, bounded cache and conservative request spacing for the public demo service. */
class PhotonSearchClient(private val http: HttpClient, private val url: String = "https://photon.komoot.io/api",
    private val timeoutMillis: Long = 12_000,
) {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<PhotonQuery, List<PhotonPlace>>()
    private var lastRequest: TimeSource.Monotonic.ValueTimeMark? = null

    suspend fun search(query: PhotonQuery): List<PhotonPlace> = mutex.withLock {
        if (query.text.trim().length < 3) return@withLock emptyList()
        require(query.center.isValid() && query.zoom.isFinite())
        // Photon requires an integer zoom; MapLibre exposes continuous zoom values.
        val normalized = query.copy(text = query.text.trim(),
            zoom = query.zoom.coerceIn(0.0, 22.0).roundToInt().toDouble(), language = photonLanguage(query.language))
        // Only the cache key is rounded; send the exact map center as location bias.
        val cacheKey = normalized.copy(center = LatLon(
            round(query.center.latitude * 1000) / 1000,
            round(query.center.longitude * 1000) / 1000))
        cache[cacheKey]?.let { return@withLock it }
        lastRequest?.let { delay((1000 - it.elapsedNow().inWholeMilliseconds).coerceAtLeast(0)) }
        try {
            val places = withTimeout(timeoutMillis) {
                val response = http.get(url) {
                    expectSuccess = false
                    header(HttpHeaders.UserAgent, "${ApplicationConstants.USER_AGENT} SCEE-mc-Photon-search")
                    parameter("q", normalized.text)
                    parameter("lat", query.center.latitude)
                    parameter("lon", query.center.longitude)
                    parameter("zoom", normalized.zoom.toInt())
                    parameter("limit", 8)
                    parameter("lang", normalized.language)
                }
                if (response.status.value !in 200..299) throw SearchException(SearchError.Server)
                val body = response.bodyAsText()
                withContext(Dispatchers.Default) { parsePhotonResponse(body) }
            }
            currentCoroutineContext().ensureActive()
            if (cache.size >= 24) cache.remove(cache.keys.first())
            cache[cacheKey] = places
            places
        } catch (e: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            throw SearchException(SearchError.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SearchException) {
            throw e
        } catch (e: Exception) {
            throw SearchException(when (e) {
                is HttpRequestTimeoutException, is ConnectTimeoutException, is SocketTimeoutException -> SearchError.Timeout
                else -> SearchError.Network
            })
        } finally {
            lastRequest = TimeSource.Monotonic.markNow()
        }
    }
}

private fun LatLon.isValid() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0

/** Invalid individual features are skipped; invalid collections are an error, not an empty result. */
internal fun parsePhotonResponse(body: String): List<PhotonPlace> {
    try {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["type"]?.jsonPrimitive?.content == "FeatureCollection")
        val features = root["features"]!!.jsonArray
        val places = features.mapNotNull { feature ->
            runCatching {
                val f = feature.jsonObject
                require(f["type"]?.jsonPrimitive?.content == "Feature")
                val g = f["geometry"]!!.jsonObject
                require(g["type"]?.jsonPrimitive?.content == "Point")
                val c = g["coordinates"]!!.jsonArray
                require(c.size >= 2)
                val position = LatLon(c[1].jsonPrimitive.double, c[0].jsonPrimitive.double)
                require(position.isValid())
                val p = f["properties"] as? JsonObject ?: JsonObject(emptyMap())
                fun prop(key: String) = (p[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                val street = listOfNotNull(prop("street"), prop("housenumber")).joinToString(" ")
                val name = prop("name") ?: street.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(prop("city"), prop("country")).firstOrNull()
                    ?: "${position.latitude}, ${position.longitude}"
                val type = when (prop("osm_value")) {
                    "attraction", "monument", "memorial" -> PlaceType.Attraction
                    "station", "halt" -> PlaceType.Station
                    "bus_stop", "tram_stop", "platform", "stop_position" -> PlaceType.Stop
                    "cafe" -> PlaceType.Cafe
                    "museum" -> PlaceType.Museum
                    else -> when (prop("type")) {
                        "city" -> PlaceType.City
                        "state", "county" -> PlaceType.Region
                        "country" -> PlaceType.Country
                        "district", "locality" -> PlaceType.District
                        "street" -> PlaceType.Street
                        "house" -> if (prop("housenumber") != null) PlaceType.Address else PlaceType.Place
                        else -> PlaceType.Place
                    }
                }
                val context = (listOf(street) + listOfNotNull(prop("postcode"), prop("district"), prop("city"), prop("state"), prop("country")))
                    .filter { it.isNotBlank() && it != name }.distinct().joinToString(", ")
                PhotonPlace(name, context, position, type)
            }.getOrNull()
        }
        if (features.isNotEmpty() && places.isEmpty()) throw SearchException(SearchError.InvalidResponse)
        return places.take(8)
    } catch (e: SearchException) {
        throw e
    } catch (e: Exception) {
        throw SearchException(SearchError.InvalidResponse)
    }
}
