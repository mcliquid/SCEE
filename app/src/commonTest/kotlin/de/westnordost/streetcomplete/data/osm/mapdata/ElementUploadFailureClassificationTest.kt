package de.westnordost.streetcomplete.data.osm.mapdata

import de.westnordost.streetcomplete.data.*
import de.westnordost.streetcomplete.data.osm.edits.ElementUploadRejectedException
import de.westnordost.streetcomplete.testutils.node
import dev.mokkery.mock
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.*

class ElementUploadFailureClassificationTest {
    @Test fun `only upload endpoint validation 400 is a definite rejection`() = runBlocking<Unit> {
        val expected = mapOf(400 to ElementUploadRejectedException::class, 401 to AuthorizationException::class,
            403 to AuthorizationException::class, 404 to ConflictException::class, 409 to ConflictException::class,
            410 to ConflictException::class, 412 to ConflictException::class, 413 to ChangesetTooLargeException::class,
            408 to ConnectionException::class, 429 to ConnectionException::class, 500 to ConnectionException::class,
            422 to ApiClientException::class, 406 to ApiClientException::class)
        for ((status, type) in expected) {
            val client = HttpClient(MockEngine { respond("Invalid edit", HttpStatusCode.fromValue(status),
                headersOf(HttpHeaders.ContentType, "text/plain")) })
            try {
                val api = MapDataApiClientImpl(client, "https://example.invalid/api/0.6/", mock(), MapDataApiParser(), MapDataApiSerializer())
                val error = assertFails { api.uploadChanges(1, MapDataChanges(modifications = listOf(node()))) }
                assertEquals(type, error::class, "HTTP $status")
                if (error is ElementUploadRejectedException) {
                    assertEquals(400, error.httpStatus)
                    assertEquals("Invalid edit", error.message)
                }
            } finally { client.close() }
        }
    }

    @Test fun `read endpoint 400 is not converted to element upload rejection`() = runBlocking<Unit> {
        val client = HttpClient(MockEngine { respond("bad query", HttpStatusCode.BadRequest) })
        try {
            val api = MapDataApiClientImpl(client, "https://example.invalid/", mock(), MapDataApiParser(), MapDataApiSerializer())
            assertFailsWith<ApiClientException> { api.getNode(1) }
        } finally { client.close() }
    }

    @Test fun `I O and successful response parsing failure remain uncertain`() = runBlocking<Unit> {
        for (networkFailure in listOf(true, false)) {
            val client = HttpClient(MockEngine {
                if (networkFailure) throw IOException("reply lost")
                respond("<diffResult><node old_id='1' new_id='bad' new_version='1'/></diffResult>",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/xml"))
            })
            try {
                val api = MapDataApiClientImpl(client, "https://example.invalid/", mock(), MapDataApiParser(), MapDataApiSerializer())
                assertFailsWith<ConnectionException> { api.uploadChanges(1, MapDataChanges()) }
            } finally { client.close() }
        }
    }
}
