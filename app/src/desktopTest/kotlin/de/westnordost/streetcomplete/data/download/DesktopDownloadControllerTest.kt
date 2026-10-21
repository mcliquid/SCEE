package de.westnordost.streetcomplete.data.download

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.translate
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopDownloadControllerTest {
    @Test fun `idle request runs immediately with Android cache default`() = test {
        controller.download(bbox(1), isUserInitiated = false, enqueue = false)
        val request = next()
        assertEquals(bbox(1), request.bbox)
        assertFalse(request.userInitiated)
        assertTrue(request.ignoreCache)
        request.release.complete(Unit)
        request.finished.await()
    }

    @Test fun `cache preference applies to manual and automatic requests`() = test {
        prefs.putBoolean(Prefs.MANUAL_DOWNLOAD_OVERRIDE_CACHE, false)
        for (userInitiated in listOf(false, true)) {
            controller.download(bbox(1), isUserInitiated = userInitiated)
            val request = next()
            assertFalse(request.ignoreCache)
            assertEquals(userInitiated, request.userInitiated)
            request.release.complete(Unit)
            request.finished.await()
            settle()
        }
    }

    @Test fun `enqueue while idle does not promote an automatic request`() = test {
        controller.download(bbox(1), isUserInitiated = false, enqueue = true)
        val request = next()
        assertFalse(request.userInitiated)
        request.release.complete(Unit)
    }

    @Test fun `automatic request without enqueue keeps the running job`() = test {
        controller.download(bbox(1), isUserInitiated = true)
        val first = next()
        controller.download(bbox(2), isUserInitiated = false, enqueue = false)
        settle()
        assertFalse(first.finished.isCompleted)
        assertNoRequest()
        first.release.complete(Unit)
        first.finished.await()
        settle()
        assertNoRequest()
    }

    @Test fun `queued requests are FIFO promoted and read cache policy when executed`() = test {
        controller.download(bbox(1), isUserInitiated = false)
        val first = next()
        controller.download(bbox(2), isUserInitiated = false, enqueue = true)
        controller.download(bbox(3), isUserInitiated = true, enqueue = true)
        settle()
        assertFalse(first.finished.isCompleted)
        assertNoRequest()
        prefs.putBoolean(Prefs.MANUAL_DOWNLOAD_OVERRIDE_CACHE, false)
        first.release.complete(Unit)
        for (i in 2..3) {
            val queued = next()
            assertEquals(bbox(i), queued.bbox)
            assertTrue(queued.userInitiated)
            assertFalse(queued.ignoreCache)
            queued.release.complete(Unit)
        }
        settle()
        assertNoRequest()
    }

    @Test fun `manual replacement cancels running job and preserves queued requests`() = test {
        controller.download(bbox(1), isUserInitiated = false)
        val first = next()
        controller.download(bbox(2), isUserInitiated = true, enqueue = true)
        settle()
        controller.download(bbox(3), isUserInitiated = true, enqueue = false)
        val replacement = next()
        assertTrue(first.finished.isCompleted)
        assertFalse(first.release.isCompleted)
        assertEquals(bbox(3), replacement.bbox)
        replacement.release.complete(Unit)
        val queued = next()
        assertEquals(bbox(2), queued.bbox)
        queued.release.complete(Unit)
    }

    @Test fun `rapid replacements wait for cancellation cleanup without overlap`() = test {
        controller.download(bbox(1), isUserInitiated = false)
        val first = next()
        val cleanup = CompletableDeferred<Unit>()
        first.cleanup = cleanup
        controller.download(bbox(2), isUserInitiated = true)
        settle()
        controller.download(bbox(3), isUserInitiated = true)
        settle()
        assertNoRequest()
        cleanup.complete(Unit)
        var replacement = next()
        if (replacement.bbox == bbox(2)) {
            replacement.finished.await()
            replacement = next()
        }
        assertEquals(bbox(3), replacement.bbox)
        assertTrue(first.finished.isCompleted)
        replacement.release.complete(Unit)
    }

    @Test fun `failure advances queue and does not prevent later requests`() = test {
        controller.download(bbox(1), isUserInitiated = true)
        val first = next()
        first.failure = IllegalStateException("download failed")
        controller.download(bbox(2), isUserInitiated = false, enqueue = true)
        settle()
        first.release.complete(Unit)
        val queued = next()
        assertEquals(bbox(2), queued.bbox)
        queued.release.complete(Unit)
        queued.finished.await()
        settle()
        controller.download(bbox(3), isUserInitiated = false)
        val later = next()
        assertEquals(bbox(3), later.bbox)
        later.release.complete(Unit)
    }

    @Test fun `downloadAlongGpxTrack schedules corridor download on controller scope`() = runBlocking {
        val prefs = MapSettings()
        val downloaded = Channel<BoundingBox>(1)
        val downloader = mock<Downloader>()
        everySuspend { downloader.download(any(), any(), any()) } calls { (bbox, _, _) ->
            downloaded.send(bbox)
        }
        val controller = DesktopDownloadController(downloader, prefs, Dispatchers.Default)
        try {
            val start = LatLon(52.5, 13.3)
            controller.downloadAlongGpxTrack(listOf(start, start.translate(800.0, 90.0)), 100.0)
            withTimeout(5_000) { downloaded.receive() }
        } finally {
            controller.close()
        }
    }

    @Test fun `close cancels active download and never starts queued or later requests`() = test {
        controller.download(bbox(1), isUserInitiated = true)
        val first = next()
        controller.download(bbox(2), isUserInitiated = false, enqueue = true)
        settle()
        controller.close()
        first.finished.await()
        controller.download(bbox(3), isUserInitiated = true)
        settle()
        assertFalse(first.release.isCompleted)
        assertNoRequest()
    }

    private fun test(block: suspend Harness.() -> Unit) = runBlocking {
        val harness = Harness(coroutineContext)
        try {
            withTimeout(5_000) { harness.block() }
        } finally {
            harness.controller.close()
            settle()
            assertTrue(harness.maxActive <= 1, "Downloads must not overlap")
        }
    }

    private class Harness(context: CoroutineContext) {
        val prefs = MapSettings()
        private val downloader = mock<Downloader>()
        private val requests = Channel<Request>(Channel.UNLIMITED)
        private var active = 0
        var maxActive = 0
            private set
        val controller = DesktopDownloadController(downloader, prefs, context)

        init {
            everySuspend { downloader.download(any(), any(), any()) } calls {
                (bbox: BoundingBox, userInitiated: Boolean, ignoreCache: Boolean) ->
                val request = Request(bbox, userInitiated, ignoreCache)
                active++
                maxActive = maxOf(maxActive, active)
                requests.send(request)
                try {
                    request.release.await()
                    request.failure?.let { throw it }
                } finally {
                    request.cleanup?.let { withContext(NonCancellable) { it.await() } }
                    active--
                    request.finished.complete(Unit)
                }
            }
        }

        suspend fun next(): Request = requests.receive()
        fun assertNoRequest() { assertTrue(requests.tryReceive().isFailure) }
    }

    private class Request(val bbox: BoundingBox, val userInitiated: Boolean, val ignoreCache: Boolean) {
        val release = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var cleanup: CompletableDeferred<Unit>? = null
        var failure: Exception? = null
    }

    companion object {
        private fun bbox(i: Int) = BoundingBox(i.toDouble(), 0.0, i + 0.1, 0.1)
        private suspend fun settle() { repeat(10) { yield() } }
    }
}
