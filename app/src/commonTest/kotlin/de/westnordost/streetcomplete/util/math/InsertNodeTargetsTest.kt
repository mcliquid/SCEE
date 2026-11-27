package de.westnordost.streetcomplete.util.math

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataChanges
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeFromVertexAction
import de.westnordost.streetcomplete.data.osm.edits.create.createInsertNodeAction
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataRepository
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Insert node into way": target selection must describe exactly the topology of the resulting edit.
 *  1e-5° ≈ 1.1 m */
internal class InsertNodeTargetsTest {
    private lateinit var repos: MapDataRepository

    private val nodes = HashMap<Long, Node>()
    private val ways = HashMap<Long, Way>()

    @BeforeTest fun setUp() {
        repos = mock()
        nodes.clear()
        ways.clear()
    }

    //region crossings: never connected automatically

    @Test fun `footway under bridge is never connected`() {
        addWay(1, mapOf("highway" to "primary", "bridge" to "yes", "layer" to "1"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("highway" to "footway"), 3L to LatLon(-0.001, 0.0), 4L to LatLon(0.001, 0.0))

        val targets = LatLon(0.00002, 0.00003).targets()

        assertTrue(targets.candidates.none { it is PositionOnCrossingWaySegments })
        assertTrue(targets.candidates.all { it.affectedWayIds.size == 1 })
        // both ways nearly equally near: user has to choose
        assertNull(targets.preselected)
    }

    @Test fun `tunnel and differing layer are never offered as new connection`() {
        addWay(1, mapOf("highway" to "primary", "tunnel" to "yes"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("highway" to "footway"), 3L to LatLon(-0.001, 0.0), 4L to LatLon(0.001, 0.0))
        assertTrue(LatLon(0.0, 0.00003).targets().candidates.none { it is PositionOnCrossingWaySegments })

        ways.clear(); nodes.clear()
        addWay(1, mapOf("highway" to "primary", "layer" to "1"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("highway" to "footway"), 3L to LatLon(-0.001, 0.0), 4L to LatLon(0.001, 0.0))
        assertTrue(LatLon(0.0, 0.00003).targets().candidates.none { it is PositionOnCrossingWaySegments })
    }

    @Test fun `road crossing building, landuse or power line is not connected automatically`() {
        for (otherTags in listOf(
            mapOf("building" to "yes"),
            mapOf("landuse" to "residential"),
            mapOf("power" to "line"),
        )) {
            ways.clear(); nodes.clear()
            addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
            addWay(2, otherTags, 3L to LatLon(-0.001, 0.0), 4L to LatLon(0.001, 0.0))

            // clearly nearer to the road, but close to the crossing
            val targets = LatLon(0.00001, 0.00006).targets()

            val preselected = assertIs<PositionOnWaySegment>(targets.preselected, "$otherTags")
            assertEquals(1L, preselected.wayId)
            val action = assertIs<CreateNodeAction>(createAction(preselected))
            assertEquals(listOf(1L), action.insertIntoWays.map { it.wayId })
        }
    }

    @Test fun `at-grade crossing without shared node is only connected when explicitly selected`() {
        addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("highway" to "footway"), 3L to LatLon(-0.001, 0.0), 4L to LatLon(0.001, 0.0))

        val targets = LatLon(0.00001, 0.00001).targets()

        val crossing = targets.candidates.filterIsInstance<PositionOnCrossingWaySegments>().single()
        assertTrue(targets.preselected !is PositionOnCrossingWaySegments)

        // explicitly selected: one new node in both ways
        val updates = apply(createAction(crossing))
        val newNodeId = (updates.creations.single() as Node).id
        assertEquals(listOf(1L, newNodeId, 2L), updates.modifiedWay(1).nodeIds)
        assertEquals(listOf(3L, newNodeId, 4L), updates.modifiedWay(2).nodeIds)
    }

    //endregion

    //region selection determines action and highlighted ways

    @Test fun `selecting way A creates the edit only for way A`() {
        addWay(1, mapOf("highway" to "footway"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("highway" to "cycleway"), 3L to LatLon(0.00002, -0.001), 4L to LatLon(0.00002, 0.001))

        val targets = LatLon(0.00001, 0.0).targets()
        assertNull(targets.preselected) // equally near: no arbitrary first candidate

        val wayA = targets.candidates.single { it.affectedWayIds == listOf(1L) }
        val updates = apply(createAction(wayA))
        val newNodeId = (updates.creations.single() as Node).id
        assertEquals(listOf(1L), updates.modifications.map { it.id })
        assertEquals(listOf(1L, newNodeId, 2L), updates.modifiedWay(1).nodeIds)
        assertEquals(listOf(3L, 4L), ways.getValue(2).nodeIds)
    }

    @Test fun `geometrically coincident but separate segments stay separate`() {
        addWay(1, mapOf("highway" to "service"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        addWay(2, mapOf("landuse" to "residential"), 5L to LatLon(0.0, -0.001), 6L to LatLon(0.0, 0.001))

        val targets = LatLon(0.00001, 0.0).targets()

        assertNull(targets.preselected)
        assertEquals(2, targets.candidates.size)
        for (candidate in targets.candidates) {
            val action = assertIs<CreateNodeAction>(createAction(assertIs<PositionOnWaySegment>(candidate)))
            assertEquals(1, action.insertIntoWays.size)
        }
    }

    @Test fun `actually shared segment keeps its topology`() {
        addWay(1, mapOf("highway" to "service"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001), 3L to LatLon(0.001, 0.001))
        // shares segment 1-2, in reverse order
        addWay(2, mapOf("landuse" to "residential"), 7L to LatLon(0.001, -0.001), 2L to LatLon(0.0, 0.001), 1L to LatLon(0.0, -0.001))

        val targets = LatLon(0.00001, 0.0).targets()

        val shared = assertIs<PositionOnWaysSegment>(targets.preselected)
        assertEquals(setOf(1L, 2L), shared.affectedWayIds.toSet())
        assertEquals(1, targets.candidates.size)

        val updates = apply(createAction(shared))
        val newNodeId = (updates.creations.single() as Node).id
        assertEquals(listOf(1L, newNodeId, 2L, 3L), updates.modifiedWay(1).nodeIds)
        assertEquals(listOf(7L, 2L, newNodeId, 1L), updates.modifiedWay(2).nodeIds)
    }

    @Test fun `existing shared node is reused and all its loaded ways are shown`() {
        addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.0))
        addWay(2, mapOf("highway" to "footway"), 2L to LatLon(0.0, 0.0), 3L to LatLon(0.001, 0.0))

        val targets = LatLon(0.00001, 0.00001).targets()

        val vertex = assertIs<VertexOfWay>(targets.preselected)
        assertEquals(2L, vertex.nodeId)
        assertEquals(setOf(1L, 2L), vertex.affectedWayIds.toSet())
        // no separate new node next to the shared one
        assertTrue(targets.candidates.none { it is PositionOnWaySegment && it.wayId in setOf(1L, 2L) })

        val action = createInsertNodeAction(vertex, nodes.getValue(2), mapOf("barrier" to "kerb")) { listOf(1L, 2L) }
        val fromVertex = assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(listOf(1L, 2L), fromVertex.containingWayIds)
        assertEquals(0, fromVertex.newElementsCount.nodes)
    }

    @Test fun `ways hidden by level filter are not offered but still count for shared node`() {
        addWay(1, mapOf("highway" to "corridor", "level" to "1"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.0))
        addWay(2, mapOf("highway" to "footway"), 2L to LatLon(0.0, 0.0), 3L to LatLon(0.001, 0.0))

        val targets = LatLon(0.0, 0.00001).targets(isCandidate = { it.id != 1L })

        val vertex = assertIs<VertexOfWay>(targets.preselected)
        assertEquals(setOf(1L, 2L), vertex.wayIds)
    }

    @Test fun `closed way inserts between last and first node`() {
        addWay(1, mapOf("building" to "yes"),
            1L to LatLon(0.0, 0.0), 2L to LatLon(0.0, 0.001), 3L to LatLon(0.001, 0.001), 4L to LatLon(0.001, 0.0), 1L to LatLon(0.0, 0.0)
        )
        val targets = LatLon(0.0005, -0.00001).targets()

        val segment = assertIs<PositionOnWaySegment>(targets.preselected)
        val updates = apply(createAction(segment))
        val newNodeId = (updates.creations.single() as Node).id
        assertEquals(listOf(1L, 2L, 3L, 4L, newNodeId, 1L), updates.modifiedWay(1).nodeIds)
    }

    //endregion

    //region missing or changed target

    @Test fun `incomplete geometry nearby requires explicit choice`() {
        addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        val targets = LatLon(0.00001, 0.0).targets(allowPreselection = false)
        assertNull(targets.preselected)
        assertEquals(1, targets.candidates.size)
    }

    @Test fun `target way changed before applying is a conflict`() {
        addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        val target = LatLon(0.00001, 0.0).targets().preselected!!
        val action = createAction(target)

        // meanwhile a node was inserted into that segment
        nodes[9] = node(9, LatLon(0.0, 0.0005))
        ways[1] = ways.getValue(1).copy(nodeIds = listOf(1, 9, 2))
        assertFailsWith<ConflictException> { apply(action) }

        // or the way was deleted
        ways.remove(1)
        assertFailsWith<ConflictException> { apply(action) }
    }

    @Test fun `nothing nearby gives no target`() {
        addWay(1, mapOf("highway" to "residential"), 1L to LatLon(0.0, -0.001), 2L to LatLon(0.0, 0.001))
        val targets = LatLon(0.01, 0.0).targets()
        assertTrue(targets.candidates.isEmpty())
        assertNull(targets.preselected)
    }

    //endregion

    //region helpers

    private fun addWay(id: Long, tags: Map<String, String>, vararg wayNodes: Pair<Long, LatLon>) {
        for ((nodeId, pos) in wayNodes) nodes[nodeId] = node(nodeId, pos)
        ways[id] = way(id, wayNodes.map { it.first }, tags)
    }

    private fun LatLon.targets(
        isCandidate: (Way) -> Boolean = { true },
        allowPreselection: Boolean = true,
    ) = getInsertNodeTargets(
        ways = ways.values.map { way -> way to way.nodeIds.map { nodes.getValue(it).position } },
        isCandidate = isCandidate,
        maxDistance = 10.0,
        snapToVertexDistance = 5.0,
        ambiguityDistance = 10.0 / 6,
        allowPreselection = allowPreselection,
    )

    private fun createAction(target: PositionOnWay) =
        createInsertNodeAction(target, null, mapOf("barrier" to "bollard")) { emptyList() }!!

    /** applies the action to the current in-memory data */
    private fun apply(action: ElementEditAction): MapDataChanges {
        for (key in action.elementKeys.filter { it.type == ElementType.WAY }) {
            val way = ways[key.id]
            every { repos.getWayComplete(key.id) } returns way?.let {
                MutableMapData(listOf(it) + it.nodeIds.distinct().map { id -> nodes.getValue(id) })
            }
        }
        // fresh provider: each one hands out the new node id only once
        return action.createUpdates(repos, ElementIdProvider(listOf(ElementKey(ElementType.NODE, -1L))))
    }

    private fun MapDataChanges.modifiedWay(id: Long) =
        modifications.filterIsInstance<Way>().single { it.id == id }

    //endregion
}
