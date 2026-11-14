package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.*
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceDao
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestTables
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.*
import kotlin.test.*

class ElementEditTransactionsTest : StreetCompleteDatabaseTestCase() {
    private lateinit var db: FailingDatabase
    private lateinit var edits: ElementEditsDao
    private lateinit var elements: EditElementsDao
    private lateinit var ids: ElementIdProviderDao
    private lateinit var controller: ElementEditsControllerImpl
    private val events = mutableListOf<String>()

    override fun onDatabaseInitialized(database: Database) {
        db = FailingDatabase(database)
        edits = ElementEditsDao(db, AllEditTypes(emptyList(), listOf(TestElementEditType)), TestElementEditType, TestElementEditType)
        elements = EditElementsDao(db)
        ids = ElementIdProviderDao(db)
        controller = ElementEditsControllerImpl(edits, elements, ids, db, ExternalSourceDao(db))
        controller.addListener(object : ElementEditsSource.Listener {
            override fun onAddedEdit(edit: ElementEdit) { assertFalse(db.inTransaction); events += "added" }
            override fun onSyncedEdit(edit: ElementEdit) { assertFalse(db.inTransaction); events += "synced" }
            override fun onDeletedEdits(edits: List<ElementEdit>) {
                assertFalse(db.inTransaction)
                // Deletion callbacks retain the old ID provider without retaining database rows.
                for (edit in edits) assertEquals(0, count(ElementIdProviderTable.NAME, edit.id))
                events += "deleted"
            }
        })
    }

    @Test fun `add rolls back after each write and retries`() {
        checkFailures(3) { addCreation() }
        assertEquals(listOf("added"), events)
        assertEquals(1, edits.getAll().size)
    }

    @Test fun `delete rolls back after each write and retries`() {
        addCreation()
        val edit = controller.getAll().single()
        checkFailures(4) { controller.undo(edit) }
        assertEquals(listOf("deleted"), events)
        assertTrue(edits.getAll().isEmpty())
    }

    @Test fun `synced undo deletes and queues revert atomically`() {
        addCreation()
        val edit = controller.getAll().single()
        controller.markSynced(edit, MapDataUpdates(idUpdates = listOf(ElementIdUpdate(ElementType.NODE, -1, 42))))
        val synced = controller.get(edit.id)!!
        checkFailures(6) { assertTrue(controller.undo(synced)) }
        assertEquals(listOf("deleted", "added"), events)
        assertFalse(controller.getAll().single().isSynced)
    }

    @Test fun `sync rolls back dependent action references status and ID mapping`() {
        addCreation()
        val created = controller.getAll().single()
        val dependent = edit(id = 0, action = de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction(
            node(id = -1), de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges(setOf(
                de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd("name", "test")))))
        controller.add(dependent.type, dependent.originalGeometry, dependent.source, dependent.action, true, null)
        checkFailures(5) {
            controller.markSynced(created, MapDataUpdates(idUpdates = listOf(ElementIdUpdate(ElementType.NODE, -1, 42))))
        }
        assertEquals(listOf("synced"), events)
        assertTrue(controller.get(created.id)!!.isSynced)
        assertEquals(listOf(ElementKey(ElementType.NODE, 42)), ids.get(created.id).getAll())
        assertEquals(listOf(ElementKey(ElementType.NODE, 42)), controller.getAllUnsynced().single().action.elementKeys)
    }

    @Test fun `external source link is atomic with edit and remains available during deletion callback`() {
        val key = ExternalSourceQuestKey("issue", "osmose")
        checkFailures(4) {
            controller.add(TestElementEditType, pGeom(), "survey",
                CreateNodeAction(LatLon(1.0, 2.0), emptyMap()), true, key)
        }
        val edit = controller.getAll().single()
        assertEquals(key, controller.getExternalSourceQuestKey(edit.id))
        controller.addListener(object : ElementEditsSource.Listener {
            override fun onAddedEdit(edit: ElementEdit) {}
            override fun onSyncedEdit(edit: ElementEdit) {}
            override fun onDeletedEdits(edits: List<ElementEdit>) {
                assertEquals(key, controller.getExternalSourceQuestKey(edit.id))
                assertEquals(1, controller.getIdProvider(edit.id).getAll().size)
            }
        })
        checkFailures(4) { controller.undo(edit) }
        assertNull(controller.getExternalSourceQuestKey(edit.id))
    }

