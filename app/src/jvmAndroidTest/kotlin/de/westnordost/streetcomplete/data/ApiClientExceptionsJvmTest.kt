package de.westnordost.streetcomplete.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.net.SocketException
import java.nio.channels.UnresolvedAddressException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** Regression tests for https://github.com/streetcomplete/StreetComplete/issues/7324 */
class ApiClientExceptionsJvmTest {

    @Test fun `CIO leaks UnresolvedAddressException on DNS failure`() = runBlocking<Unit> {
        // .invalid is reserved (RFC 2606) and never resolves
        HttpClient(CIO).use { client ->
            assertFailsWith<UnresolvedAddressException> { client.get("http://host.invalid/") }
        }
    }

    @Test fun `DNS failure from CIO becomes ConnectionException`() = runBlocking<Unit> {
        HttpClient(CIO).use { client ->
            val e = assertFailsWith<ConnectionException> {
                wrapApiClientExceptions { client.get("http://host.invalid/") }
            }
            assertIs<UnresolvedAddressException>(e.cause)
        }
    }

    @Test fun `connection errors become ConnectionException`() = runBlocking<Unit> {
        for (error in listOf(
            UnresolvedAddressException(),
            ConnectException("connection refused"),
            SocketException("connection reset"),
            HttpRequestTimeoutException("http://x", 1000),
        )) {
            val client = HttpClient(MockEngine { throw error })
            val e = assertFailsWith<ConnectionException> { wrapApiClientExceptions { client.get("http://x") } }
            assertEquals(error::class, e.cause!!::class)
        }
    }

    @Test fun `request succeeds after a connection error`() = runBlocking<Unit> {
        var fail = true
        val client = HttpClient(MockEngine { if (fail) throw UnresolvedAddressException() else respondOk() })
        assertFailsWith<ConnectionException> { wrapApiClientExceptions { client.get("http://x") } }
        fail = false
        wrapApiClientExceptions { client.get("http://x") }
    }

    @Test fun `other IllegalArgumentExceptions are not swallowed`() {
        assertFailsWith<IllegalArgumentException> { wrapApiClientExceptions { throw IllegalArgumentException() } }
    }

    @Test fun `cancellation is passed through`() {
        assertFailsWith<CancellationException> { wrapApiClientExceptions { throw CancellationException() } }
    }
}
