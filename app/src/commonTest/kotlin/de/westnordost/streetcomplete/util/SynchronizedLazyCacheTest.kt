package de.westnordost.streetcomplete.util

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SynchronizedLazyCacheTest {

    private class CountingCreator<K, V>(private val create: (K) -> V) {
        private val lock = ReentrantLock()
        private val counts = HashMap<K, Int>()

        operator fun invoke(key: K): V {
            lock.withLock { counts[key] = (counts[key] ?: 0) + 1 }
            return create(key)
        }

        fun count(key: K): Int = lock.withLock { counts[key] ?: 0 }
        val totalCount: Int get() = lock.withLock { counts.values.sum() }
    }

    /** runs [block] [n] times in parallel on Dispatchers.Default, all starting at the same time */
    private fun <T> parallel(n: Int, block: (Int) -> T): List<T> = runBlocking {
        val start = CompletableDeferred<Unit>()
        val jobs = (0 until n).map { i ->
            async(Dispatchers.Default) {
                start.await()
                block(i)
            }
        }
        start.complete(Unit)
        jobs.awaitAll()
    }

    @Test fun returns_created_value() {
        val cache = SynchronizedLazyCache<String, Int> { it.length }
        assertEquals(3, cache.get("abc"))
        assertEquals(0, cache.get(""))
    }

    @Test fun caches_null() {
        val creator = CountingCreator<String, String?> { null }
        val cache = SynchronizedLazyCache(creator::invoke)
        assertNull(cache.get("a"))
        assertNull(cache.get("a"))
        assertEquals(1, creator.count("a"))
    }

    @Test fun same_key_in_parallel_is_created_once() {
        // the creation of the value blocks until all callers have been started, so that they all
        // certainly request the key while it is still being created
        val release = CompletableDeferred<Unit>()
        val creator = CountingCreator<String, String> { key ->
            runBlocking { release.await() }
            key.uppercase()
        }
        val cache = SynchronizedLazyCache(creator::invoke)
        val results = runBlocking {
            val start = CompletableDeferred<Unit>()
            val jobs = (0 until 64).map {
                async(Dispatchers.Default) {
                    start.await()
                    cache.get("key")
                }
            }
            start.complete(Unit)
            delay(100)
            release.complete(Unit)
            jobs.awaitAll()
        }
        assertEquals(List(64) { "KEY" }, results)
        assertEquals(1, creator.count("key"))
    }

    @Test fun many_different_keys_in_parallel_are_each_created_once() {
        repeat(20) {
            val creator = CountingCreator<Int, Int> { it * 2 }
            val cache = SynchronizedLazyCache(creator::invoke)
            val results = parallel(500) { i -> cache.get(i % 100) }
            assertEquals((0 until 500).map { (it % 100) * 2 }, results)
            assertEquals(100, creator.totalCount)
            for (key in 0 until 100) assertEquals(1, creator.count(key))
        }
    }

    @Test fun mixed_existing_and_new_keys_in_parallel() {
        val creator = CountingCreator<Int, String?> { if (it % 3 == 0) null else it.toString() }
        val cache = SynchronizedLazyCache(creator::invoke)
        // pre-fill half of the keys
        for (key in 0 until 50) cache.get(key)

        repeat(20) {
            val results = parallel(400) { i -> cache.get(i % 100) }
            assertEquals((0 until 400).map { k -> (k % 100).let { if (it % 3 == 0) null else it.toString() } }, results)
        }
        assertEquals(100, creator.totalCount)
        for (key in 0 until 100) assertEquals(1, creator.count(key))
    }
}
