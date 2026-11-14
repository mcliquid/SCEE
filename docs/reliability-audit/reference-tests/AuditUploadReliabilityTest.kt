package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.AllEditTypes
import de.westnordost.streetcomplete.data.ApiClientException
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditUploader
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditsUploader
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.OpenChangesetsManager
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.ElementUpdate
import de.westnordost.streetcomplete.data.osm.mapdata.MapData
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataApiClient
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataChanges
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataSource
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.Relation
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.mapdata.createMapDataUpdates
import de.westnordost.streetcomplete.data.osm.osmquests.TestQuestType
import de.westnordost.streetcomplete.data.overlays.OverlayRegistry
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.testutils.mockPrefs2
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.pGeom
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AUDIT regression tests (not part of the main tree). Each test asserts the DESIRED invariant, so
 * a FAILING test here confirms the defect described in its name.
 *
 * Real SQLite queue + real ElementEditsControllerImpl + real ElementEditUploader/ElementEditsUploader,
 * against a tiny in-memory OSM API fake.
 */
class AuditUploadReliabilityTest : StreetCompleteDatabaseTestCase() {
    private val questType = TestQuestType()
    private lateinit var controller: ElementEditsControllerImpl
    private lateinit var server: FakeOsmServer
    private lateinit var uploader: ElementEditsUploader
    private val localNodes = mutableMapOf<Long, Node>()

    override fun onDatabaseInitialized(database: Database) {
        val types = AllEditTypes(listOf(QuestTypeRegistry({ listOf(1 to questType) }), OverlayRegistry(emptyList())))
        controller = ElementEditsControllerImpl(
            ElementEditsDao(database, types, tagEdit, addNodeEdit),
            EditElementsDao(database),
            ElementIdProviderDao(database),
        )
        server = FakeOsmServer()
        val changesets: OpenChangesetsManager = mock {
            everySuspend { getOrCreateChangeset(any(), any(), any(), any()) } returns 1L
            everySuspend { createChangeset(any(), any(), any()) } returns 2L
        }
        val localSource: MapDataSource = mock {
            every { get(any(), any()) } calls { (_: ElementType, id: Long) -> localNodes[id] }
            every { getNodes(any()) } returns emptyList()
        }
        uploader = ElementEditsUploader(
            controller, mock(), mock(),
            ElementEditUploader(changesets, server, localSource),
            server, mock(), mock(), mockPrefs2()
        )
    }

    private fun addCreateNode() =
        controller.add(questType, pGeom(), "survey", CreateNodeAction(p(1.0, 2.0), mapOf("amenity" to "bench")), true, null)

    private fun addTagEdit(node: Node, key: String = "lit", value: String = "yes") =
        controller.add(questType, pGeom(), "survey",
            UpdateElementTagsAction(node, StringMapChanges(setOf(StringMapEntryAdd(key, value)))), true, null)

    /** F1: server commits, reply is lost (timeout / connection reset / process death) → retry. */
    @Test fun `node creation is not duplicated when the server reply is lost and the upload is retried`(): Unit = runBlocking {
        addCreateNode()
        server.loseNextResponse = true
        assertFailsWith<ConnectionException> { uploader.upload() }
        assertEquals(1, controller.getAllUnsynced().size) // still queued, WorkManager retries
        uploader.upload()
        assertEquals(1, server.nodes.size, "the same bench exists ${server.nodes.size}x on the server")
    }

    /** F1b: same scenario for a tag edit: second upload is accepted and bumps the version again. */
    @Test fun `tag edit is not uploaded a second time when the server reply is lost`(): Unit = runBlocking {
        val node = Node(5, p(), emptyMap(), 1)
        server.nodes[5] = node
        localNodes[5] = node
        addTagEdit(node)
        server.loseNextResponse = true
        assertFailsWith<ConnectionException> { uploader.upload() }
        uploader.upload()
        assertEquals(2, server.nodes[5]!!.version, "element got an extra identical version")
    }

