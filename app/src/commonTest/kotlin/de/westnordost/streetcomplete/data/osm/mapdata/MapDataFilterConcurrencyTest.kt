package de.westnordost.streetcomplete.data.osm.mapdata

import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MapDataFilterConcurrencyTest {

    private val mapData = TestMapDataWithGeometry(listOf(
        node(1, tags = mapOf("amenity" to "bench")),
        node(2, tags = mapOf("amenity" to "bench", "backrest" to "yes")),
        node(3, tags = mapOf("shop" to "bakery")),
        way(4, tags = mapOf("highway" to "residential")),
        way(5, tags = mapOf("highway" to "footway", "surface" to "asphalt")),
    ))

    private val expressions = (0 until 200).map { i ->
        // many distinct strings, but only a few distinct semantics
        when (i % 4) {
            0 -> "nodes with amenity = bench and !backrest and ref != $i"
            1 -> "nodes, ways with (shop or highway = footway) and ref != $i"
            2 -> "ways with highway and surface != gravel$i"
            else -> "nodes with amenity = bench"
        }
    }

    private fun idsOf(elements: Sequence<Element>) = elements.map { it.key }.toList()

    @Test fun filter_by_string_gives_same_result_as_uncached_expression() {
        for (expr in expressions) {
            assertEquals(idsOf(mapData.filter(expr.toElementFilterExpression())), idsOf(mapData.filter(expr)))
        }
    }

    @Test fun parallel_filtering_gives_same_results_as_sequential_filtering() {
        val expected = expressions.associateWith { idsOf(mapData.filter(it.toElementFilterExpression())) }

        repeat(20) {
            val results = runBlocking {
                val start = CompletableDeferred<Unit>()
                val jobs = (0 until 1000).map { i ->
                    async(Dispatchers.Default) {
                        start.await()
                        // mixes identical, new and already cached expressions
                        val expr = expressions[(i * 7) % expressions.size]
                        expr to idsOf(mapData.filter(expr))
                    }
                }
                start.complete(Unit)
                jobs.awaitAll()
            }
            for ((expr, ids) in results) {
                assertEquals(expected.getValue(expr), ids)
            }
        }
    }
}
