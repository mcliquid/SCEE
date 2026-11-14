package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.AllEditTypes
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceDao
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** A2 regression based on the audit reproduction, using real SQLite and both upload layers. */
class ElementUploadClaimsTest : StreetCompleteDatabaseTestCase() {
    private val questType = TestQuestType()
    private lateinit var controller: ElementEditsControllerImpl
    private lateinit var server: ClaimTestServer
    private lateinit var uploader: ElementEditsUploader
    private lateinit var database: Database
    private lateinit var singleUploader: ElementEditUploader
    private val localNodes = mutableMapOf<Long, Node>()

    override fun onDatabaseInitialized(database: Database) {
        this.database = database
        val types = AllEditTypes(listOf(QuestTypeRegistry({ listOf(1 to questType) }), OverlayRegistry(emptyList())))
        controller = ElementEditsControllerImpl(
            ElementEditsDao(database, types, tagEdit, addNodeEdit),
            EditElementsDao(database),
            ElementIdProviderDao(database), database, ExternalSourceDao(database),
        )
        server = ClaimTestServer()
        val changesets: OpenChangesetsManager = mock {
            everySuspend { getOrCreateChangeset(any(), any(), any(), any()) } returns 1L
            everySuspend { createChangeset(any(), any(), any()) } returns 2L
        }
        val localSource: MapDataSource = mock {
            every { get(any(), any()) } calls { (_: ElementType, id: Long) -> localNodes[id] }
            every { getNodes(any()) } returns emptyList()
        }
        singleUploader = ElementEditUploader(changesets, server, localSource)
        uploader = ElementEditsUploader(
            controller, mock(), mock(),
            singleUploader,
            server, mock(), mock(), mockPrefs2()
        )
    }

    private fun addCreateNode() =
        controller.add(questType, pGeom(), "survey", CreateNodeAction(p(1.0, 2.0), mapOf("amenity" to "bench")), true, null)

    private fun addTagEdit(node: Node, key: String = "lit", value: String = "yes") =
        controller.add(questType, pGeom(), "survey",
            UpdateElementTagsAction(node, StringMapChanges(setOf(StringMapEntryAdd(key, value)))), true, null)

    private fun addTag(): ElementEdit {
        val node = Node(5, p(), emptyMap(), 1)
        server.nodes[5] = node
        localNodes[5] = node
        addTagEdit(node)
        return controller.getAllUnsynced().single()
    }

    @Test fun `undo before claim prevents any request`() = runBlocking {
        val edit = addTag()
        assertTrue(controller.undo(edit))
        uploader.upload()
        assertEquals(0, server.calls)
        assertNull(controller.claimOldestUnsynced())
    }

    @Test fun `claim and undo share controller synchronization`() {
        val edit = addTag()
        assertEquals(edit, controller.claimOldestUnsynced())
        assertNull(controller.claimOldestUnsynced())
        assertFailsWith<ElementEditUploadInProgressException> { controller.undo(edit) }
        assertEquals(edit, controller.get(edit.id))
        controller.releaseUploadClaim(edit.id)
        assertTrue(controller.undo(edit))
    }

