package de.westnordost.streetcomplete.quests.waterway_crossing

import de.westnordost.streetcomplete.data.osm.osmquests.crossingAreaGroups
import de.westnordost.streetcomplete.data.osm.osmquests.crossingTestCache
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.member
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import com.sun.management.ThreadMXBean
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import java.lang.management.ManagementFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

/** Cold detection on complete, long ways; timings are diagnostic, never pass/fail thresholds. */
class WaterwayCrossingPerformanceTest {
    @Test fun `measure 1091 distinct overlapping reads versus bounded exact union reads`() {
        val world = MutableMapDataWithGeometry()
        repeat(10000) { i -> world.putElement(node(i + 1L, LatLon(47.0 + (i / 100) * 0.0001, 9.0 + (i % 100) * 0.0001))) }
        val creator = ElementGeometryCreator()
        repeat(100) { i ->
            val w = way(i + 1L, (1L..100L).map { it + i * 100 }, mapOf("highway" to "service"))
            world.put(w, creator.create(w, world))
        }
        repeat(89) { i -> world.putElement(rel(i + 1L, listOf(member(ElementType.WAY, i + 1L)))) }
        val areas = (0 until 1091).map { i ->
            val d = i * 0.0000001
            BoundingBox(47.0 + d, 9.0 + d, 47.006 + d, 9.007 + d)
        }
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        fun allocated() = if (bean?.isThreadAllocatedMemoryEnabled == true)
            bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else 0L
        data class Sample(val ms: Double, val bytes: Long, val queries: Int, val materialized: Long, val largest: Int, val result: MutableMapData)
        fun measure(bulk: Boolean): Sample {
            val cache = crossingTestCache(world, warm = true)
            var queries = 0
            var materialized = 0L
            var largest = 0
            val result = MutableMapData()
            val bytes = allocated()
            val start = System.nanoTime()
            if (bulk) {
                for (group in crossingAreaGroups(areas)) {
                    val data = cache.getMapDataForAreas(group)
                    queries++
                    materialized += data.size
                    largest = maxOf(largest, data.size)
                    result.addAll(data)
                }
            } else for (area in areas) {
                val data = cache.getMapDataWithGeometry(area)
                queries++
                materialized += data.size
                largest = maxOf(largest, data.size)
                result.addAll(data)
            }
            return Sample((System.nanoTime() - start) / 1_000_000.0, allocated() - bytes, queries, materialized, largest, result)
        }
        measure(false); measure(true)
        val before = (0 until 3).map { measure(false) }
        val after = (0 until 3).map { measure(true) }
        before.forEach { old -> after.forEach { new -> assertEquals(old.result.toSet(), new.result.toSet()) } }
        assertEquals(AddWaterwayCrossing().getCrossings(before.first().result), AddWaterwayCrossing().getCrossings(after.first().result))
        fun report(samples: List<Sample>) = "queries=${samples.first().queries} materialized=${samples.first().materialized} " +
            "medianMs=${samples.map { it.ms }.sorted()[1]} medianBytes=${samples.map { it.bytes }.sorted()[1]} " +
            "maxBatch=${samples.maxOf { it.largest }}"
        println("CROSSING_PERF exactAreas allocationSupported=${bean?.isThreadAllocatedMemoryEnabled == true} BEFORE ${report(before)} AFTER ${report(after)}")
    }

