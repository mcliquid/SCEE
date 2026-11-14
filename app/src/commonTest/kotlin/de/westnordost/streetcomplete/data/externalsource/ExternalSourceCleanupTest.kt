package de.westnordost.streetcomplete.data.externalsource

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsControllerImpl
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsDao
import de.westnordost.streetcomplete.data.osm.edits.elementEditIdsKeptAfterCleanup
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExternalSourceCleanupTest : StreetCompleteDatabaseTestCase() {
    private lateinit var database: Database
    private lateinit var dao: ExternalSourceDao

    override fun onDatabaseInitialized(database: Database) {
        this.database = database
        dao = ExternalSourceDao(database)
    }

    @Test fun `cleanup keeps every edit that was not deleted`() {
        val oldSynced = edit(id = 1, timestamp = 10, isSynced = true)
        val oldUnsynced = edit(id = 2, timestamp = 10, isSynced = false)
        val fresh = edit(id = 3, timestamp = 100, isSynced = false)
        val deleted = listOf(oldSynced)
        assertEquals(
            listOf(oldUnsynced.id, fresh.id),
            elementEditIdsKeptAfterCleanup(listOf(oldSynced, oldUnsynced, fresh), deleted),
        )
    }

    @Test fun `only orphaned external source links are removed`() {
        val keptUnsynced = ExternalSourceQuestKey("issue-old", "osmose")
        val keptFresh = ExternalSourceQuestKey("issue-new", "osmose")
        dao.addElementEdit(ExternalSourceQuestKey("issue-synced", "osmose"), 1)
        dao.addElementEdit(keptUnsynced, 2)
        dao.addElementEdit(keptFresh, 3)
        dao.addElementEdit(ExternalSourceQuestKey("issue-orphan", "osmose"), 9)

        dao.deleteAllExceptForElementEdits(listOf(2, 3))

        assertNull(dao.getKeyForElementEdit(1))
        assertEquals(keptUnsynced, dao.getKeyForElementEdit(2))
        assertEquals(keptFresh, dao.getKeyForElementEdit(3))
        assertNull(dao.getKeyForElementEdit(9))
    }

    @Test fun `an empty keep list removes every link`() {
        dao.addElementEdit(ExternalSourceQuestKey("issue", "osmose"), 1)
        dao.deleteAllExceptForElementEdits(emptyList())
        assertNull(dao.getKeyForElementEdit(1))
    }

    @Test fun `cleanup still drops orphans when no synced edit is deleted`() {
        val unsynced = edit(id = 2, timestamp = 10, isSynced = false)
        val synced = edit(id = 3, timestamp = 100, isSynced = true)
        val db = mock<ElementEditsDao> { every { getAll() } returns listOf(unsynced, synced) }
        var kept: Collection<Long>? = null
        val ctrl = ElementEditsControllerImpl(db, mock(), mock(), database, dao) { kept = it }
        assertEquals(0, ctrl.deleteSyncedOlderThan(50))
        assertEquals(listOf(unsynced.id, synced.id), kept?.toList())

        val unsyncedKey = ExternalSourceQuestKey("unsynced", "osmose")
        val syncedKey = ExternalSourceQuestKey("synced", "osmose")
        dao.addElementEdit(unsyncedKey, unsynced.id)
        dao.addElementEdit(syncedKey, synced.id)
        dao.addElementEdit(ExternalSourceQuestKey("orphan", "osmose"), 9)
        dao.deleteAllExceptForElementEdits(kept!!)
        assertEquals(unsyncedKey, dao.getKeyForElementEdit(unsynced.id))
        assertEquals(syncedKey, dao.getKeyForElementEdit(synced.id))
        assertNull(dao.getKeyForElementEdit(9))
    }

    @Test fun `an empty edit table drops every external source link`() {
        val db = mock<ElementEditsDao> { every { getAll() } returns emptyList() }
        var kept: Collection<Long>? = null
        val ctrl = ElementEditsControllerImpl(db, mock(), mock(), database, dao) { kept = it }
        assertEquals(0, ctrl.deleteSyncedOlderThan(50))
        assertEquals(emptyList(), kept?.toList())
        dao.addElementEdit(ExternalSourceQuestKey("orphan", "osmose"), 4)
        dao.deleteAllExceptForElementEdits(kept!!)
        assertNull(dao.getKeyForElementEdit(4))
    }
}
