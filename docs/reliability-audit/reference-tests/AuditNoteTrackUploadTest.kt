package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osmnotes.NoteController
import de.westnordost.streetcomplete.data.osmnotes.NotesApiClient
import de.westnordost.streetcomplete.data.osmnotes.PhotoServiceApiClient
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.osmtracks.TracksApiClient
import de.westnordost.streetcomplete.data.user.UserDataSource
import de.westnordost.streetcomplete.testutils.note
import de.westnordost.streetcomplete.testutils.noteEdit
import de.westnordost.streetcomplete.testutils.p
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** AUDIT regression test: a FAILING test confirms the defect in its name. */
class AuditNoteTrackUploadTest : StreetCompleteDatabaseTestCase() {
    private lateinit var dao: NoteEditsDao
    private lateinit var controller: NoteEditsController

    override fun onDatabaseInitialized(database: Database) {
        dao = NoteEditsDao(database)
        controller = NoteEditsControllerImpl(dao, SystemFileSystem)
    }

    /** F4: the GPX trace is POSTed before the durable note reservation exists. */
    @Test fun `gpx trace is uploaded once even if the trace reply is lost and upload is retried`(): Unit = runBlocking {
        var tracesOnServer = 0
        var loseReply = true
        val tracks: TracksApiClient = mock {
            everySuspend { create(any(), any(), any(), any()) } calls {
                tracesOnServer++
                if (loseReply) { loseReply = false; throw ConnectionException("reply lost after commit") }
                tracesOnServer.toLong()
            }
        }
        val notes: NotesApiClient = mock { everySuspend { create(any(), any()) } returns note(1) }
        val user: UserDataSource = mock { every { userName } returns "u" }
        val uploader = NoteEditsUploader(controller, mock<NoteController>(), user, notes, tracks, mock<PhotoServiceApiClient>())
        val edit = noteEdit(noteId = 0, action = NoteEditAction.CREATE, track = listOf(Trackpoint(p(), 1, 1f, 1f)))
        assertTrue(dao.add(edit))

        assertFailsWith<ConnectionException> { uploader.upload() }
        uploader.upload()

        assertEquals(1, tracesOnServer, "the same GPX trace exists ${tracesOnServer}x on the server")
    }
}
