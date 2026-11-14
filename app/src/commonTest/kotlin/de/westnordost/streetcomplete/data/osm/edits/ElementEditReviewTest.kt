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

import de.westnordost.streetcomplete.data.osm.edits.create.CreateRelationAction
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osm.edits.upload.requireResolvedReferences
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.DatabaseImpl
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.data.ConflictAlgorithm
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.answering.throws
import kotlinx.coroutines.*

/** Persistent B1 regression based on the original audit reproduction. */
class ElementEditReviewTest : StreetCompleteDatabaseTestCase() {
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

    private fun addTag(id: Long): ElementEdit {
        val node = Node(id, p(), emptyMap(), 1)
        server.nodes[id] = node
        localNodes[id] = node
        addTagEdit(node)
        return controller.getAllUnsynced().last()
    }
    private fun reconstructed(db: Database = database) = ElementEditsControllerImpl(
        ElementEditsDao(db, AllEditTypes(listOf(QuestTypeRegistry({ listOf(1 to questType) }))), tagEdit, addNodeEdit),
        EditElementsDao(db), ElementIdProviderDao(db), db, ExternalSourceDao(db))
    private fun uploader(ctrl: ElementEditsControllerImpl) = ElementEditsUploader(ctrl, mock(), mock(), singleUploader,
        server, mock(), mock(), mockPrefs2())
    private fun reject(reason: String = "Invalid tag") { server.whileInFlight = { throw ElementUploadRejectedException(400, reason) } }
    private fun countReviews() = database.rawQuery("SELECT COUNT(*) AS n FROM osm_element_edit_reviews") { it.getInt("n") }.single()

    @Test fun `permanent rejection retains original and lets independent edit upload across restart`() = runBlocking<Unit> {
        val first = addTag(6)
        val second = addTag(7)
        reject()
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        assertEquals(2, server.calls)
        assertNull(server.nodes[6]!!.tags["lit"])
        assertEquals("yes", server.nodes[7]!!.tags["lit"])
        assertEquals(first.action, controller.get(first.id)!!.action)
        assertFalse(controller.get(first.id)!!.isSynced)
        assertTrue(controller.get(second.id)!!.isSynced)
        val restarted = reconstructed()
        assertEquals("Invalid tag", restarted.get(first.id)!!.review!!.reason)
        assertFailsWith<ElementEditsNeedReviewException> { uploader(restarted).upload() }
        assertEquals(2, server.calls, "restart must not resend the rejected edit")
    }

    @Test fun `explicit retry clears persisted review then succeeds`() = runBlocking<Unit> {
        val edit = addTag(6)
        reject()
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        assertTrue(controller.retryRejectedEdit(edit.id))
        assertEquals(0, countReviews())
        assertFalse(controller.retryRejectedEdit(edit.id))
        uploader.upload()
        assertTrue(controller.get(edit.id)!!.isSynced)
        assertEquals("yes", server.nodes[6]!!.tags["lit"])
    }

    @Test fun `repeated rejection replaces diagnosis and remains unsynced`() = runBlocking<Unit> {
        val edit = addTag(6)
        reject("first diagnosis")
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        controller.retryRejectedEdit(edit.id)
        reject("new diagnosis")
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        assertEquals("new diagnosis", reconstructed().get(edit.id)!!.review!!.reason)
        assertEquals(1, countReviews())
    }

    @Test fun `created element rejection blocks transitive dependents and recovery updates IDs`() {
        addCreateNode()
        val root = controller.getAllUnsynced().single()
        val nodeId = controller.getIdProvider(root.id).getAll().single().id
        controller.add(questType, pGeom(), "survey", CreateRelationAction(emptyMap(), listOf(RelationMember(ElementType.NODE, nodeId, ""))), true, null)
        val child = controller.getAllUnsynced().last()
        val relationId = controller.getIdProvider(child.id).getAll().single().id
        controller.add(questType, pGeom(), "survey", CreateRelationAction(emptyMap(), listOf(RelationMember(ElementType.RELATION, relationId, ""))), true, null)
        val grandchild = controller.getAllUnsynced().last()
        assertEquals(root.id, controller.claimOldestUnsynced()!!.id)
        controller.markRejected(root.id, ElementUploadRejectedException(400, "creation rejected"))
        assertFailsWith<ElementEditUploadInProgressException> { controller.retryRejectedEdit(root.id) }
        assertFailsWith<ElementEditUploadInProgressException> { controller.undo(root) }
        controller.releaseUploadClaim(root.id)
        assertNull(controller.claimOldestUnsynced())
        assertEquals(root.id, controller.get(child.id)!!.review!!.editId)
        assertEquals(root.id, controller.get(grandchild.id)!!.review!!.editId)
        controller.retryRejectedEdit(root.id)
        assertEquals(root.id, controller.claimOldestUnsynced()!!.id)
        controller.markSynced(root, MapDataUpdates(idUpdates = listOf(ElementIdUpdate(ElementType.NODE, nodeId, 42))))
        controller.releaseUploadClaim(root.id)
        assertEquals(child.id, controller.claimOldestUnsynced()!!.id)
        controller.markSynced(controller.get(child.id)!!, MapDataUpdates(idUpdates = listOf(ElementIdUpdate(ElementType.RELATION, relationId, 43))))
        controller.releaseUploadClaim(child.id)
        assertEquals(grandchild.id, controller.claimOldestUnsynced()!!.id)
        controller.releaseUploadClaim(grandchild.id)
    }

