package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.test.*

class ElementEditsCacheConcurrencyTest {
    @Test fun `read snapshot waits for in-flight cache write and observes committed state`() {
        val original = edit(id = 1, isSynced = false)
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val db = mock<ElementEditsDao>()
        every { db.getAll() } returns listOf(original)
        every { db.markSynced(any()) } calls {
            writing.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            true
        }
        val controller = ElementEditsControllerImpl(db, mock(), mock())
        controller.getAll() // initialize the actual cache before the writer starts
        val lock = controller.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(controller) as ReentrantLock
        val workers = Executors.newFixedThreadPool(2)
        try {
            val writer = workers.submit { controller.markSynced(original, MapDataUpdates()) }
            assertTrue(writing.await(5, TimeUnit.SECONDS))
            val reader = workers.submit<List<ElementEdit>> { controller.getAll() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!lock.hasQueuedThreads() && !reader.isDone && System.nanoTime() < deadline) Thread.yield()
            assertTrue(lock.hasQueuedThreads(), "snapshot must use the same lock as the in-flight write")
            assertFalse(reader.isDone)
            release.countDown()
            writer.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(original.copy(isSynced = true)), reader.get(5, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            workers.shutdownNow()
        }
    }
}
