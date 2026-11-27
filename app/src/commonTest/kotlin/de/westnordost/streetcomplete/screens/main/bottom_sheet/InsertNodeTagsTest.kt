package de.westnordost.streetcomplete.screens.main.bottom_sheet

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeFromVertexAction
import de.westnordost.streetcomplete.data.osm.edits.create.createInsertNodeAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataRepository
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.math.PositionOnWay
import de.westnordost.streetcomplete.util.math.PositionOnWaySegment
import de.westnordost.streetcomplete.util.math.VertexOfWay
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

/** Covers the chain "insert node into way":
 *  preset + existing vertex → initial editor tags → editor result (diff) → node passed to onAdd
 *  → edit action → applied map data changes */
internal class InsertNodeTagsTest {
    private lateinit var repos: MapDataRepository
    private val provider = ElementIdProvider(listOf(ElementKey(ElementType.NODE, -1L)))

    private val vertexPos = LatLon(0.0, 1.0)
    private val crossingTags = mapOf(
        "highway" to "crossing",
        "crossing" to "marked",
        "tactile_paving" to "yes",
    )

    @BeforeTest fun setUp() {
        repos = mock()
    }

    //region initial tags and conflicts

    @Test fun `no conflicts when preset adds new keys or same values`() {
        assertEquals(
            emptyList(),
            getPresetTagConflicts(crossingTags, mapOf("highway" to "crossing", "barrier" to "bollard"))
        )
    }

    @Test fun `differing value of same key is a conflict`() {
        assertEquals(
            listOf(PresetTagConflict("highway", "crossing", "traffic_signals")),
            getPresetTagConflicts(crossingTags, mapOf("highway" to "traffic_signals"))
        )
    }

    @Test fun `initial tags keep existing values unless replacing was chosen`() {
        val preset = mapOf("highway" to "traffic_signals", "crossing" to "traffic_signals", "button_operated" to "yes")
        assertEquals(
            crossingTags + ("button_operated" to "yes"),
            getInitialTagsForExistingVertex(crossingTags, preset, replaceConflicting = false)
        )
        assertEquals(
            mapOf(
                "highway" to "traffic_signals",
                "crossing" to "traffic_signals",
                "tactile_paving" to "yes",
                "button_operated" to "yes",
            ),
            getInitialTagsForExistingVertex(crossingTags, preset, replaceConflicting = true)
        )
    }

    @Test fun `values are never merged with semicolon`() {
        val preset = mapOf("highway" to "traffic_signals")
        listOf(false, true).forEach { replace ->
            val tags = getInitialTagsForExistingVertex(crossingTags, preset, replace)
            assertTrue(tags.values.none { ';' in it })
        }
    }

    //endregion

    //region full chain: editor → action → applied changes

    @Test fun `same preset on tagged vertex keeps additional tags and creates no diff`() {
        val vertex = node(1, vertexPos, crossingTags)
        val preset = mapOf("highway" to "crossing", "crossing" to "marked")

        // user confirms the editor without changes
        val action = runChain(vertex, preset, replaceConflicting = false) { it }

        // nothing to do: no unnecessary edit
        assertNull(action)
    }

    @Test fun `other preset on tagged vertex keeps all existing tags and adds preset tags`() {
        val vertex = node(1, vertexPos, crossingTags)
        val action = runChain(vertex, mapOf("barrier" to "bollard"), replaceConflicting = false) { it }

        assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(setOf(StringMapEntryAdd("barrier", "bollard")), action.changes.changes)
        assertEquals(crossingTags + ("barrier" to "bollard"), applyVertexAction(action, vertex).tags)
    }

    @Test fun `conflicting preset value does not silently replace existing value`() {
        val vertex = node(1, vertexPos, crossingTags)
        val preset = mapOf("highway" to "traffic_signals", "button_operated" to "yes")

        val action = runChain(vertex, preset, replaceConflicting = false) { it }

        assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(setOf(StringMapEntryAdd("button_operated", "yes")), action.changes.changes)
        assertEquals("crossing", applyVertexAction(action, vertex).tags["highway"])
    }

    @Test fun `conflicting preset value replaces existing value after explicit decision`() {
        val vertex = node(1, vertexPos, crossingTags)
        val preset = mapOf("highway" to "traffic_signals")

        val action = runChain(vertex, preset, replaceConflicting = true) { it }

        assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(
            setOf(StringMapEntryModify("highway", "crossing", "traffic_signals")),
            action.changes.changes
        )
        assertEquals(
            crossingTags + ("highway" to "traffic_signals"),
            applyVertexAction(action, vertex).tags
        )
    }