    @Test fun `rejected existing element also blocks later edits on that element`() {
        val first = addTag(6)
        addTagEdit(server.nodes[6]!!, "name", "later")
        controller.claimOldestUnsynced()
        controller.markRejected(first.id, ElementUploadRejectedException(400, "invalid"))
        controller.releaseUploadClaim(first.id)
        assertNull(controller.claimOldestUnsynced())
        assertEquals(first.id, controller.getAllUnsynced().last().review!!.editId)
    }

    @Test fun `undo deletes rejected creation dependents and review atomically`() {
        addCreateNode()
        val root = controller.getAllUnsynced().single()
        val created = controller.getIdProvider(root.id).getAll().single().id
        addTagEdit(Node(created, p(), emptyMap(), 1))
        controller.claimOldestUnsynced()
        controller.markRejected(root.id, ElementUploadRejectedException(400, "invalid"))
        controller.releaseUploadClaim(root.id)
        assertTrue(controller.undo(root))
        assertTrue(reconstructed().getAll().isEmpty())
        assertEquals(0, countReviews())
        assertEquals(0, database.rawQuery("SELECT COUNT(*) AS n FROM osm_element_id_provider") { it.getInt("n") }.single())
    }

    @Test fun `history expiry never discards pending rejected edit`() = runBlocking<Unit> {
        val edit = addTag(6)
        reject()
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        assertEquals(0, controller.deleteSyncedOlderThan(Long.MAX_VALUE))
        assertNotNull(reconstructed().get(edit.id)!!.review)
    }

    @Test fun `review persistence failure rolls back and does not notify or alter cache`() {
        val edit = addTag(6)
        val failing = object : Database by database {
            override fun replace(table: String, values: Collection<Pair<String, Any?>>): Long {
                val id = database.replace(table, values)
                if (table == ElementEditReviewsTable.NAME) error("injected after review write")
                return id
            }
        }
        val ctrl = reconstructed(failing)
        var events = 0
        ctrl.addListener(object : ElementEditsSource.Listener {
            override fun onAddedEdit(edit: ElementEdit) {}
            override fun onSyncedEdit(edit: ElementEdit) {}
            override fun onDeletedEdits(edits: List<ElementEdit>) {}
            override fun onReviewChanged() { events++ }
        })
        ctrl.claimOldestUnsynced()
        try { assertFailsWith<IllegalStateException> { ctrl.markRejected(edit.id, ElementUploadRejectedException(400, "invalid")) } }
        finally { ctrl.releaseUploadClaim(edit.id) }
        assertEquals(0, events)
        assertNull(ctrl.get(edit.id)!!.review)
        assertEquals(0, countReviews())
        assertEquals(edit.id, ctrl.claimOldestUnsynced()!!.id)
        ctrl.releaseUploadClaim(edit.id)
    }

    @Test fun `failed retry transaction preserves review and blocks claim`() = runBlocking<Unit> {
        val edit = addTag(6)
        reject()
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        val failing = object : Database by database {
            override fun delete(table: String, where: String?, args: Array<Any>?): Int {
                val count = database.delete(table, where, args)
                if (table == ElementEditReviewsTable.NAME) error("injected after review delete")
                return count
            }
        }
        val ctrl = reconstructed(failing)
        assertFailsWith<IllegalStateException> { ctrl.retryRejectedEdit(edit.id) }
        assertNotNull(ctrl.get(edit.id)!!.review)
        assertNotNull(reconstructed().get(edit.id)!!.review)
        assertNull(ctrl.claimOldestUnsynced())
    }

    @Test fun `unexpected local exception and transport error never quarantine or discard`() = runBlocking<Unit> {
        val edit = addTag(6)
        for (error in listOf(IllegalArgumentException("local bug"), ConnectionException("network"),
            de.westnordost.streetcomplete.data.ApiClientException("HTTP 400"),
            de.westnordost.streetcomplete.data.AuthorizationException("authentication"))) {
            server.whileInFlight = { throw error }
            val actual = assertFailsWith<RuntimeException> { uploader.upload() }
            assertEquals(error::class, actual::class)
            assertEquals(error.message, actual.message)
            assertEquals(edit.action, controller.get(edit.id)!!.action)
            assertNull(controller.get(edit.id)!!.review)
            assertEquals(0, countReviews())
        }
    }

