package de.westnordost.streetcomplete.data.search

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.TimeSource

class PhotonSearchClientTest {
    @Test fun `app languages use supported public instance languages`() {
        assertEquals("de", photonLanguage("de-DE"))
        assertEquals("en", photonLanguage("en_GB"))
        assertEquals("fr", photonLanguage("fr"))
        assertEquals("default", photonLanguage("es"))
        assertEquals("default", photonLanguage("ja"))
    }
    private val query = PhotonQuery("Berlin", LatLon(52.52, 13.405), 14.0, "de")
    private val feature = """{"type":"Feature","geometry":{"type":"Point","coordinates":[13.4,52.5]},"properties":{"name":"Tor","street":"Platz","housenumber":"1","city":"Berlin","osm_value":"attraction"}}"""
    private fun collection(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    @Test fun `GeoJSON coordinates metadata and equal names retain server order`() {
        val places = parsePhotonResponse(collection(feature, feature.replace("Berlin", "Paris").replace("13.4,52.5", "2.3,48.8")))
        assertEquals(listOf("Tor", "Tor"), places.map { it.name })
        assertEquals(LatLon(52.5, 13.4), places.first().position)
        assertEquals(PlaceType.Attraction, places.first().type)
        assertTrue(places[0].context.contains("Berlin"))
        assertTrue(places[1].context.contains("Paris"))
        assertEquals(8, parsePhotonResponse(collection(*Array(12) { feature })).size)
    }

    @Test fun `optional properties and nameless addresses are handled`() {
        val minimal = feature.substringBefore("\"properties\"") + "\"properties\":{}}"
        assertEquals(PlaceType.Place, parsePhotonResponse(collection(minimal)).single().type)
        val address = feature.replace("\"name\":\"Tor\",", "").replace("\"osm_value\":\"attraction\"", "\"type\":\"house\"")
        assertEquals("Platz 1", parsePhotonResponse(collection(address)).single().name)
        assertEquals(PlaceType.Address, parsePhotonResponse(collection(address)).single().type)
        assertEquals(emptyList(), parsePhotonResponse(collection()))
    }

    @Test fun `invalid geometry cannot reach the map and collections are validated`() {
        for (invalid in listOf(feature.replace("52.5", "152.5"), feature.replace("13.4", "181"),
            feature.replace("Point", "LineString"), feature.replace("[13.4,52.5]", "[]"))) {
            assertEquals(1, parsePhotonResponse(collection(invalid, feature)).size)
            assertEquals(SearchError.InvalidResponse, assertFailsWith<SearchException> { parsePhotonResponse(collection(invalid)) }.error)
        }
        for (body in listOf("{}", "oops", collection().replace("FeatureCollection", "Point"))) {
            assertFailsWith<SearchException> { parsePhotonResponse(body) }
        }
    }

    @Test fun `request uses map bias language limit and identifiable user agent with cache`() = runBlocking {
        var calls = 0
        val http = HttpClient(MockEngine { request ->
            calls++
            assertEquals("Berlin", request.url.parameters["q"])
            assertEquals("52.52", request.url.parameters["lat"])
            assertEquals("13.405", request.url.parameters["lon"])
            assertEquals("14", request.url.parameters["zoom"])
            assertEquals("8", request.url.parameters["limit"])
            assertEquals("de", request.url.parameters["lang"])
            assertNull(request.url.parameters["bbox"])
            assertNull(request.url.parameters["countrycode"])
            assertTrue(request.headers["User-Agent"]!!.contains("SCEE-mc-Photon-search"))
            respond(collection(feature))
        })
        try {
            val client = PhotonSearchClient(http)
            assertEquals(client.search(query), client.search(query))
            client.search(query.copy(text = " Berlin ", zoom = 14.3))
            assertEquals(1, calls)
            client.search(query.copy(text = ""))
            assertEquals(1, calls)
        } finally { http.close() }
    }

    @Test fun `small map movements reuse cache while region zoom language and text remain distinct`() = runBlocking {
        var calls = 0
        val http = HttpClient(MockEngine { request ->
            calls++
            if (calls == 1) assertEquals("52.52001", request.url.parameters["lat"])
            respond(collection())
        })
        try {
            val client = PhotonSearchClient(http)
            val first = query.copy(center = LatLon(52.52001, 13.40501))
            client.search(first)
            client.search(first.copy(center = LatLon(52.5202, 13.4052)))
            assertEquals(1, calls)
            client.search(first.copy(center = LatLon(52.522, 13.40501)))
            client.search(first.copy(zoom = 15.0))
            client.search(first.copy(language = "fr"))
            client.search(first.copy(text = "Alexanderplatz"))
            assertEquals(5, calls)
        } finally { http.close() }
    }

    @Test fun `HTTP failure and timeout are understandable categories`() = runBlocking {
        for (timeout in listOf(false, true)) {
            val http = HttpClient(MockEngine { request ->
                if (timeout) throw HttpRequestTimeoutException(request)
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            })
            try {
                assertEquals(if (timeout) SearchError.Timeout else SearchError.Server,
                    assertFailsWith<SearchException> { PhotonSearchClient(http).search(query) }.error)
            } finally { http.close() }
        }
    }

    @Test fun `request deadline cancels a stalled server`() = runBlocking {
        var cancelled = false
        val http = HttpClient(MockEngine {
            try { awaitCancellation() } finally { cancelled = true }
        })
        try {
            assertEquals(SearchError.Timeout, assertFailsWith<SearchException> {
                PhotonSearchClient(http, timeoutMillis = 100).search(query)
            }.error)
            assertTrue(cancelled)
        } finally { http.close() }
    }

    @Test fun `changed bias bypasses cache and requests are spaced`() = runBlocking {
        var calls = 0
        val http = HttpClient(MockEngine { calls++; respond(collection()) })
        try {
            val client = PhotonSearchClient(http)
            client.search(query)
            val mark = TimeSource.Monotonic.markNow()
            client.search(query.copy(center = LatLon(48.8, 2.3)))
            assertTrue(mark.elapsedNow().inWholeMilliseconds >= 950)
            client.search(query.copy(language = "fr"))
            assertEquals(3, calls)
        } finally { http.close() }
    }

    @Test fun `cancellation propagates and a cancelled response is not cached`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var calls = 0
        val http = HttpClient(MockEngine {
            if (++calls == 1) { started.complete(Unit); awaitCancellation() }
            respond(collection())
        })
        try {
            val client = PhotonSearchClient(http)
            val job = launch { client.search(query) }
            started.await()
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            client.search(query)
            assertEquals(2, calls)
        } finally { http.close() }
    }
}
