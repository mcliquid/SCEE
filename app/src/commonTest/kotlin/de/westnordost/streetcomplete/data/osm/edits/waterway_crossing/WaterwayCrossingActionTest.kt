package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.split_way.*
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.util.math.distanceTo
import kotlin.test.*

class WaterwayCrossingActionTest {
    private val f = WaterwayCrossingTestData()
    private fun MapDataChanges.ways() = (creations + modifications).filterIsInstance<Way>()

    @Test fun `shared ford modifies only existing node and rejects remotely added tags or references`() {
        val f = WaterwayCrossingTestData()
        val c = f.sharedCrossing()
        val action = CreateWaterwayFordAction(c)
        assertEquals(NewElementsCount(0, 0, 0), action.newElementsCount)
        val changes = action.createUpdates(f.repo, ElementIdProvider(emptyList()))
        assertTrue(changes.creations.isEmpty())
        assertTrue(changes.deletions.isEmpty())
        assertEquals(listOf(7L), changes.modifications.map { it.id })
        assertEquals(mapOf("ford" to "yes"), changes.modifications.single().tags)
        assertEquals(c.road, f.data.getWay(10))
        assertEquals(c.waterway, f.data.getWay(20))
        assertFailsWith<ConflictException> {
            SetWaterwayStructureAction(c, WaterwayStructure.BRIDGE, f.splits).createUpdates(f.repo, f.idProvider())
        }
        f.data.putElement(node(7, p(), tags = mapOf("name" to "Existing feature")))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, f.idProvider()) }
        f.data.putElement(node(7, p()))
        f.data.putElement(way(30, listOf(7, 1)))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, f.idProvider()) }
        f.data.remove(ElementType.WAY, 30)
        f.data.putElement(rel(30, listOf(member(ElementType.NODE, 7))))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, f.idProvider()) }
    }

    @Test fun `far context changes and classification edits do not conflict but near geometry changes do`() {
        val positions = listOf(p(-0.0005, 0.0007), p(0.0005, 0.0007), p(0.01, 0.01), p(0.02, 0.02))
        positions.forEachIndexed { i, pos -> f.data.putElement(node(i + 7L, pos)) }
        val other = way(30, listOf(7, 8, 9, 10), mapOf("highway" to "service"))
        f.data.putElement(other)
        val action = f.bridge.copy(crossing = f.crossing.copy(nearbyWays = listOf(CrossingContextWay(other, positions))))
        assertTrue(other.key in action.elementKeys)
        assertFalse(ElementKey(ElementType.NODE, 10) in action.elementKeys)
        f.data.putElement(node(10, p(0.025, 0.025)))
        f.data.putElement(other.copy(tags = mapOf("highway" to "track")))
        action.createUpdates(f.repo, f.idProvider())
        f.data.putElement(node(7, p(-0.0005, 0.0006)))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, f.idProvider()) }
        f.data.putElement(node(7, positions[0]))
        f.data.putElement(node(10, p()))
        assertFailsWith<ConflictException> { action.createUpdates(f.repo, f.idProvider()) }
    }

    @Test fun `temporary context nodes remain dependencies even outside crossing radius`() {
        val positions = listOf(p(0.01, 0.01), p(0.02, 0.02))
        val other = way(-300, listOf(7, -99), mapOf("highway" to "service"))
        val action = f.bridge.copy(crossing = f.crossing.copy(nearbyWays = listOf(CrossingContextWay(other, positions))))
        assertTrue(ElementKey(ElementType.NODE, -99) in action.elementKeys)
        assertEquals(199L, action.idsUpdatesApplied(mapOf(ElementKey(ElementType.NODE, -99) to 199L))
            .crossing.nearbyWays.single().way.nodeIds.last())
    }

    @Test fun `complete responses are reused within an attempt but refreshed on replay`() {
        val requests = mutableMapOf<Long, Int>()
        var nodeRequests = 0
        var relationRequests = 0
        val repository = object : MapDataRepository by f.repo {
            override fun getWayComplete(id: Long): MapData? {
                requests[id] = (requests[id] ?: 0) + 1
                return f.repo.getWayComplete(id)
            }
            override fun getNode(id: Long): Node? {
                nodeRequests++
                return f.repo.getNode(id)
            }
            override fun getRelationsForWay(id: Long): Collection<Relation> {
                relationRequests++
                return f.repo.getRelationsForWay(id)
            }
        }
        f.bridge.createUpdates(repository, f.idProvider())
        assertEquals(mapOf(10L to 1, 20L to 1), requests)
        assertEquals(0, nodeRequests)
        assertEquals(1, relationRequests)
        f.bridge.createUpdates(repository, f.idProvider())
        assertEquals(mapOf(10L to 2, 20L to 2), requests)
        assertEquals(2, relationRequests)
        CreateWaterwayFordAction(f.crossing).createUpdates(repository, f.idProvider())
        assertEquals(mapOf(10L to 3, 20L to 3), requests)
        assertEquals(0, nodeRequests)
    }

    @Test fun `bridge tags only bounded middle even when it has a new id`() {
        val changes = f.bridge.createUpdates(f.repo, f.idProvider())
        val middle = changes.ways().single { it.tags["bridge"] == "yes" }
        assertTrue(middle.id < 0)
        assertEquals("1", middle.tags["layer"])
        assertEquals("Road", middle.tags["name"])
        assertEquals(3, changes.ways().size)
        assertEquals(2, changes.creations.filterIsInstance<Node>().size)
        assertEquals(2, changes.ways().count { "bridge" !in it.tags && "layer" !in it.tags })
        val bounds = changes.creations.filterIsInstance<Node>().map { it.id }.toSet()
        assertEquals(bounds, setOf(middle.nodeIds.first(), middle.nodeIds.last()))
    }

    @Test fun `middle can keep original way id`() {
        val cuts = listOf(SplitAtLinePosition(f.roadPositions[0], f.roadPositions[1], 0.9),
            SplitAtLinePosition(f.roadPositions[2], f.roadPositions[3], 0.1))
        val changes = f.bridge.copy(splits = cuts.reversed()).createUpdates(f.repo, f.idProvider())
        assertEquals(f.road.id, changes.ways().single { "bridge" in it.tags }.id)
    }

    @Test fun `culvert changes waterway and preserves road`() {
        val changes = f.culvert.createUpdates(f.repo, f.idProvider())
        val middle = changes.ways().single { "tunnel" in it.tags }
        assertEquals("culvert", middle.tags["tunnel"])
        assertEquals("-1", middle.tags["layer"])
        assertTrue(changes.ways().all { it.tags["waterway"] == "stream" })
        assertTrue(changes.modifications.none { it.key == f.road.key })
    }

    @Test fun `route and restriction replacements refer to actual split results`() {
        f.data.putElement(rel(30, listOf(member(ElementType.WAY, 10, "forward")), mapOf("type" to "route")))
        f.data.putElement(rel(31, listOf(member(ElementType.WAY, 10, "from"), member(ElementType.NODE, 4, "via")), mapOf("type" to "restriction")))
        val changes = f.bridge.createUpdates(f.repo, f.idProvider())
        val route = changes.modifications.filterIsInstance<Relation>().single { it.id == 30L }
        assertEquals(changes.ways().map { it.id }.toSet(), route.members.map { it.ref }.toSet())
        assertTrue(route.members.all { it.role == "forward" })
        val restriction = changes.modifications.filterIsInstance<Relation>().single { it.id == 31L }
        val from = restriction.members.single { it.role == "from" }
        assertEquals(changes.ways().single { 4L in it.nodeIds }.id, from.ref)
    }

    @Test fun `existing structure or explicit height tags are rejected without overwrite`() {
        for (key in listOf("bridge", "tunnel", "covered", "ford", "layer", "level", "location")) {
            f.data.putElement(f.road.copy(tags = f.road.tags + (key to "0")))
            assertFailsWith<ConflictException> { f.bridge.createUpdates(f.repo, f.idProvider()) }
            assertFailsWith<ConflictException> { CreateWaterwayFordAction(f.crossing).createUpdates(f.repo, f.idProvider()) }
        }
    }

    @Test fun `moved interior geometry and changed topology conflict`() {
        f.data.putElement(f.data.getNode(2)!!.copy(position = p(0.0001, -0.0002)))
        assertFailsWith<ConflictException> { f.bridge.createUpdates(f.repo, f.idProvider()) }
        assertFailsWith<ConflictException> { CreateWaterwayFordAction(f.crossing).createUpdates(f.repo, f.idProvider()) }
        f.data.putElement(node(2, f.roadPositions[1]))
        f.data.putElement(f.road.copy(nodeIds = listOf(1, 3, 4)))
        assertFailsWith<ConflictException> { f.bridge.createUpdates(f.repo, f.idProvider()) }
    }

    @Test fun `new unrelated tags on server are preserved`() {
        f.data.putElement(f.road.copy(tags = f.road.tags + ("surface" to "gravel")))
        val changes = f.bridge.createUpdates(f.repo, f.idProvider())
        assertTrue(changes.ways().all { it.tags["surface"] == "gravel" })
    }

    @Test fun `cuts must enclose crossing and cannot create tiny segments`() {
        val invalid = listOf(
            listOf(f.splits[0]),
            listOf(f.splits[0], f.splits[0]),
            listOf(SplitAtLinePosition(f.roadPositions[1], f.roadPositions[2], 0.001), f.splits[1]),
            listOf(SplitAtLinePosition(f.roadPositions[0], f.roadPositions[1], 0.4),
                SplitAtLinePosition(f.roadPositions[0], f.roadPositions[1], 0.6)),
        )
        for (cuts in invalid) assertFailsWith<ConflictException> {
            f.bridge.copy(splits = cuts).createUpdates(f.repo, f.idProvider())
        }
    }

    @Test fun `manual entry finds unique pair and never generates a quest`() {
        val crossings = findWaterwayCrossings(f.data, p(), 15.0)
        assertEquals(1, crossings.size)
        assertEquals(f.road.id, crossings.single().road.id)
        assertEquals(f.water.id, crossings.single().waterway.id)
        assertTrue(findWaterwayCrossings(f.data, p(1.0, 1.0), 15.0).isEmpty())
    }

    @Test fun `closed repeated self intersecting and connected ways are rejected`() {
        val invalidRoads = listOf(
            f.road.copy(nodeIds = listOf(1, 2, 3, 1)),
            f.road.copy(nodeIds = listOf(1, 2, 3, 2, 4)),
            f.road.copy(nodeIds = listOf(1, 5, 3, 4)),
        )
        for (road in invalidRoads) {
            val positions = road.nodeIds.map { f.data.getNode(it)!!.position }
            f.data.putElement(road)
            assertFailsWith<ConflictException> { f.bridge.copy(crossing = f.crossing.copy(road = road,
                roadPositions = positions)).createUpdates(f.repo, f.idProvider()) }
        }
        val positions = listOf(p(-0.001, -0.001), p(0.001, 0.001), p(-0.001, 0.001), p(0.001, -0.001))
        assertFailsWith<ConflictException> { checkCrossing(f.road, f.water, positions, f.waterPositions, p()) }
    }

    @Test fun `ford creates one tagged node referenced by both ways`() {
        val changes = CreateWaterwayFordAction(f.crossing).createUpdates(f.repo, f.idProvider())
        val node = changes.creations.single() as Node
        assertEquals(mapOf("ford" to "yes"), node.tags)
        assertTrue(node.position.distanceTo(f.crossing.position) < 0.01)
        assertEquals(2, changes.ways().size)
        assertTrue(changes.ways().all { node.id in it.nodeIds })
        assertEquals(f.road.tags, changes.ways().single { it.id == 10L }.tags)
    }

    @Test fun `ford rejects nearby vertices major roads and additional crossing ways`() {
        val road = f.road.copy(tags = mapOf("highway" to "primary"))
        f.data.putElement(road)
        assertFailsWith<ConflictException> { CreateWaterwayFordAction(f.crossing.copy(road = road)).createUpdates(f.repo, f.idProvider()) }
        f.data.putElement(f.road)
        val nearby = f.roadPositions.toMutableList().also { it[1] = p(0.0, -0.000001) }
        assertFailsWith<ConflictException> { checkCrossing(f.road, f.water, nearby, f.waterPositions, p()) }
        val other = f.water.copy(id = 21)
        f.data.putElement(other)
        val crossing = f.crossing.copy(nearbyWays = listOf(CrossingContextWay(other, f.waterPositions)))
        assertFailsWith<ConflictException> { CreateWaterwayFordAction(crossing).createUpdates(f.repo, f.idProvider()) }
        assertFailsWith<ConflictException> { f.bridge.copy(crossing = crossing).createUpdates(f.repo, f.idProvider()) }
    }

    @Test fun `temporary way and node ids remap together`() {
        val mapped = f.bridge.idsUpdatesApplied(mapOf(f.road.key to 110, ElementKey(ElementType.NODE, 1) to 101))
        assertEquals(110L, mapped.crossing.road.id)
        assertEquals(101L, mapped.crossing.road.nodeIds.first())
        assertTrue(ElementKey(ElementType.NODE, 101) in mapped.elementKeys)
    }

    @Test fun `actions require fresh server data and are undoable before upload only`() {
        for (action in listOf(f.bridge, f.culvert, CreateWaterwayFordAction(f.crossing))) {
            assertTrue(action::class in ApplicationConstants.EDIT_ACTIONS_NOT_ALLOWED_TO_USE_LOCAL_CHANGES)
            assertTrue(edit(action = action).isUndoable)
            assertFalse(edit(action = action, isSynced = true).isUndoable)
        }
    }
}