    @Test fun `cancellation during rejection commits review then releases claim`() = runBlocking<Unit> {
        val edit = addTag(6)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { started.complete(Unit); release.await(); throw ElementUploadRejectedException(400, "invalid") }
        val job = launch(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { started.await() }
            job.cancel()
        } finally { release.complete(Unit) }
        withTimeout(10000) { job.join() }
        assertNotNull(reconstructed().get(edit.id)!!.review)
        assertTrue(controller.retryRejectedEdit(edit.id))
        assertEquals(edit.id, controller.claimOldestUnsynced()!!.id)
        controller.releaseUploadClaim(edit.id)
    }

    @Test fun `unowned negative edit is never sent and independent edit continues`() = runBlocking<Unit> {
        addTagEdit(Node(-999, p(), emptyMap(), 1))
        addTag(7)
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        assertEquals(1, server.calls)
        assertEquals("yes", server.nodes[7]!!.tags["lit"])
        assertEquals(-999, controller.getAllUnsynced().single().action.elementKeys.single().id)
    }

    @Test fun `payload guard checks nested references and permits same diff creations`() {
        assertFailsWith<UnresolvedElementReferenceException> {
            MapDataChanges(modifications = listOf(Way(5, listOf(-99)))).requireResolvedReferences()
        }
        assertFailsWith<UnresolvedElementReferenceException> {
            MapDataChanges(creations = listOf(Relation(-2, listOf(RelationMember(ElementType.NODE, -99, ""))))).requireResolvedReferences()
        }
        MapDataChanges(creations = listOf(Node(-1, p()), Way(-2, listOf(-1)),
            Relation(-3, listOf(RelationMember(ElementType.WAY, -2, ""))))).requireResolvedReferences()
        assertFailsWith<UnresolvedElementReferenceException> {
            MapDataChanges(modifications = listOf(Node(-1, p()))).requireResolvedReferences()
        }
    }

    @Test fun `diagnostic is bounded and removes markup credentials URLs emails and controls`() {
        val sanitized = sanitizeRejectionReason("<osm secret='private'/>invalid\nBearer secret https://example.invalid/?token=secret user@example.invalid " + "x".repeat(2000))
        assertTrue(sanitized.length <= 512)
        assertFalse(sanitized.contains("secret"))
        assertFalse(sanitized.contains("example.invalid"))
        assertFalse(sanitized.contains('\n'))
        val credentials = sanitizeRejectionReason("Invalid tag\nBasic c2VjcmV0\npassword = 'two words'\n{\"access_token\":\"private\"}")
        assertFalse(credentials.contains("c2VjcmV0"))
        assertFalse(credentials.contains("two words"))
        assertFalse(credentials.contains("private"))
    }
    @Test fun `concurrent requests preserve one rejection and upload independent edit once`() = runBlocking<Unit> {
        addTag(6)
        addTag(7)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        server.whileInFlight = { entered.complete(Unit); release.await(); throw ElementUploadRejectedException(400, "invalid") }
        val first = async(Dispatchers.Default) { runCatching { uploader.upload() } }
        try {
            withTimeout(10000) { entered.await() }
            val second = async(Dispatchers.Default) { runCatching { uploader.upload() } }
            release.complete(Unit)
            assertTrue(withTimeout(10000) { first.await() }.exceptionOrNull() is ElementEditsNeedReviewException)
            assertTrue(withTimeout(10000) { second.await() }.exceptionOrNull() is ElementEditsNeedReviewException)
        } finally { release.complete(Unit) }
        assertEquals(2, server.calls)
        assertEquals(1, countReviews())
        assertEquals("yes", server.nodes[7]!!.tags["lit"])
    }

    @Test fun `review survives reopening the committed SQLite file`() = runBlocking<Unit> {
        val edit = addTag(6)
        reject("persistent diagnosis")
        assertFailsWith<ElementEditsNeedReviewException> { uploader.upload() }
        val file = database.rawQuery("PRAGMA database_list") { it.getString("file") }.first()
        assertTrue(file.contains("streetcomplete_test_"))
        val reopened = DatabaseImpl(BundledSQLiteDriver().open(file))
        try {
            reopened.initialize(de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator)
            val ctrl = reconstructed(reopened)
            assertEquals("persistent diagnosis", ctrl.get(edit.id)!!.review!!.reason)
            assertNull(ctrl.claimOldestUnsynced())
        } finally { reopened.close() }
    }

    @Test fun `single uploader rejects nested unresolved reference before API request`() = runBlocking<Unit> {
        val action = mock<ElementEditAction> {
            every { createUpdates(any(), any()) } returns MapDataChanges(modifications = listOf(Way(5, listOf(-99))))
        }
        assertFailsWith<UnresolvedElementReferenceException> {
            singleUploader.upload(edit(action = action)) { ElementIdProvider(emptyList()) }
        }
        assertEquals(0, server.calls)
    }

}
