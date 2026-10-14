package de.westnordost.streetcomplete.quests.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.*
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.*
import kotlin.test.*
import de.westnordost.streetcomplete.util.math.distanceTo

class AddWaterwayCrossingTest {
    private val type = AddWaterwayCrossing()

    @Test fun `element-only detection is identical and retains relation and context exclusions`() {
        val f = WaterwayCrossingTestData()
        assertEquals(type.getCrossings(f.data), type.getCrossings(MutableMapData(f.data)))
        val shared = f.sharedCrossing()
        f.data.putElement(rel(30, listOf(member(ElementType.NODE, shared.road.nodeIds[2]))))
        assertTrue(type.getCrossings(MutableMapData(f.data)).isEmpty())
        f.data.remove(ElementType.RELATION, 30)
        assertTrue(type.getCrossings(MutableMapData(f.data)).isEmpty())
        f.data.putElement(way(40, listOf(999, 1000), mapOf("highway" to "service")))
        assertTrue(type.getCrossings(MutableMapData(f.data)).isEmpty())
    }

    @Test fun `unchanged geometry and irrelevant tags reuse detection but return current metadata`() {
        val f = WaterwayCrossingTestData()
        var nodeReads = 0
        val data = object : MapDataWithGeometry by f.data {
            override fun getNode(id: Long): Node? { nodeReads++; return f.data.getNode(id) }
        }
        type.getCrossings(data)
        val firstReads = nodeReads
        assertTrue(firstReads > 0)
        type.getCrossings(data)
        assertEquals(firstReads, nodeReads)
        val renamed = f.road.copy(tags = f.road.tags + ("name" to "New name"))
        f.data.putElement(renamed)
        assertEquals(renamed, type.getCrossings(data).single().road)
        assertEquals(firstReads, nodeReads)
    }

    @Test fun `shared endpoints touches and collinear overlaps are excluded identically`() {
        for (kind in listOf("shared", "touch", "overlap", "structure")) {
            val f = WaterwayCrossingTestData()
            when (kind) {
                "shared" -> f.data.putElement(f.water.copy(nodeIds = listOf(1, 6)))
                "touch" -> f.data.putElement(node(5, p()))
                "overlap" -> {
                    f.data.putElement(node(5, p(0.0, -0.001)))
                    f.data.putElement(node(6, p(0.0, 0.001)))
                }
                "structure" -> f.data.putElement(f.road.copy(tags = f.road.tags + ("bridge" to "yes")))
            }
            assertTrue(type.getCrossings(f.data).isEmpty(), kind)
            assertTrue(findWaterwayCrossings(f.data, p(), 100.0).isEmpty(), kind)
        }
    }