    /** F2: user taps undo while the HTTP request of that very edit is in flight. */
    @Test fun `undo during in-flight upload either prevents the upload or queues a revert`(): Unit = runBlocking {
        val node = Node(5, p(), emptyMap(), 1)
        server.nodes[5] = node
        localNodes[5] = node
        addTagEdit(node)
        val edit = controller.getOldestUnsynced()!!
        var undoResult: Boolean? = null
        server.whileInFlight = { undoResult = controller.undo(edit) }

        uploader.upload()

        assertEquals(true, undoResult) // the UI was told "undone"
        val onServer = server.nodes[5]!!.tags["lit"]
        val pendingRevert = controller.getAllUnsynced().any { it.action is IsRevertAction }
        assertTrue(onServer == null || pendingRevert,
            "lit=$onServer is on the server, history has ${controller.getAll().size} entries, no revert is queued")
    }

    /** F3: an edit the API rejects with a non-conflict 4xx blocks every later edit forever. */
    @Test fun `a permanently rejected edit does not block later edits`(): Unit = runBlocking {
        val bad = Node(6, p(), emptyMap(), 1)
        val good = Node(7, p(), emptyMap(), 1)
        server.nodes[6] = bad; server.nodes[7] = good
        localNodes[6] = bad; localNodes[7] = good
        addTagEdit(bad)
        Thread.sleep(5)
        addTagEdit(good)
        server.rejectNodeIds += 6L
        repeat(3) { runCatching { uploader.upload() } }
        assertEquals("yes", server.nodes[7]!!.tags["lit"], "later edit never reached the server; queue=${controller.getAllUnsynced().size}")
    }
}

private class FakeOsmServer : MapDataApiClient {
    val nodes = mutableMapOf<Long, Node>()
    val rejectNodeIds = mutableSetOf<Long>()
    var loseNextResponse = false
    var whileInFlight: (() -> Unit)? = null
    private var nextId = 1000L

    override suspend fun uploadChanges(
        changesetId: Long,
        changes: MapDataChanges,
        ignoreRelation: (tags: Map<String, String>, members: Int) -> Boolean
    ): MapDataUpdates {
        val all = changes.creations + changes.modifications + changes.deletions
        if (all.any { it.id in rejectNodeIds }) throw ApiClientException("HTTP 400")
        val updates = mutableMapOf<ElementKey, ElementUpdate>()
        for (c in changes.creations) {
            c as Node
            val id = nextId++
            nodes[id] = c.copy(id = id, version = 1)
            updates[c.key] = ElementUpdate.Update(id, 1)
        }
        for (m in changes.modifications) {
            m as Node
            val current = nodes[m.id] ?: throw ConflictException("gone")
            if (current.version != m.version) throw ConflictException("version mismatch")
            nodes[m.id] = m.copy(version = current.version + 1)
            updates[m.key] = ElementUpdate.Update(m.id, current.version + 1)
        }
        whileInFlight?.let { whileInFlight = null; it() }
        if (loseNextResponse) {
            loseNextResponse = false
            throw ConnectionException("reply lost after commit")
        }
        return createMapDataUpdates(all, updates, ignoreRelation)
    }

    override suspend fun getNode(id: Long): Node? = nodes[id]
    override suspend fun getMap(bounds: BoundingBox, ignoreRelation: (tags: Map<String, String>, members: Int) -> Boolean): MutableMapData = MutableMapData()
    override suspend fun getWayComplete(id: Long): MapData? = null
    override suspend fun getRelationComplete(id: Long): MapData? = null
    override suspend fun getWay(id: Long): Way? = null
    override suspend fun getRelation(id: Long): Relation? = null
    override suspend fun getWaysForNode(id: Long): Collection<Way> = emptyList()
    override suspend fun getRelationsForNode(id: Long): Collection<Relation> = emptyList()
    override suspend fun getRelationsForWay(id: Long): Collection<Relation> = emptyList()
    override suspend fun getRelationsForRelation(id: Long): Collection<Relation> = emptyList()
}
