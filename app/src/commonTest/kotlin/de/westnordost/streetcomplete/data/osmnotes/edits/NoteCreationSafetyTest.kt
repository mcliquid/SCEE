package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osmnotes.NoteController
import de.westnordost.streetcomplete.data.osmnotes.NotesApiClient
import de.westnordost.streetcomplete.data.osmnotes.PhotoServiceApiClient
import de.westnordost.streetcomplete.testutils.note
import de.westnordost.streetcomplete.testutils.noteEdit
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.io.files.SystemFileSystem
import de.westnordost.streetcomplete.util.ktx.format
import io.ktor.client.engine.mock.respond
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real SQLite queue and journal, controlled API: commit on the server then lose the reply. */
class NoteCreationSafetyTest : StreetCompleteDatabaseTestCase() {
    private lateinit var db: Database
    private lateinit var dao: NoteEditsDao
    private lateinit var controller: NoteEditsController
    private lateinit var api: NotesApiClient
    private lateinit var photos: PhotoServiceApiClient
    private var failSync = false
    private var failAcknowledgement = false
    private var requests = 0
    private val serverNotes = mutableListOf<Long>()

    override fun onDatabaseInitialized(database: Database) {
        db = object : Database by database {
            override fun update(table: String, values: Collection<Pair<String, Any?>>, where: String?,
                args: Array<Any>?, conflictAlgorithm: de.westnordost.streetcomplete.data.ConflictAlgorithm?): Int {
                if (failSync && table == NoteEditsTable.NAME && values.any { it.first == NoteEditsTable.Columns.IS_SYNCED }) error("local sync write failed")
                if (failAcknowledgement && table == NoteCreationAttemptsTable.NAME) error("ack write failed")
                return database.update(table, values, where, args, conflictAlgorithm)
            }
        }
        dao = NoteEditsDao(db)
        controller = NoteEditsControllerImpl(dao, SystemFileSystem)
        api = mock {
            everySuspend { create(any(), any()) } calls {
                requests++
                serverNotes += requests.toLong()
                note(requests.toLong())
            }
            everySuspend { get(any()) } calls { note(1) }
            everySuspend { lookup(any()) } calls { (id: Long) ->
                get(id)?.let { de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Available(it) }
                    ?: de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Unavailable.UNKNOWN
            }
        }
        photos = mock { everySuspend { upload(any()) } returns listOf("https://example.invalid/photo.jpg") }
    }

    private fun uploader() = NoteEditsUploader(controller, mock<NoteController>(), mock(), api, mock(), photos)
    private fun pending(images: List<String> = emptyList()): NoteEdit {
        val edit = noteEdit(noteId = 0, action = NoteEditAction.CREATE, text = "same text", imagePaths = images)
        assertTrue(dao.add(edit))
        return edit
    }

    @Test fun `uncertain creation blocks dependents but independent notes continue`() = runBlocking {
        val first = pending()
        assertTrue(dao.beginCreation(first.id, "prepared"))
        val dependent = noteEdit(noteId = first.noteId, action = NoteEditAction.COMMENT)
        dao.add(dependent)
        val independent = pending()
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        assertTrue(dao.get(independent.id)!!.isSynced)
        assertFalse(dao.get(dependent.id)!!.isSynced)
        assertEquals(1, requests)
    }