    @Test fun `history cleanup rolls back all related rows`() {
        addCreation()
        val edit = controller.getAll().single()
        controller.markSynced(edit, MapDataUpdates())
        checkFailures(4) { controller.deleteSyncedOlderThan(Long.MAX_VALUE) }
        assertTrue(controller.getAll().isEmpty())
    }

    private fun addCreation() = controller.add(TestElementEditType, pGeom(), "survey",
        CreateNodeAction(LatLon(1.0, 2.0), mapOf("amenity" to "bench")), true, null)

    private fun count(table: String, id: Long? = null) = db.rawQuery(
        "SELECT COUNT(*) AS n FROM $table" + if (id == null) "" else " WHERE edit_id = $id"
    ) { it.getInt("n") }.single()

    private fun snapshot(): List<Any> = listOf(
        edits.getAll(), controller.getAll(),
        db.rawQuery("SELECT * FROM ${ExternalSourceQuestTables.NAME_EDITS} ORDER BY edit_id") {
            listOf(it.getLong("edit_id"), it.getString("id"), it.getString("source"))
        },
        db.rawQuery("SELECT * FROM ${EditElementsTable.NAME} ORDER BY edit_id, element_id") {
            listOf(it.getLong("edit_id"), it.getString("element_type"), it.getLong("element_id"))
        },
        db.rawQuery("SELECT * FROM ${ElementIdProviderTable.NAME} ORDER BY id") {
            listOf(it.getLong("edit_id"), it.getString("element_type"), it.getLong("id"))
        },
    )

    private fun checkFailures(writes: Int, operation: () -> Unit) {
        val before = snapshot()
        events.clear()
        for (write in 1..writes) {
            db.remaining = write
            assertFailsWith<InjectedFailure>("write $write") { operation() }
            assertEquals(before, snapshot(), "write $write must roll back DB and cache")
            assertTrue(events.isEmpty(), "rolled back operation must not notify")
        }
        db.remaining = Int.MAX_VALUE
        operation()
    }
}

private class InjectedFailure : RuntimeException()

private class FailingDatabase(private val delegate: Database) : Database by delegate {
    var remaining = Int.MAX_VALUE
    var inTransaction = false
    private fun <T> afterWrite(result: T): T {
        if (--remaining == 0) throw InjectedFailure()
        return result
    }
    override fun <T> transaction(block: () -> T): T = delegate.transaction {
        val previous = inTransaction
        inTransaction = true
        try { block() } finally { inTransaction = previous }
    }
    override fun insert(table: String, values: Collection<Pair<String, Any?>>, conflictAlgorithm: ConflictAlgorithm?): Long =
        afterWrite(delegate.insert(table, values, conflictAlgorithm))
    override fun replace(table: String, values: Collection<Pair<String, Any?>>): Long =
        afterWrite(delegate.replace(table, values))
    override fun insertMany(table: String, columnNames: Array<String>, valuesList: Iterable<Array<Any?>>, conflictAlgorithm: ConflictAlgorithm?): List<Long> =
        afterWrite(delegate.insertMany(table, columnNames, valuesList, conflictAlgorithm))
    override fun delete(table: String, where: String?, args: Array<Any>?): Int =
        afterWrite(delegate.delete(table, where, args))
    override fun update(table: String, values: Collection<Pair<String, Any?>>, where: String?, args: Array<Any>?, conflictAlgorithm: ConflictAlgorithm?): Int =
        afterWrite(delegate.update(table, values, where, args, conflictAlgorithm))
}
