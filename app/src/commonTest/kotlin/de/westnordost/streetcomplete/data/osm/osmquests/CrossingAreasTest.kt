package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.download.tiles.enclosingTilePos
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryEntry
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import kotlin.test.*

/** Uses the same node/way/relation closure as ElementDao.getAll, not a bbox-only mock. */
internal fun crossingTestCache(world: MapDataWithGeometry, warm: Boolean = false, maxTiles: Int = 192): MapDataCache {
    val cache = MapDataCache(16, maxTiles, world.size, { bbox ->
        val selected = world.nodes.filter { it.position in bbox }.map { it.id }.toSet()
        val ways = world.ways.filter { it.nodeIds.any { id -> id in selected } }
        val nodes = selected + ways.flatMap { it.nodeIds }
        val waysIds = ways.map { it.id }.toSet()
        val relations = world.relations.filter { relation -> relation.members.any {
            it.type == ElementType.NODE && it.ref in nodes || it.type == ElementType.WAY && it.ref in waysIds
        } }
        val elements = world.nodes.filter { it.id in nodes } + ways + relations
        elements to elements.mapNotNull { element -> world.getGeometry(element.type, element.id)?.let {
            ElementGeometryEntry(element.type, element.id, it)
        } }
    }, { ids -> ids.mapNotNull(world::getNode) })
    if (warm && world.nodes.isNotEmpty()) cache.update(updatedElements = world,
        updatedGeometries = world.mapNotNull { e -> world.getGeometry(e.type, e.id)?.let { ElementGeometryEntry(e.type, e.id, it) } },
        bbox = world.nodes.map { it.position }.enclosingBoundingBox())
    return cache
}

internal fun legacyCrossingAreas(cache: MapDataCache, areas: List<BoundingBox>): MutableMapData =
    MutableMapData().apply { areas.forEach { addAll(cache.getMapDataWithGeometry(it)) } }

internal fun groupedCrossingAreas(cache: MapDataCache, areas: List<BoundingBox>): MutableMapData =
    MutableMapData().apply { crossingAreaGroups(areas).forEach { addAll(cache.getMapDataForAreas(it)) } }

class CrossingAreasTest {
    @Test fun `holes boundaries long way nodes and parent relations match individual reads cold and warm`() {
        val f = WaterwayCrossingTestData()
        val data = f.data
        data.putElement(node(8, p(0.0015, 0.0015))) // hole inside enclosing rectangle
        data.putElement(node(9, p(0.0016, 0.0015)))
        data.putElement(way(40, listOf(8, 9), mapOf("highway" to "service")))
        data.putElement(rel(50, listOf(member(ElementType.WAY, 40))))
        data.putElement(rel(51, listOf(member(ElementType.WAY, f.road.id))))
        data.putElement(rel(52, listOf(member(ElementType.NODE, 1))))
        val creator = ElementGeometryCreator()
        data.ways.toList().forEach { data.put(it, creator.create(it, data)) }
        val areas = listOf(BoundingBox(-0.0001, -0.0002, 0.0001, 0.0002),
            BoundingBox(0.002, 0.0, 0.0021, 0.002))
        for (warm in listOf(false, true)) {
            val expected = legacyCrossingAreas(crossingTestCache(data, warm), areas)
            val actual = groupedCrossingAreas(crossingTestCache(data, warm), areas)
            assertEquals(expected.toSet(), actual.toSet())
            assertNotNull(actual.getNode(1)) // complete road, outside every area
            assertNotNull(actual.getRelation(51))
            assertNull(actual.getWay(40))
            assertNull(actual.getRelation(50))
            assertEquals(AddWaterwayCrossing().getCrossings(expected), AddWaterwayCrossing().getCrossings(actual))
        }
    }

    @Test fun `incomplete geometry and cache eviction preserve the original results`() {
        val f = WaterwayCrossingTestData()
        f.data.remove(ElementType.NODE, 4)
        val areas = listOf(BoundingBox(-0.001, -0.001, 0.001, 0.001), BoundingBox(-0.0009, -0.0008, 0.0011, 0.0012))
        val expected = legacyCrossingAreas(crossingTestCache(f.data, maxTiles = 4), areas)
        val actual = groupedCrossingAreas(crossingTestCache(f.data, maxTiles = 4), areas)
        assertEquals(expected.toSet(), actual.toSet())
        assertEquals(AddWaterwayCrossing().getCrossings(expected), AddWaterwayCrossing().getCrossings(actual))
    }

    @Test fun `tile aligned cold reads preserve existing fast path including relations of outside nodes`() {
        val f = WaterwayCrossingTestData()
        f.data.putElement(rel(50, listOf(member(ElementType.NODE, 4))))
        val tile = p().enclosingTilePos(16).asBoundingBox(16)
        val areas = listOf(tile, BoundingBox(-0.001, -0.001, 0.001, 0.001))
        assertEquals(legacyCrossingAreas(crossingTestCache(f.data), areas).toSet(),
            groupedCrossingAreas(crossingTestCache(f.data), areas).toSet())
    }

    @Test fun `fresh reads respect updates deletes structure tags and relation invalidation`() {
        val f = WaterwayCrossingTestData()
        val areas = listOf(BoundingBox(-0.0021, -0.001, 0.001, 0.001), BoundingBox(-0.0009, -0.0008, 0.0011, 0.0012))
        val cache = crossingTestCache(f.data, warm = true)
        assertEquals(1, AddWaterwayCrossing().getCrossings(groupedCrossingAreas(cache, areas)).size)
        val changed = f.road.copy(tags = f.road.tags + ("bridge" to "yes"))
        cache.update(updatedElements = listOf(changed))
        assertEquals(legacyCrossingAreas(cache, areas).toSet(), groupedCrossingAreas(cache, areas).toSet())
        assertTrue(AddWaterwayCrossing().getCrossings(groupedCrossingAreas(cache, areas)).isEmpty())
        cache.update(updatedElements = listOf(f.road, rel(50, listOf(member(ElementType.WAY, f.road.id)))))
        assertEquals(legacyCrossingAreas(cache, areas).toSet(), groupedCrossingAreas(cache, areas).toSet())
        cache.update(deletedKeys = listOf(ElementKey(ElementType.WAY, f.water.id), ElementKey(ElementType.RELATION, 50)))
        assertEquals(legacyCrossingAreas(cache, areas).toSet(), groupedCrossingAreas(cache, areas).toSet())
    }

    @Test fun `grouping bounds every expanded read and leaves large dateline and polar anchors unchanged`() {
        val normal = (0..150).map { i -> BoundingBox(47.0, 9.0 + i * 0.000001, 47.005, 9.005 + i * 0.000001) }
        val exceptional = listOf(BoundingBox(47.0, 9.0, 48.0, 10.0), BoundingBox(0.0, 179.99, 0.001, -179.99),
            BoundingBox(0.0, -179.999, 0.001, -179.99), BoundingBox(0.0, 179.99, 0.001, 179.999),
            BoundingBox(85.0, 9.0, 85.001, 10.0))
        val groups = crossingAreaGroups(normal + exceptional)
        assertEquals((normal + exceptional).toSet(), groups.flatten().toSet())
        assertTrue(groups.all { it.size <= 64 })
        exceptional.forEach { assertTrue(listOf(it) in groups) }
        assertEquals(3, crossingAreaGroups(normal).size)
    }
}