    @Test fun `manual link rejects a different note without changing the journal`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        assertFailsWith<IllegalArgumentException> { uploader().resolveCreation(edit.id, 1) }
        assertNull(dao.getCreationAttempt(edit.id)!!.noteId)
        assertFalse(dao.get(edit.id)!!.isSynced)
        assertEquals(0, requests)
    }

    @Test fun `manual link accepts exact text author and rounded coordinates without POST`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        val linked = note(100).copy(position = edit.position, comments = listOf(
            de.westnordost.streetcomplete.data.osmnotes.NoteComment(0,
                de.westnordost.streetcomplete.data.osmnotes.NoteComment.Action.OPENED, "prepared",
                de.westnordost.streetcomplete.data.user.User(0, "test"))))
        everySuspend { api.get(100) } returns linked
        uploader().resolveCreation(edit.id, 100)
        assertTrue(dao.get(edit.id)!!.isSynced)
        assertEquals(100L, dao.getCreationAttempt(edit.id)!!.noteId)
        assertEquals(0, requests)
    }

    @Test fun `ordinary undo cannot delete an uncertain CREATE or its journal`() {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        assertFalse(controller.undo(edit))
        assertNotNull(dao.get(edit.id))
        assertNotNull(dao.getCreationAttempt(edit.id))
    }

    @Test fun `confirmed discard removes only the selected uncertain edit`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        val other = pending()
        uploader().discardUncertainCreation(edit.id)
        assertNull(dao.get(edit.id))
        assertNull(dao.getCreationAttempt(edit.id))
        assertNotNull(dao.get(other.id))
        assertEquals(0, requests)
    }

    @Test fun `manual link checks text author and location separately`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        val comment = de.westnordost.streetcomplete.data.osmnotes.NoteComment(0,
            de.westnordost.streetcomplete.data.osmnotes.NoteComment.Action.OPENED, "prepared",
            de.westnordost.streetcomplete.data.user.User(0, "test"))
        val exact = note(100).copy(position = edit.position, comments = listOf(comment))
        for (mismatch in listOf(exact.copy(id = 101), exact.copy(comments = listOf(comment.copy(text = "different"))),
            exact.copy(comments = listOf(comment.copy(user = de.westnordost.streetcomplete.data.user.User(99, "other")))),
            exact.copy(position = de.westnordost.streetcomplete.data.osm.mapdata.LatLon(9.0, 9.0)))) {
            everySuspend { api.get(100) } returns mismatch
            assertFailsWith<IllegalArgumentException> { uploader().resolveCreation(edit.id, 100) }
            assertNull(dao.getCreationAttempt(edit.id)!!.noteId)
            assertFalse(dao.get(edit.id)!!.isSynced)
        }
        assertEquals(0, requests)
    }

    @Test fun `discard refuses to silently remove dependent comments`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        dao.add(noteEdit(noteId = edit.noteId, action = NoteEditAction.COMMENT))
        assertFailsWith<IllegalStateException> { uploader().discardUncertainCreation(edit.id) }
        assertNotNull(dao.get(edit.id))
        assertEquals(2, dao.getUnsyncedCount())
    }

    @Test fun `version 23 upgrade preserves pending notes and creates an empty journal`() {
        val edit = pending(listOf("photo.jpg"))
        val before = dao.get(edit.id)
        db.exec("DROP TABLE ${NoteCreationAttemptsTable.NAME}")
        db.exec("DROP TABLE scee_schema_tracks")
        db.exec("DROP TABLE IF EXISTS osm_element_edit_reviews")
        db.exec("PRAGMA user_version = 23")
        db.initialize(de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator)
        assertEquals(before, dao.get(edit.id))
        assertNull(dao.getCreationAttempt(edit.id))
        assertTrue(dao.beginCreation(edit.id, "prepared"))
    }

    @Test fun `reservation survives a separate SQLite connection without in memory state`() {
        val edit = pending()
        assertTrue(dao.beginCreation(edit.id, "prepared"))
        val file = db.rawQuery("PRAGMA database_list") { it.getString("file") }.first()
        val reopened = de.westnordost.streetcomplete.data.DatabaseImpl(
            androidx.sqlite.driver.bundled.BundledSQLiteDriver().open(file)
        )
        try {
            assertEquals(NoteCreationAttempt("prepared", null), NoteEditsDao(reopened).getCreationAttempt(edit.id))
            assertFalse(NoteEditsDao(reopened).beginCreation(edit.id, "duplicate"))
        } finally {
            reopened.close()
        }
    }

    @Test fun `deleted edits also remove their journal`() {
        val edit = pending()
        assertTrue(dao.beginCreation(edit.id, "prepared"))
        assertTrue(dao.delete(edit.id))
        assertNull(dao.getCreationAttempt(edit.id))
        assertFalse(dao.beginCreation(edit.id, "stale uploader"))
    }

    @Test fun `normal success and repeated background sync create once`() = runBlocking {
        val edit = pending()
        repeat(30) { uploader().upload() }
        assertEquals(1, requests)
        assertTrue(dao.get(edit.id)!!.isSynced)
        assertEquals(1L, dao.get(edit.id)!!.noteId)
    }

    @Test fun `explicit authorization rejection permits retry after login`() = runBlocking {
        val edit = pending()
        everySuspend { api.create(any(), any()) } calls { throw de.westnordost.streetcomplete.data.AuthorizationException() }
        assertFailsWith<de.westnordost.streetcomplete.data.AuthorizationException> { uploader().upload() }
        assertNull(dao.getCreationAttempt(edit.id))
        assertNotNull(dao.get(edit.id))
        everySuspend { api.create(any(), any()) } calls { requests++; note(1) }
        uploader().upload()
        assertEquals(1, requests)
        assertTrue(dao.get(edit.id)!!.isSynced)
    }

    @Test fun `timeout before server acceptance blocks blind retries and preserves edit`() = runBlocking {
        val edit = pending()
        everySuspend { api.create(any(), any()) } calls { requests++; throw java.net.SocketTimeoutException() }
        repeat(30) { assertFailsWith<NoteCreationUncertainException> { uploader().upload() } }
        assertEquals(1, requests)
        assertTrue(serverNotes.isEmpty())
        assertFalse(dao.get(edit.id)!!.isSynced)
        assertEquals("same text", dao.getCreationAttempt(edit.id)!!.text)
    }

    @Test fun `server commit followed by lost response never creates a second note after restart`() = runBlocking {
        val edit = pending(listOf("missing-photo.jpg"))
        everySuspend { api.create(any(), any()) } calls {
            requests++
            serverNotes += 99L
            throw java.net.SocketTimeoutException("reply lost after commit")
        }
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        controller = NoteEditsControllerImpl(NoteEditsDao(db), SystemFileSystem)
        repeat(30) { assertFailsWith<NoteCreationUncertainException> { uploader().upload() } }
        assertEquals(listOf(99L), serverNotes)
        assertEquals(1, requests)
        assertEquals(listOf("missing-photo.jpg"), dao.get(edit.id)!!.imagePaths)
        assertTrue(dao.getCreationAttempt(edit.id)!!.text.contains("https://example.invalid/photo.jpg"))
        dev.mokkery.verifySuspend(dev.mokkery.verify.VerifyMode.exactly(1)) { photos.upload(any()) }
    }

    @Test fun `local sync failure after acknowledged creation resumes by server ID`() = runBlocking {
        val edit = pending()
        failSync = true
        assertFailsWith<IllegalStateException> { uploader().upload() }
        assertFalse(dao.get(edit.id)!!.isSynced)
        assertEquals(edit.noteId, dao.get(edit.id)!!.noteId)
        assertEquals(1L, dao.getCreationAttempt(edit.id)!!.noteId)
        failSync = false
        controller = NoteEditsControllerImpl(NoteEditsDao(db), SystemFileSystem)
        uploader().upload()
        assertEquals(1, requests)
        assertTrue(dao.get(edit.id)!!.isSynced)
    }

    @Test fun `failure persisting server acknowledgement leaves uncertainty without retry`() = runBlocking {
        val edit = pending()
        failAcknowledgement = true
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        failAcknowledgement = false
        repeat(3) { assertFailsWith<NoteCreationUncertainException> { uploader().upload() } }
        assertEquals(1, requests)
        assertNull(dao.getCreationAttempt(edit.id)!!.noteId)
    }

    @Test fun `two concurrent triggers serialize the same pending note`() = runBlocking {
        pending()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        everySuspend { api.create(any(), any()) } calls {
            requests++
            started.complete(Unit)
            finish.await()
            note(1)
        }
        val upload = uploader()
        val first = async { upload.upload() }
        started.await()
        val second = async { upload.upload() }
        finish.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, requests)
    }

    @Test fun `a different uploader cannot POST an already claimed edit`() = runBlocking {
        val edit = pending()
        assertTrue(dao.beginCreation(edit.id, "prepared"))
        assertFalse(NoteEditsDao(db).beginCreation(edit.id, "other"))
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        assertEquals(0, requests)
        assertEquals("prepared", dao.getCreationAttempt(edit.id)!!.text)
    }

    @Test fun `two legitimate notes with identical location and text are independent`() = runBlocking {
        pending()
        pending()
        uploader().upload()
        assertEquals(2, requests)
        assertEquals(0, dao.getUnsyncedCount())
    }

    @Test fun `process interruption after reservation is uncertain on next uploader`() = runBlocking {
        val edit = pending()
        assertTrue(dao.beginCreation(edit.id, "prepared text"))
        controller = NoteEditsControllerImpl(NoteEditsDao(db), SystemFileSystem)
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        assertNotNull(dao.get(edit.id))
        assertEquals(0, requests)
    }

    @Test fun `photo activation marks the local edit ID rather than server note ID`() = runBlocking {
        val edit = pending(listOf("missing-photo.jpg"))
        everySuspend { api.create(any(), any()) } calls { requests++; note(9000) }
        uploader().upload()
        assertEquals(9000L, dao.get(edit.id)!!.noteId)
        assertFalse(dao.get(edit.id)!!.imagesNeedActivation)
        dev.mokkery.verifySuspend { photos.activate(9000) }
    }

    @Test fun `cancellation during CREATE still saves acknowledgement and completes current edit`() = runBlocking {
        val edit = pending()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        everySuspend { api.create(any(), any()) } calls {
            requests++
            started.complete(Unit)
            finish.await()
            note(1)
        }
        val job = launch { uploader().upload() }
        started.await()
        job.cancel()
        finish.complete(Unit)
        job.join()
        assertTrue(dao.get(edit.id)!!.isSynced)
        assertEquals(1L, dao.getCreationAttempt(edit.id)!!.noteId)
        assertEquals(1, requests)
    }

    @Test fun `photo failure before reservation allows retry`() = runBlocking {
        val edit = pending(listOf("missing-photo.jpg"))
        everySuspend { photos.upload(any()) } calls { throw IllegalStateException("photo failed") }
        assertFailsWith<IllegalStateException> { uploader().upload() }
        assertNull(dao.getCreationAttempt(edit.id))
        everySuspend { photos.upload(any()) } returns listOf("photo")
        uploader().upload()
        assertEquals(1, requests)
    }

    @Test fun `listener failure after success cannot reupload note`() = runBlocking {
        val edit = pending()
        val upload = uploader()
        upload.uploadedChangeListener = object : de.westnordost.streetcomplete.data.upload.OnUploadedChangeListener {
            override fun onUploaded(editType: String, at: de.westnordost.streetcomplete.data.osm.mapdata.LatLon) { error("listener failed") }
            override fun onDiscarded(editType: String, at: de.westnordost.streetcomplete.data.osm.mapdata.LatLon) = Unit
        }
        assertFailsWith<IllegalStateException> { upload.upload() }
        uploader().upload()
        assertTrue(dao.get(edit.id)!!.isSynced)
        assertEquals(1, requests)
    }
    @Test fun `manual recovery uses truncated positive and negative wire coordinates`() = runBlocking {
        for (position in listOf(
            de.westnordost.streetcomplete.data.osm.mapdata.LatLon(52.52000006, 13.40000009),
            de.westnordost.streetcomplete.data.osm.mapdata.LatLon(-52.52000006, -13.40000009),
            de.westnordost.streetcomplete.data.osm.mapdata.LatLon(52.520000099, 13.400000099),
            de.westnordost.streetcomplete.data.osm.mapdata.LatLon(52.520000101, 13.400000101),
        )) {
            val edit = noteEdit(noteId = 0, action = NoteEditAction.CREATE, pos = position)
            dao.add(edit)
            dao.beginCreation(edit.id, "prepared")
            val wire = de.westnordost.streetcomplete.data.osm.mapdata.LatLon(
                position.latitude.format(7).toDouble(), position.longitude.format(7).toDouble())
            val linked = note(100).copy(position = wire, comments = listOf(
                de.westnordost.streetcomplete.data.osmnotes.NoteComment(0,
                    de.westnordost.streetcomplete.data.osmnotes.NoteComment.Action.OPENED, "prepared",
                    de.westnordost.streetcomplete.data.user.User(0, "test"))))
            everySuspend { api.get(100) } returns linked.copy(position = wire.copy(latitude = wire.latitude + 0.0000002))
            assertFailsWith<IllegalArgumentException> { uploader().resolveCreation(edit.id, 100) }
            assertNull(dao.getCreationAttempt(edit.id)!!.noteId)
            everySuspend { api.get(100) } returns linked
            uploader().resolveCreation(edit.id, 100)
            assertTrue(dao.get(edit.id)!!.isSynced)
        }
        assertEquals(0, requests)
    }

    @Test fun `acknowledged unavailable CREATE is retained until confirmed discard`() = runBlocking {
        for (reason in listOf(
            de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Unavailable.MISSING,
            de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Unavailable.HIDDEN,
        )) {
            val edit = pending()
            dao.beginCreation(edit.id, "prepared")
            dao.acknowledgeCreation(edit.id, 100)
            everySuspend { api.lookup(100) } returns reason
            val error = assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
            assertEquals(reason, (error.cause as de.westnordost.streetcomplete.data.osmnotes.NoteUnavailableException).reason)
            assertNotNull(dao.get(edit.id))
            assertFalse(controller.undo(edit))
            uploader().discardUncertainCreation(edit.id)
            assertNull(dao.get(edit.id))
        }
        assertEquals(0, requests)
    }

    @Test fun `temporarily unreachable acknowledged note cannot be discarded or reposted`() = runBlocking {
        val edit = pending()
        dao.beginCreation(edit.id, "prepared")
        dao.acknowledgeCreation(edit.id, 100)
        everySuspend { api.lookup(100) } calls { throw IllegalStateException("offline") }
        assertFailsWith<IllegalStateException> { uploader().upload() }
        assertFailsWith<IllegalStateException> { uploader().discardUncertainCreation(edit.id) }
        assertNotNull(dao.get(edit.id))
        assertEquals(100L, dao.getCreationAttempt(edit.id)!!.noteId)
        assertEquals(0, requests)
    }

    @Test fun `lost COMMENT and CLOSE replies never cause another POST after controller restart`() = runBlocking {
        for (action in listOf(NoteEditAction.COMMENT, NoteEditAction.CLOSE)) {
            val edit = noteEdit(noteId = 50, action = action, text = "identical")
            dao.add(edit)
            everySuspend { api.comment(any(), any()) } calls { requests++; error("accepted then lost reply") }
            everySuspend { api.close(any(), any()) } calls { requests++; error("accepted then lost reply") }
            assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
            controller = NoteEditsControllerImpl(NoteEditsDao(db), SystemFileSystem)
            repeat(3) { assertFailsWith<NoteCreationUncertainException> { uploader().upload() } }
            assertNotNull(dao.get(edit.id))
            assertFalse(controller.undo(edit))
            uploader().discardUncertainCreation(edit.id)
        }
        assertEquals(2, requests)
    }

    @Test fun `two identical comments stay distinct and block subsequent CLOSE only on their note`() = runBlocking {
        val first = noteEdit(noteId = 50, action = NoteEditAction.COMMENT, text = "identical")
        val second = first.copy(id = 0)
        val close = noteEdit(noteId = 50, action = NoteEditAction.CLOSE)
        val independent = noteEdit(noteId = 51, action = NoteEditAction.COMMENT)
        listOf(first, second, close, independent).forEach { dao.add(it) }
        dao.beginCreation(first.id, "identical")
        everySuspend { api.comment(any(), any()) } calls { (id: Long, _: String) -> requests++; note(id) }
        everySuspend { api.close(any(), any()) } calls { (id: Long, _: String) -> requests++; note(id) }
        assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
        assertTrue(dao.get(independent.id)!!.isSynced)
        assertFalse(dao.get(second.id)!!.isSynced)
        assertFalse(dao.get(close.id)!!.isSynced)
        assertEquals(1, requests)
        uploader().discardUncertainCreation(first.id)
        uploader().upload()
        assertTrue(dao.get(second.id)!!.isSynced)
        assertTrue(dao.get(close.id)!!.isSynced)
        assertEquals(3, requests)
    }

    @Test fun `concurrent COMMENT triggers issue only one POST`() = runBlocking {
        val edit = noteEdit(noteId = 50, action = NoteEditAction.COMMENT)
        dao.add(edit)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        everySuspend { api.comment(any(), any()) } calls {
            requests++
            started.complete(Unit)
            finish.await()
            note(50)
        }
        val upload = uploader()
        val first = async { upload.upload() }
        started.await()
        val second = async { upload.upload() }
        finish.complete(Unit)
        first.await()
        second.await()
        assertEquals(1, requests)
        assertTrue(dao.get(edit.id)!!.isSynced)
    }

    @Test fun `real HTTP rejection mapping only releases definitive reservations`() = runBlocking {
        val definitive = setOf(400, 401, 403, 404, 405, 409, 410, 413, 414)
        for (status in definitive + setOf(406, 408, 429, 500, 502)) {
            val edit = pending()
            val engine = io.ktor.client.engine.mock.MockEngine {
                requests++
                respond("rejected", io.ktor.http.HttpStatusCode.fromValue(status))
            }
            val http = io.ktor.client.HttpClient(engine)
            api = de.westnordost.streetcomplete.data.osmnotes.NotesApiClientImpl(http, "https://mock.invalid/",
                object : de.westnordost.streetcomplete.data.user.UserAccessTokenSource {
                    override val accessToken: String? = null
                }, de.westnordost.streetcomplete.data.osmnotes.NotesApiParser())
            try {
                val failure = assertFailsWith<Exception> { uploader().upload() }
                assertNotNull(dao.get(edit.id))
                if (status in definitive) {
                    assertNull(dao.getCreationAttempt(edit.id), "HTTP $status")
                    assertTrue(failure is NoteWriteRejectedException || failure is de.westnordost.streetcomplete.data.AuthorizationException)
                    assertTrue(controller.undo(edit))
                } else {
                    assertTrue(failure is NoteCreationUncertainException)
                    assertNotNull(dao.getCreationAttempt(edit.id), "HTTP $status")
                    val before = requests
                    assertFailsWith<NoteCreationUncertainException> { uploader().upload() }
                    assertEquals(before, requests)
                    uploader().discardUncertainCreation(edit.id)
                }
            } finally { http.close() }
        }
    }

    @Test fun `HTTP lookup distinguishes missing hidden and temporary failure`() = runBlocking {
        for (status in listOf(404, 410, 500)) {
            val http = io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine {
                respond("unavailable", io.ktor.http.HttpStatusCode.fromValue(status))
            })
            val client = de.westnordost.streetcomplete.data.osmnotes.NotesApiClientImpl(http, "https://mock.invalid/",
                object : de.westnordost.streetcomplete.data.user.UserAccessTokenSource {
                    override val accessToken: String? = null
                }, de.westnordost.streetcomplete.data.osmnotes.NotesApiParser())
            try {
                if (status == 500) assertFailsWith<de.westnordost.streetcomplete.data.ConnectionException> { client.lookup(100) }
                else assertEquals(if (status == 404) de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Unavailable.MISSING
                    else de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Unavailable.HIDDEN, client.lookup(100))
            } finally { http.close() }
        }
    }

}
