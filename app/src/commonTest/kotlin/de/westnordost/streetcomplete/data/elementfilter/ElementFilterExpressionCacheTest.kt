package de.westnordost.streetcomplete.data.elementfilter

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ElementFilterExpressionCacheTest {

    @Test fun identicalExpressionReturnsSameInstance() {
        val expr = "nodes with amenity = bench"
        assertSame(cachedElementFilterExpression(expr), cachedElementFilterExpression(expr))
    }

    @Test fun differentExpressionsReturnDifferentInstances() {
        val a = cachedElementFilterExpression("nodes with amenity = bench")
        val b = cachedElementFilterExpression("nodes with amenity = cafe")
        assertNotSame(a, b)
    }

    @Test fun parallelAccessReturnsSameInstancePerExpression() {
        val expressions = (0 until 50).map { i ->
            when (i % 3) {
                0 -> "nodes with amenity = bench and ref != $i"
                1 -> "ways with highway = residential and ref != $i"
                else -> "nodes with amenity = bench"
            }
        }

        repeat(10) {
            val results = runBlocking {
                val start = CompletableDeferred<Unit>()
                val jobs = (0 until 500).map { i ->
                    async(Dispatchers.Default) {
                        start.await()
                        val expr = expressions[i % expressions.size]
                        expr to cachedElementFilterExpression(expr)
                    }
                }
                start.complete(Unit)
                jobs.awaitAll()
            }
            val reference = expressions.associateWith { cachedElementFilterExpression(it) }
            for ((expr, parsed) in results) {
                assertSame(reference.getValue(expr), parsed)
            }
        }
    }
}