    @Test fun `deliberate change and deletion in editor are applied and not restored`() {
        val vertex = node(1, vertexPos, crossingTags)
        val action = runChain(vertex, mapOf("barrier" to "bollard"), replaceConflicting = false) {
            it - "tactile_paving" + ("crossing" to "uncontrolled")
        }

        assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(
            setOf(
                StringMapEntryAdd("barrier", "bollard"),
                StringMapEntryDelete("tactile_paving", "yes"),
                StringMapEntryModify("crossing", "marked", "uncontrolled"),
            ),
            action.changes.changes
        )
        assertEquals(
            mapOf("highway" to "crossing", "crossing" to "uncontrolled", "barrier" to "bollard"),
            applyVertexAction(action, vertex).tags
        )
    }

    @Test fun `untagged vertex gets preset tags`() {
        val vertex = node(1, vertexPos)
        val action = runChain(vertex, mapOf("barrier" to "bollard"), replaceConflicting = false) { it }

        assertIs<CreateNodeFromVertexAction>(action)
        assertEquals(mapOf("barrier" to "bollard"), applyVertexAction(action, vertex).tags)
    }

    @Test fun `tags added to vertex after opening the editor are not removed`() {
        val shown = node(1, vertexPos, mapOf("highway" to "crossing"))
        val action = runChain(shown, mapOf("barrier" to "bollard"), replaceConflicting = false) { it }
        assertIs<CreateNodeFromVertexAction>(action)

        // meanwhile someone added another tag
        val current = shown.copy(tags = shown.tags + ("crossing" to "zebra"))
        assertEquals(
            mapOf("highway" to "crossing", "crossing" to "zebra", "barrier" to "bollard"),
            applyVertexAction(action, current).tags
        )
    }

    @Test fun `concurrent change of an edited tag is a conflict, not an overwrite`() {
        val shown = node(1, vertexPos, crossingTags)
        val action = runChain(shown, mapOf("barrier" to "bollard"), replaceConflicting = false) {
            it + ("crossing" to "uncontrolled")
        }
        assertIs<CreateNodeFromVertexAction>(action)

        val current = shown.copy(tags = shown.tags + ("crossing" to "unmarked"))
        assertFailsWith<ConflictException> { applyVertexAction(action, current) }
    }

    @Test fun `no action if the vertex shown is not the target vertex`() {
        val vertex = node(1, vertexPos, crossingTags)
        val target = VertexOfWay(setOf(10L), vertexPos, nodeId = 2)
        assertNull(createInsertNodeAction(target, vertex, crossingTags + ("a" to "b")) { listOf(10L) })
        assertNull(createInsertNodeAction(target, null, mapOf("a" to "b")) { listOf(10L) })
    }

    @Test fun `new node on segment still gets preset and edited tags`() {
        val pos1 = LatLon(0.0, 1.0)
        val pos2 = LatLon(0.0, 2.0)
        val way = way(10, listOf(1, 2))
        every { repos.getWayComplete(10) } returns MutableMapData(listOf(way, node(1, pos1), node(2, pos2)))

        val segment = PositionOnWaySegment(10, LatLon(0.0, 1.5), pos1 to pos2)
        val preset = mapOf("barrier" to "bollard")
        val action = runChain(null, preset, replaceConflicting = false, segment) {
            it + ("bollard" to "removable")
        }

        assertIs<CreateNodeAction>(action)
        val updates = action.createUpdates(repos, provider)
        val created = updates.creations.single() as Node
        assertEquals(mapOf("barrier" to "bollard", "bollard" to "removable"), created.tags)
        assertEquals(listOf(1L, -1L, 2L), (updates.modifications.single() as Way).nodeIds)
    }

    //endregion

    /** Runs the same functions as the UI: initial tags → [edit] by user → editor diff → node from
     *  editor → insert action */
    private fun runChain(
        vertex: Node?,
        presetTags: Map<String, String>,
        replaceConflicting: Boolean,
        positionOnWay: PositionOnWay = VertexOfWay(setOf(10L), vertexPos, vertex!!.id),
        edit: (Map<String, String>) -> Map<String, String>,
    ): ElementEditAction? {
        val originalTags = vertex?.tags ?: presetTags
        val initialTags = vertex?.let {
            getInitialTagsForExistingVertex(it.tags, presetTags, replaceConflicting)
        } ?: presetTags
        val changes = editedTagsToChanges(edit(initialTags), originalTags)
        val node = nodeFromEditorChanges(vertex, presetTags, positionOnWay.position, changes)
        return createInsertNodeAction(positionOnWay, vertex, node.tags) { listOf(10L) }
    }

    private fun applyVertexAction(action: CreateNodeFromVertexAction, currentNode: Node): Node {
        every { repos.getNode(currentNode.id) } returns currentNode
        every { repos.getWaysForNode(currentNode.id) } returns listOf(way(10, listOf(currentNode.id)))
        return action.createUpdates(repos, provider).modifications.single() as Node
    }
}
