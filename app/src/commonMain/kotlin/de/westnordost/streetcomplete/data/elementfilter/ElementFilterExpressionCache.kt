package de.westnordost.streetcomplete.data.elementfilter

import de.westnordost.streetcomplete.util.SynchronizedLazyCache

private object ElementFilterExpressionCache {
    // used concurrently by quest creation, map filtering, custom overlay, etc.
    private val cache = SynchronizedLazyCache<String, ElementFilterExpression> {
        it.toElementFilterExpression()
    }

    fun get(expr: String): ElementFilterExpression = cache.get(expr)
}

/** Parsed filter for [expr], created at most once per distinct string (thread-safe). */
fun cachedElementFilterExpression(expr: String): ElementFilterExpression =
    ElementFilterExpressionCache.get(expr)
