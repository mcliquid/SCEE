package de.westnordost.streetcomplete.util

import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

/** Thread-safe cache that creates the value for each key at most once, also when accessed from
 *  several threads at the same time (e.g. quest creation, which runs one coroutine per quest type
 *  on Dispatchers.Default).
 *
 *  Only the map is guarded by the lock. The (possibly expensive) [create] runs outside of it inside
 *  a synchronized [Lazy], so different keys are created in parallel while concurrent requests for
 *  the same key wait for that single creation. `null` results are cached as well. */
internal class SynchronizedLazyCache<K, V>(private val create: (K) -> V) {
    private val lock = ReentrantLock()
    private val map = HashMap<K, Lazy<V>>()

    fun get(key: K): V =
        lock.withLock { map.getOrPut(key) { lazy { create(key) } } }.value
}
