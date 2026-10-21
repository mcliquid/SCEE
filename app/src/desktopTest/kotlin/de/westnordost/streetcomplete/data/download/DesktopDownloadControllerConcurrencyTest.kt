package de.westnordost.streetcomplete.data.download

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import dev.mokkery.answering.calls
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/** The controller must not lose queued requests or overlap downloads when it runs on a multi-threaded dispatcher. */
class DesktopDownloadControllerConcurrencyTest {
    @Test fun `concurrent enqueue from many threads runs every request exactly once and never overlaps`() {
        // without a delay, downloads end while new requests arrive: the window in which a request could be lost
        repeat(30) { round ->
            val downloadTakesTime = round % 3 == 0
            val executed = ConcurrentLinkedQueue<BoundingBox>()
            val active = AtomicInteger()
            val maxActive = AtomicInteger()
            val downloader = mock<Downloader>()
            everySuspend { downloader.download(any(), any(), any()) } calls { (bbox: BoundingBox, _: Boolean, _: Boolean) ->
                maxActive.accumulateAndGet(active.incrementAndGet()) { a, b -> maxOf(a, b) }
                try {
                    if (downloadTakesTime) delay(1)
                    executed.add(bbox)
                } finally {
                    active.decrementAndGet()
                }
            }
            val controller = DesktopDownloadController(downloader, MapSettings(), Dispatchers.Default)
            val threads = 8
            val perThread = 40 // keep threads * perThread * 0.001 within the valid longitude range
            val start = CountDownLatch(1)
            val workers = (0 until threads).map { t ->
                thread {
                    start.await()
                    for (i in 0 until perThread) {
                        Thread.yield()
                        controller.download(BoundingBox(0.0, (t * perThread + i) * 0.001, 0.1, (t * perThread + i) * 0.001 + 0.0005), false, true)
                    }
                }
            }
            start.countDown()
            workers.forEach { it.join() }
            try {
                runBlocking {
                    withTimeout(30_000) { while (executed.size < threads * perThread) delay(10) }
                    // nothing more than requested may arrive afterwards
                    delay(200)
                }
                assertEquals(threads * perThread, executed.size, "round $round")
                assertEquals(threads * perThread, executed.toSet().size, "each request exactly once, round $round")
                assertEquals(1, maxActive.get(), "downloads must not overlap, round $round")
            } finally {
                controller.close()
            }
        }
    }
}