    @Test fun `measure overlapping area merge without unused geometry maps`() {
        val area = MutableMapDataWithGeometry()
        repeat(10000) { i ->
            val position = LatLon(47.0 + i * 0.000001, 9.0)
            area.put(node(i + 1L, position), ElementPointGeometry(position))
        }
        repeat(100) { i -> area.putElement(way(i + 1L, (1L..100L).map { it + i * 100 }, mapOf("highway" to "service"))) }
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        fun allocated() = if (bean?.isThreadAllocatedMemoryEnabled == true)
            bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else 0L
        fun measure(geometries: Boolean): Pair<Double, Long> {
            val bytes = allocated()
            val start = System.nanoTime()
            if (geometries) {
                val merged = MutableMapDataWithGeometry()
                repeat(40) { merged.putAll(area) }
                assertEquals(area.nodes.size, merged.nodes.size)
            } else {
                val merged = MutableMapData()
                repeat(40) { merged.addAll(area) }
                assertEquals(area.nodes.size, merged.nodes.size)
            }
            return (System.nanoTime() - start) / 1_000_000.0 to (allocated() - bytes)
        }
        repeat(2) { measure(true); measure(false) }
        val before = (0 until 3).map { measure(true) }
        val after = (0 until 3).map { measure(false) }
        println("CROSSING_PERF merge 40x10100 elements: beforeMs=${before.map { it.first }.sorted()[1]} " +
            "afterMs=${after.map { it.first }.sorted()[1]} beforeBytes=${before.map { it.second }.sorted()[1]} " +
            "afterBytes=${after.map { it.second }.sorted()[1]}")
    }

    @Test fun `parallel identical inputs share detection and edits invalidate that result`() {
        val fixture = WaterwayCrossingTestData()
        val reads = AtomicInteger()
        val data = object : MapDataWithGeometry by fixture.data {
            override fun getNode(id: Long): Node? {
                reads.incrementAndGet()
                return fixture.data.getNode(id)
            }
        }
        val type = AddWaterwayCrossing()
        val expected = AddWaterwayCrossing().getCrossings(data)
        val singleRunReads = reads.get()
        reads.set(0)
        val gate = CountDownLatch(1)
        val results = arrayOfNulls<List<AddWaterwayCrossing.Candidate>>(8)
        val threads = results.indices.map { i -> thread {
            gate.await()
            results[i] = type.getCrossings(data)
        } }
        gate.countDown()
        threads.forEach { it.join() }
        results.forEach { assertEquals(expected, it) }
        val once = reads.get()
        assertEquals(singleRunReads, once)
        assertEquals(expected, type.getCrossings(data))
        assertEquals(once, reads.get())
        fixture.data.putElement(fixture.road.copy(tags = fixture.road.tags + ("bridge" to "yes")))
        assertEquals(emptyList(), type.getCrossings(data))
        fixture.data.putElement(fixture.road)
        assertEquals(expected, type.getCrossings(data))
    }

    @Test fun `measure cold detection with spatially separate long ways`() {
        val data = MutableMapDataWithGeometry()
        var nextNode = 1L
        repeat(80) { road ->
            val ids = (0 until 160).map { vertex ->
                val id = nextNode++
                data.putElement(node(id, LatLon(47.0 + road * 0.0002, 9.0 + vertex * 0.0001)))
                id
            }
            data.putElement(way(road + 1L, ids, mapOf("highway" to "residential")))
        }
        val a = nextNode++
        val b = nextNode
        data.putElement(node(a, LatLon(47.1, 9.0)))
        data.putElement(node(b, LatLon(47.2, 9.0)))
        data.putElement(way(1000, listOf(a, b), mapOf("waterway" to "stream")))
        val bean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (bean?.isThreadAllocatedMemorySupported == true) bean.isThreadAllocatedMemoryEnabled = true
        fun allocated() = if (bean?.isThreadAllocatedMemoryEnabled == true)
            bean.getThreadAllocatedBytes(Thread.currentThread().threadId()) else 0L
        repeat(2) { assertEquals(0, AddWaterwayCrossing().getCrossings(data).size) }
        val samples = (0 until 3).map {
            val bytes = allocated()
            val start = System.nanoTime()
            assertEquals(0, AddWaterwayCrossing().getCrossings(data).size)
            (System.nanoTime() - start) / 1_000_000.0 to (allocated() - bytes)
        }
        println("CROSSING_PERF cold 80x160 vertices: medianMs=${samples.map { it.first }.sorted()[1]} " +
            "medianAllocatedBytes=${samples.map { it.second }.sorted()[1]}")
    }
}
