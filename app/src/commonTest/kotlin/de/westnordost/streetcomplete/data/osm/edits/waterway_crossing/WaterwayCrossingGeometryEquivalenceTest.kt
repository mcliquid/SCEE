package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.intersectionOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

class WaterwayCrossingGeometryEquivalenceTest {
    @Test fun `indexed intersections and overlap checks match original algorithms exactly`() {
        val lines = listOf(
            emptyList(), listOf(LatLon(0.0, 0.0)),
            listOf(LatLon(0.0, -0.002), LatLon(0.0, 0.0), LatLon(0.0, 0.002)),
            listOf(LatLon(-0.002, 0.0), LatLon(0.0, 0.0), LatLon(0.002, 0.0)),
            listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.0), LatLon(0.0, 0.002)),
            listOf(LatLon(80.0, 179.0), LatLon(81.0, -179.0), LatLon(79.0, 178.0)),
            listOf(LatLon(80.0, -179.0), LatLon(81.0, 179.0), LatLon(79.0, -178.0))
        )
        val random = Random(917)
        val generated = (0 until 30).map {
            (0 until 5).map { LatLon(47.0 + random.nextDouble(-0.01, 0.01), 9.0 + random.nextDouble(-0.01, 0.01)) }
        }
        for (a in lines + generated) for (b in lines + generated) {
            assertEquals(originalPoints(a, b), crossingPoints(a, b))
            assertEquals(originalOverlap(a, b), hasOverlappingCrossingEdges(a, b))
        }
    }

    @Test fun `normal reuse preserves simple way validation including rejection reasons`() {
        val random = Random(42)
        val cases = (0 until 60).map {
            (0 until 8).map { LatLon(47.0 + random.nextDouble(-0.01, 0.01), 9.0 + random.nextDouble(-0.01, 0.01)) }
        } + listOf(
            listOf(LatLon(0.0, 0.0), LatLon(0.0, 0.001), LatLon(0.0, 0.0005)),
            listOf(LatLon(80.0, 179.0), LatLon(81.0, -179.0), LatLon(82.0, -178.0))
        )
        for (positions in cases) {
            val way = way(1, positions.indices.map { it + 1L })
            assertEquals(failure { originalSimple(way, positions) }, failure { checkSimpleCrossingWay(way, positions) })
        }
    }

    private fun failure(block: () -> Unit): String? = try { block(); null } catch (e: ConflictException) { e.message }

    private fun originalPoints(a: List<LatLon>, b: List<LatLon>) = a.zipWithNext().flatMap { (p, q) ->
        b.zipWithNext().mapNotNull { (r, s) -> when {
            p == r || p == s -> p
            q == r || q == s -> q
            else -> intersectionOf(p, q, r, s)
        } }
    }

    private fun originalOverlap(a: List<LatLon>, b: List<LatLon>) = a.zipWithNext().any { (p, q) ->
        b.zipWithNext().any { (r, s) ->
            val shared = listOf(p, q).filter { it.distanceToArc(r, s) < 0.01 } +
                listOf(r, s).filter { it.distanceToArc(p, q) < 0.01 }
            shared.any { x -> shared.any { y -> x.distanceTo(y) > 0.01 } }
        }
    }

    private fun originalSimple(way: Way, positions: List<LatLon>) {
        crossingCheck(!way.isClosed && way.nodeIds.size >= 2 && way.nodeIds.size == positions.size
            && way.nodeIds.distinct().size == way.nodeIds.size && positions.distinct().size == positions.size,
            "Closed or ambiguous way")
        crossingCheck(positions.zipWithNext().all { (a, b) -> a.distanceTo(b) > 0.01 }, "Degenerate segment")
        val segments = positions.zipWithNext()
        for (i in segments.indices) for (j in i + 2 until segments.size) {
            crossingCheck(intersectionOf(segments[i].first, segments[i].second, segments[j].first, segments[j].second) == null,
                "Self-intersecting way")
        }
        for (i in 1 until positions.lastIndex) {
            crossingCheck(positions[i - 1].distanceToArc(positions[i], positions[i + 1]) > 0.01
                && positions[i + 1].distanceToArc(positions[i - 1], positions[i]) > 0.01,
                "Overlapping way segments")
        }
    }
}
