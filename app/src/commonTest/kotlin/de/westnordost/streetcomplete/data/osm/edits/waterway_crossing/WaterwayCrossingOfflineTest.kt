package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.*
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtLinePosition
import de.westnordost.streetcomplete.testutils.*
import kotlinx.serialization.json.Json
import kotlin.math.round

class WaterwayCrossingOfflineTest {
    @Test fun `shared ford serializes replays offline and undo restores untagged vertex`() {
        val f = WaterwayCrossingTestData()
        val c = f.sharedCrossing()
        val action = Json.decodeFromString<CreateWaterwayFordAction>(Json.encodeToString(CreateWaterwayFordAction(c)))
        var pending = listOf(edit(action = action))
        lateinit var listener: ElementEditsSource.Listener
        val edits = mock<ElementEditsSource> {
            every { getAllUnsynced() } calls { pending }
            every { getIdProvider(any()) } calls { ElementIdProvider(emptyList()) }
            every { addListener(any()) } calls { (l: ElementEditsSource.Listener) -> listener = l }
        }
        val local = MapDataWithEditsSourceImpl(f.repo, edits, ElementGeometryCreator())
        assertEquals("yes", local.getNode(7)!!.tags["ford"])
        assertEquals(c.road.nodeIds, local.getWay(10)!!.nodeIds)
        assertEquals(c.waterway.nodeIds, local.getWay(20)!!.nodeIds)
        val removed = pending
        pending = emptyList()
        listener.onDeletedEdits(removed)
        assertTrue(local.getNode(7)!!.tags.isEmpty())
        f.data.putElement(node(7, p(0.00000004, 0.00000004)))
        assertEquals("yes", action.createUpdates(f.repo, ElementIdProvider(emptyList())).modifications.single().tags["ford"])
        f.data.putElement(node(7, p(0.0000002, 0.0)))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, ElementIdProvider(emptyList())) }
    }

    @Test fun `two serialized pending splits replay and second upload accepts first uploads OSM rounding`() {
        val f = WaterwayCrossingTestData()
        val base = WaterwayCrossingTestData()
        for (fixture in listOf(f, base)) {
            fixture.data.putElement(node(7, p(-0.002, -0.001)))
            fixture.data.putElement(node(8, p(0.002, -0.001)))
            fixture.data.putElement(way(30, listOf(7, 8), mapOf("waterway" to "ditch")))
        }
        val first = Json.decodeFromString<SetWaterwayStructureAction>(Json.encodeToString(f.bridge.copy(splits = listOf(
            SplitAtLinePosition(f.roadPositions[1], f.roadPositions[2], 1.0 / 3),
            SplitAtLinePosition(f.roadPositions[1], f.roadPositions[2], 2.0 / 3),
        ))))
        val changes = first.createUpdates(f.repo, f.idProvider())
        (changes.creations + changes.modifications).forEach { f.data.putElement(it) }
        val road = f.data.getWay(10)!!
        val water = f.data.getWay(30)!!
        val rp = road.nodeIds.map { f.data.getNode(it)!!.position }
        val wp = water.nodeIds.map { f.data.getNode(it)!!.position }
        val second = Json.decodeFromString<SetWaterwayStructureAction>(Json.encodeToString(
            SetWaterwayStructureAction(WaterwayCrossing(road, water, rp, wp, p(0.0, -0.001)), WaterwayStructure.BRIDGE,
                listOf(SplitAtLinePosition(rp[0], rp[1], 0.45), SplitAtLinePosition(rp[0], rp[1], 0.65)))))
        val secondIds = listOf(ElementKey(ElementType.NODE, -1003), ElementKey(ElementType.NODE, -1004),
            ElementKey(ElementType.WAY, -2003), ElementKey(ElementType.WAY, -2004))
        val secondChanges = second.createUpdates(f.repo, ElementIdProvider(secondIds))
        val middle = (secondChanges.creations + secondChanges.modifications).filterIsInstance<Way>().single { "bridge" in it.tags }
        var pending = listOf(edit(id = 1, action = first), edit(id = 2, action = second))
        val edits = mock<ElementEditsSource> {
            every { getAllUnsynced() } calls { pending }
            every { getIdProvider(any()) } calls { (id: Long) -> if (id == 1L) f.idProvider() else ElementIdProvider(secondIds) }
        }
        val offline = MapDataWithEditsSourceImpl(base.repo, edits, ElementGeometryCreator())
        assertEquals(middle.nodeIds, offline.getWay(middle.id)!!.nodeIds)
        assertEquals("yes", offline.getWay(middle.id)!!.tags["bridge"])

        val newIds = mapOf(ElementKey(ElementType.NODE, -1001) to 101L, ElementKey(ElementType.NODE, -1002) to 102L,
            ElementKey(ElementType.WAY, -2001) to 201L, ElementKey(ElementType.WAY, -2002) to 202L)
        for (element in changes.creations + changes.modifications) base.data.putElement(when (element) {
            is Node -> element.copy(id = newIds[element.key] ?: element.id,
                position = LatLon(round(element.position.latitude * 1e7) / 1e7, round(element.position.longitude * 1e7) / 1e7))
            is Way -> element.copy(id = newIds[element.key] ?: element.id,
                nodeIds = element.nodeIds.map { newIds[ElementKey(ElementType.NODE, it)] ?: it })
            is Relation -> element
        })
        val uploadAction = second.idsUpdatesApplied(newIds)
        assertNotEquals(uploadAction.crossing.roadPositions.last(), base.data.getNode(101)!!.position)
        assertEquals(3, (uploadAction.createUpdates(base.repo, ElementIdProvider(secondIds)).let { it.creations + it.modifications })
            .filterIsInstance<Way>().size)
        pending = listOf(edit(id = 2, action = uploadAction))
        val resumed = MapDataWithEditsSourceImpl(base.repo, edits, ElementGeometryCreator())
        assertEquals("yes", resumed.getWay(middle.id)!!.tags["bridge"])
        base.data.putElement(base.data.getNode(101)!!.copy(position = base.data.getNode(101)!!.position.let {
            LatLon(it.latitude + 2e-7, it.longitude)
        }))
        assertFailsWith<ConflictException> { uploadAction.createUpdates(base.repo, ElementIdProvider(secondIds)) }
    }

    @Test fun `rebuilding pending structure generates geometry and undo removes the entire edit`() {
        val f = WaterwayCrossingTestData()
        var pending = listOf(edit(action = f.bridge))
        lateinit var listener: ElementEditsSource.Listener
        val edits = mock<ElementEditsSource> {
            every { getAllUnsynced() } calls { pending }
            every { getIdProvider(any()) } calls { f.idProvider() }
            every { addListener(any()) } calls { (l: ElementEditsSource.Listener) -> listener = l }
        }
        val local = MapDataWithEditsSourceImpl(f.repo, edits, ElementGeometryCreator())
        val expected = f.bridge.createUpdates(f.repo, f.idProvider())
        val middle = (expected.creations + expected.modifications).filterIsInstance<Way>().single { "bridge" in it.tags }
        assertEquals(middle.nodeIds, local.getWay(middle.id)!!.nodeIds)
        assertEquals("yes", local.getWay(middle.id)!!.tags["bridge"])
        assertNotNull(local.getGeometry(ElementType.WAY, middle.id))
        for (node in expected.creations.filterIsInstance<Node>()) assertNotNull(local.getNode(node.id))
        val removed = pending
        pending = emptyList()
        listener.onDeletedEdits(removed)
        assertEquals(f.road, local.getWay(f.road.id))
        for (element in expected.creations) assertNull(local.get(element.type, element.id))
        val restarted = MapDataWithEditsSourceImpl(f.repo, edits, ElementGeometryCreator())
        assertEquals(f.road, restarted.getWay(f.road.id))
    }

    @Test fun `rebuilding pending ford keeps shared node and both geometries`() {
        val f = WaterwayCrossingTestData()
        val action = CreateWaterwayFordAction(f.crossing)
        val edits = mock<ElementEditsSource> {
            every { getAllUnsynced() } returns listOf(edit(action = action))
            every { getIdProvider(any()) } calls { f.idProvider() }
        }
        val local = MapDataWithEditsSourceImpl(f.repo, edits, ElementGeometryCreator())
        val node = action.createUpdates(f.repo, f.idProvider()).creations.single() as Node
        assertEquals("yes", local.getNode(node.id)!!.tags["ford"])
        assertTrue(node.id in local.getWay(f.road.id)!!.nodeIds)
        assertTrue(node.id in local.getWay(f.water.id)!!.nodeIds)
        assertNotNull(local.getGeometry(ElementType.WAY, f.road.id))
        assertNotNull(local.getGeometry(ElementType.WAY, f.water.id))
    }
}