    @Test fun `undo during suspended HTTP is rejected and stale undo after success queues revert`() = runBlocking {
        val edit = addTag()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await() }
        val upload = async(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { entered.await() }
            assertFailsWith<ElementEditUploadInProgressException> { controller.undo(edit) }
            assertFalse(controller.get(edit.id)!!.isSynced)
        } finally { release.complete(Unit) }
        withTimeout(10000) { upload.await() }
        assertEquals("yes", server.nodes[5]!!.tags["lit"])
        assertTrue(controller.get(edit.id)!!.isSynced)
        assertTrue(controller.undo(edit)) // deliberately pass the old, unsynced history snapshot
        assertTrue(controller.getAllUnsynced().single().action is IsRevertAction)
    }

    @Test fun `definite failure releases claim and permits retry or undo`() = runBlocking {
        val edit = addTag()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await(); throw ConnectionException("before commit") }
        val upload = async(Dispatchers.Default) { runCatching { uploader.upload() } }
        try {
            withTimeout(10000) { entered.await() }
            assertFailsWith<ElementEditUploadInProgressException> { controller.undo(edit) }
        } finally { release.complete(Unit) }
        assertTrue(withTimeout(10000) { upload.await() }.exceptionOrNull() is ConnectionException)
        assertNull(server.nodes[5]!!.tags["lit"])
        assertEquals(edit, controller.claimOldestUnsynced())
        controller.releaseUploadClaim(edit.id)
        assertTrue(controller.undo(edit))
    }

    @Test fun `cancellation finishes current request before releasing claim`() = runBlocking {
        val edit = addTag()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await() }
        val upload = launch(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { entered.await() }
            upload.cancel()
            assertFailsWith<ElementEditUploadInProgressException> { controller.undo(edit) }
        } finally { release.complete(Unit) }
        withTimeout(10000) { upload.join() }
        assertTrue(controller.get(edit.id)!!.isSynced)
        assertTrue(controller.undo(edit))
        assertTrue(controller.getAllUnsynced().single().action is IsRevertAction)
    }

    @Test fun `two upload requests send the edit once`() = runBlocking {
        addTag()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await() }
        val first = async(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { entered.await() }
            val second = async(Dispatchers.Default) { uploader.upload() }
            release.complete(Unit)
            withTimeout(10000) { first.await(); second.await() }
        } finally { release.complete(Unit) }
        assertEquals(1, server.calls)
    }

    @Test fun `separate uploaders share authoritative controller claim`() = runBlocking {
        val edit = addTag()
        val other = ElementEditsUploader(controller, mock(), mock(), singleUploader, server, mock(), mock(), mockPrefs2())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await() }
        val first = async(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { entered.await() }
            withTimeout(10000) { other.upload() }
            assertEquals(1, server.calls)
            assertFailsWith<ElementEditUploadInProgressException> { controller.undo(edit) }
        } finally { release.complete(Unit) }
        withTimeout(10000) { first.await() }
        assertTrue(controller.get(edit.id)!!.isSynced)
    }

    @Test fun `undo ancestor cannot delete a claimed dependent edit`() {
        addCreateNode()
        val parent = controller.getAllUnsynced().single()
        controller.markSynced(parent, MapDataUpdates(idUpdates = listOf(
            de.westnordost.streetcomplete.data.osm.mapdata.ElementIdUpdate(ElementType.NODE, -1, 42))))
        addTagEdit(Node(42, p(), emptyMap(), 1))
        val dependent = controller.getAllUnsynced().single()
        assertEquals(dependent, controller.claimOldestUnsynced())
        assertFailsWith<ElementEditUploadInProgressException> { controller.undo(parent) }
        assertNotNull(controller.get(dependent.id))
        controller.releaseUploadClaim(dependent.id)
        assertTrue(controller.undo(parent))
    }

    @Test fun `unexpected exception preserves edit and releases claim`() = runBlocking {
        val edit = addTag()
        server.whileInFlight = { throw IllegalStateException("local failure") }
        assertFailsWith<IllegalStateException> { uploader.upload() }
        assertEquals(edit, controller.get(edit.id))
        assertEquals(edit, controller.claimOldestUnsynced())
        controller.releaseUploadClaim(edit.id)
        uploader.upload()
        assertTrue(controller.get(edit.id)!!.isSynced)
    }

    @Test fun `history cleanup cannot remove acknowledged edit before claim release`() {
        val edit = addTag()
        assertEquals(edit, controller.claimOldestUnsynced())
        controller.markSynced(edit, MapDataUpdates())
        assertEquals(0, controller.deleteSyncedOlderThan(Long.MAX_VALUE))
        assertNotNull(controller.get(edit.id))
        controller.releaseUploadClaim(edit.id)
        assertEquals(1, controller.deleteSyncedOlderThan(Long.MAX_VALUE))
        assertNull(controller.get(edit.id))
    }

    @Test fun `claims are transient and a reconstructed controller can claim queued edit`() {
        val edit = addTag()
        assertEquals(edit, controller.claimOldestUnsynced())
        val types = AllEditTypes(listOf(QuestTypeRegistry({ listOf(1 to questType) })))
        val restarted = ElementEditsControllerImpl(ElementEditsDao(database, types, tagEdit, addNodeEdit),
            EditElementsDao(database), ElementIdProviderDao(database), database, ExternalSourceDao(database))
        assertEquals(edit, restarted.claimOldestUnsynced())
        restarted.releaseUploadClaim(edit.id)
        controller.releaseUploadClaim(edit.id)
    }
}

internal class ClaimTestServer : MapDataApiClient {
    val nodes = mutableMapOf<Long, Node>()
    var calls = 0
    var whileInFlight: (suspend () -> Unit)? = null
    private var nextId = 1000L

    override suspend fun uploadChanges(
        changesetId: Long,
        changes: MapDataChanges,
        ignoreRelation: (tags: Map<String, String>, members: Int) -> Boolean
    ): MapDataUpdates {
        calls++
        whileInFlight?.let { whileInFlight = null; it() }
        val all = changes.creations + changes.modifications + changes.deletions
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
