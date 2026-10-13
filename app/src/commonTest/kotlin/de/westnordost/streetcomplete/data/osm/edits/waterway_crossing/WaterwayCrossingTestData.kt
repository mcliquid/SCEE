package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtLinePosition
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.*
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock

class WaterwayCrossingTestData {
    val roadPositions = listOf(p(0.0, -0.002), p(0.0, -0.0002), p(0.0, 0.0002), p(0.0, 0.002))
    val waterPositions = listOf(p(-0.002, 0.0), p(0.002, 0.0))
    val road = way(10, listOf(1, 2, 3, 4), mapOf("highway" to "residential", "name" to "Road"), timestamp = 0)
    val water = way(20, listOf(5, 6), mapOf("waterway" to "stream"), timestamp = 0)
    val crossing = WaterwayCrossing(road, water, roadPositions, waterPositions, p())
    val data = MutableMapDataWithGeometry().also { data ->
        val creator = ElementGeometryCreator()
        val elements = roadPositions.mapIndexed { i, pos -> node(i + 1L, pos, timestamp = 0) } +
            waterPositions.mapIndexed { i, pos -> node(i + 5L, pos, timestamp = 0) } + listOf(road, water)
        for (element in elements) data.put(element, creator.create(element, MutableMapData(elements)))
    }
    val splits = listOf(SplitAtLinePosition(roadPositions[1], roadPositions[2], 0.25),
        SplitAtLinePosition(roadPositions[1], roadPositions[2], 0.75))
    val bridge = SetWaterwayStructureAction(crossing, WaterwayStructure.BRIDGE, splits)
    val culvert = SetWaterwayStructureAction(crossing, WaterwayStructure.CULVERT,
        listOf(SplitAtLinePosition(waterPositions[0], waterPositions[1], 0.4),
            SplitAtLinePosition(waterPositions[0], waterPositions[1], 0.6)))
    val ids = listOf(ElementKey(ElementType.NODE, -1001), ElementKey(ElementType.NODE, -1002),
        ElementKey(ElementType.WAY, -2001), ElementKey(ElementType.WAY, -2002))
    fun idProvider() = ElementIdProvider(ids)
    fun sharedCrossing(): WaterwayCrossing {
        val road = road.copy(nodeIds = listOf(1, 2, 7, 3, 4))
        val water = water.copy(nodeIds = listOf(5, 7, 6))
        data.putElement(node(7, p(), timestamp = 0))
        for (way in listOf(road, water)) data.put(way, ElementGeometryCreator().create(way, data))
        return WaterwayCrossing(road, water, road.nodeIds.map { data.getNode(it)!!.position },
            water.nodeIds.map { data.getNode(it)!!.position }, p())
    }

    val repo: MapDataSource = mock {
        every { getNode(any()) } calls { (id: Long) -> data.getNode(id) }
        every { getWay(any()) } calls { (id: Long) -> data.getWay(id) }
        every { getRelation(any()) } calls { (id: Long) -> data.getRelation(id) }
        every { get(any(), any()) } calls { (type: ElementType, id: Long) -> when (type) {
            ElementType.NODE -> data.getNode(id)
            ElementType.WAY -> data.getWay(id)
            ElementType.RELATION -> data.getRelation(id)
        } }
        every { getNodes(any()) } calls { (ids: Collection<Long>) -> ids.mapNotNull { data.getNode(it) } }
        every { getWayComplete(any()) } calls { (id: Long) -> data.getWay(id)?.let { way ->
            MutableMapData(listOf(way) + way.nodeIds.mapNotNull { data.getNode(it) })
        } }
        every { getWaysForNode(any()) } calls { (id: Long) -> data.ways.filter { id in it.nodeIds } }
        every { getRelationsForNode(any()) } calls { (id: Long) -> data.relations.filter { relation ->
            relation.members.any { it.type == ElementType.NODE && it.ref == id }
        } }
        every { getRelationsForWay(any()) } calls { (id: Long) -> data.relations.filter { relation ->
            relation.members.any { it.type == ElementType.WAY && it.ref == id }
        } }
        every { getRelationsForElement(any(), any()) } calls { (type: ElementType, id: Long) ->
            data.relations.filter { relation -> relation.members.any { it.type == type && it.ref == id } }
        }
    }
}