    @Test fun `each crossing has its own tile independent pin`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(node(7, p(-0.001, 0.001)))
        f.data.putElement(node(8, p(0.001, 0.001)))
        f.data.putElement(way(30, listOf(7, 8), mapOf("waterway" to "ditch")))
        val bbox = BoundingBox(-0.0001, 0.0009, 0.0001, 0.0011)
        assertEquals(30L, type.getCrossings(f.data, bbox).single().waterway.id)
        assertEquals(setOf(20L, 30L), type.getCrossings(f.data).map { it.waterway.id }.toSet())
    }

    @Test fun `incomplete surrounding way is never silently omitted from edit snapshot`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(way(30, listOf(999, 1000), mapOf("highway" to "service")))
        assertTrue(findWaterwayCrossings(f.data, p(), 1.0).isEmpty())
    }

    @Test fun `detect unconnected road and waterway without an existing crossing node`() {
        val f = WaterwayCrossingTestData()
        val crossing = type.getCrossings(f.data).single()
        assertEquals(f.road, crossing.road)
        assertEquals(f.water, crossing.waterway)
        assertEquals(p(), crossing.position)
        assertEquals(listOf(f.road), type.getApplicableElements(f.data).toList())
        assertNull(type.isApplicableTo(f.road))
        assertFalse(type.isApplicableTo(f.water)!!)
    }

    @Test fun `motorway trunk and link roads can be described but never become simple fords`() {
        for (highway in listOf("motorway", "trunk", "primary_link", "trunk_link", "motorway_link")) {
            val f = WaterwayCrossingTestData()
            val road = f.road.copy(tags = mapOf("highway" to highway))
            f.data.putElement(road)
            assertEquals(road, type.getCrossings(f.data).single().road)
            assertFailsWith<ConflictException> {
                CreateWaterwayFordAction(f.crossing.copy(road = road)).createUpdates(f.repo, f.idProvider())
            }
            val changes = f.bridge.copy(crossing = f.crossing.copy(road = road)).createUpdates(f.repo, f.idProvider())
            assertEquals("1", (changes.creations + changes.modifications).filterIsInstance<Way>()
                .single { it.tags["bridge"] == "yes" }.tags["layer"])
        }
    }

    @Test fun `any existing structure or elevation tag excludes the automatic quest`() {
        for ((key, value) in listOf("bridge" to "yes", "bridge" to "viaduct", "tunnel" to "culvert",
            "tunnel" to "yes", "covered" to "yes", "ford" to "yes", "layer" to "1", "layer" to "0",
            "bridge" to "no", "level" to "1", "location" to "underground")) {
            for (onRoad in listOf(false, true)) {
                val f = WaterwayCrossingTestData()
                val way = if (onRoad) f.road else f.water
                f.data.putElement(way.copy(tags = way.tags + (key to value)))
                assertTrue(type.getCrossings(f.data).isEmpty(), "$key=$value on ${way.id}")
            }
        }
    }

    @Test fun `shared crossing vertex at T junction is excluded because only ford would remain`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(node(7, p()))
        val road = f.road.copy(nodeIds = listOf(1, 2, 7, 3, 4))
        val water = f.water.copy(nodeIds = listOf(5, 7, 6))
        f.data.putElement(road); f.data.putElement(water)
        assertTrue(type.getCrossings(f.data).isEmpty())
        assertTrue(findWaterwayCrossings(f.data, p(), 1.0).isEmpty())
        f.data.putElement(node(7, p(), tags = mapOf("ford" to "yes")))
        assertTrue(type.getCrossings(f.data).isEmpty())
    }

    @Test fun `unconnected crossing still offers bridge and culvert`() {
        val f = WaterwayCrossingTestData()
        val crossing = type.getCrossings(f.data).single()
        assertTrue(crossingSupportsBridgeOrCulvert(crossing.road, crossing.waterway, f.roadPositions, crossing.position))
    }

    @Test fun `shared endpoint tangent major roads node tags and additional references have no answers`() {
        for (kind in listOf("endpoint", "touch", "major", "tag", "layer", "way", "relation")) {
            val f = WaterwayCrossingTestData()
            val c = f.sharedCrossing()
            when (kind) {
                "endpoint" -> f.data.putElement(c.waterway.copy(nodeIds = listOf(7, 6)))
                "touch" -> f.data.putElement(node(5, p(0.001, -0.001)))
                "major" -> f.data.putElement(c.road.copy(tags = mapOf("highway" to "primary")))
                "tag" -> f.data.putElement(node(7, p(), tags = mapOf("barrier" to "gate")))
                "layer" -> f.data.putElement(c.waterway.copy(tags = c.waterway.tags + ("layer" to "-1")))
                "way" -> f.data.putElement(way(30, listOf(7, 1), mapOf("boundary" to "administrative")))
                "relation" -> f.data.putElement(rel(30, listOf(member(ElementType.NODE, 7))))
            }
            assertTrue(type.getCrossings(f.data).isEmpty(), kind)
            assertTrue(findWaterwayCrossings(f.data, p(), 1.0).isEmpty(), kind)
        }
    }

    @Test fun `complete long segments with all vertices outside tile still produce stable crossing`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(f.road.copy(nodeIds = listOf(1, 4)))
        val tile = BoundingBox(-0.00001, -0.00001, 0.00001, 0.00001)
        val first = type.getCrossings(f.data, tile).single()
        assertEquals(10L, first.road.id)
        assertEquals(first.position, type.getCrossings(f.data, BoundingBox(-0.00002, -0.00002, 0.00002, 0.00002)).single().position)
    }

    @Test fun `incomplete closed and self intersecting ways are excluded`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(f.road.copy(nodeIds = listOf(1, 2, 999, 3, 4)))
        assertTrue(type.getCrossings(f.data).isEmpty())
        f.data.putElement(f.road.copy(nodeIds = listOf(1, 2, 3, 4, 1)))
        assertTrue(type.getCrossings(f.data).isEmpty())
        val positions = listOf(p(-0.001, -0.001), p(0.001, 0.001), p(0.001, -0.001), p(-0.001, 0.001))
        positions.forEachIndexed { i, position -> f.data.putElement(node(i + 1L, position)) }
        f.data.putElement(f.road)
        assertTrue(type.getCrossings(f.data).isEmpty())
    }

    @Test fun `near misses do not create quests`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(node(5, p(0.00001, 0.0)))
        assertTrue(type.getCrossings(f.data).isEmpty())
    }

    @Test fun `multiple safe intersections of the same pair are independent`() {
        val f = WaterwayCrossingTestData()
        listOf(p(-0.001, -0.001), p(0.001, -0.001), p(0.001, 0.001), p(-0.001, 0.001))
            .forEachIndexed { i, pos -> f.data.putElement(node(i + 1L, pos)) }
        f.data.putElement(node(5, p(0.0, -0.002))); f.data.putElement(node(6, p(0.0, 0.002)))
        assertEquals(2, type.getCrossings(f.data).size)
    }

    @Test fun `multiple quests per road have deterministic positions`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(node(7, p(-0.001, 0.001))); f.data.putElement(node(8, p(0.001, 0.001)))
        f.data.putElement(way(30, listOf(7, 8), mapOf("waterway" to "ditch")))
        val candidates = type.getCrossings(f.data)
        assertEquals(2, candidates.size)
        val reversed = MutableMapDataWithGeometry(f.data.toList().reversed(), emptyList())
        assertEquals(candidates.toSet(), type.getCrossings(reversed).toSet())
    }

    @Test fun `bridge culvert and ford each resolve automatic detection`() {
        for (action in listOf("bridge", "culvert", "ford")) {
            val f = WaterwayCrossingTestData()
            val edit = when (action) {
                "bridge" -> f.bridge
                "culvert" -> f.culvert
                else -> CreateWaterwayFordAction(f.crossing)
            }
            val changes = edit.createUpdates(f.repo, f.idProvider())
            (changes.creations + changes.modifications).forEach { f.data.putElement(it) }
            assertTrue(type.getCrossings(f.data).isEmpty(), action)
        }
    }
}
