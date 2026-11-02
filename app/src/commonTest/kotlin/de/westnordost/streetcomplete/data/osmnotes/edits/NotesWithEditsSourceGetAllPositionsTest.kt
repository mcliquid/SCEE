package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.NoteComment
import de.westnordost.streetcomplete.data.osmnotes.NoteControllerImpl
import de.westnordost.streetcomplete.data.osmnotes.NoteDao
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.CLOSE
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.COMMENT
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.CREATE
import de.westnordost.streetcomplete.data.user.User
import de.westnordost.streetcomplete.data.user.UserDataSource
import de.westnordost.streetcomplete.testutils.bbox
import de.westnordost.streetcomplete.testutils.noteEdit
import de.westnordost.streetcomplete.testutils.p
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.not
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Regression tests for CRASH-641: [NotesWithEditsSourceImpl.getAllPositions] must stay lightweight. */
class NotesWithEditsSourceGetAllPositionsTest {

    private lateinit var src: NotesWithEditsSource
    private lateinit var noteController: de.westnordost.streetcomplete.data.osmnotes.NoteController
    private lateinit var noteEditsSource: NoteEditsSource

    private val queryBbox = bbox(0.0, 0.0, 10.0, 10.0)

    @BeforeTest fun setUp() {
        noteController = mock()
        noteEditsSource = mock()
        val userDataSource = mock<UserDataSource> {
            every { userId } returns 1
            every { userName } returns "user"
        }
        src = NotesWithEditsSourceImpl(noteController, noteEditsSource, userDataSource)
    }

    private fun stub(
        openPositions: List<LatLon> = emptyList(),
        unsyncedEdits: List<NoteEdit> = emptyList(),
    ) {
        every { noteController.getAllOpenPositions(queryBbox) } returns openPositions
        every { noteEditsSource.getAllUnsynced(queryBbox) } returns unsyncedEdits
    }

    @Test
    fun `open database note without local edits is included`() {
        val pos = p(1.0, 2.0)
        stub(openPositions = listOf(pos))

        assertEquals(listOf(pos), src.getAllPositions(queryBbox))
    }

    @Test
    fun `closed database note without local edits is excluded`() {
        stub(openPositions = emptyList())

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    @Test
    fun `unsynced CREATE is included`() {
        val pos = p(3.0, 4.0)
        stub(
            openPositions = emptyList(),
            unsyncedEdits = listOf(noteEdit(noteId = -1, action = CREATE, pos = pos))
        )

        assertEquals(listOf(pos), src.getAllPositions(queryBbox))
    }

    @Test
    fun `unsynced COMMENT on open note keeps position without duplicate`() {
        val pos = p(1.0, 2.0)
        stub(
            openPositions = listOf(pos),
            unsyncedEdits = listOf(noteEdit(noteId = 1, action = COMMENT, pos = pos))
        )

        assertEquals(listOf(pos), src.getAllPositions(queryBbox))
    }

    @Test
    fun `unsynced COMMENT on closed note does not reintroduce position`() {
        val pos = p(1.0, 2.0)
        stub(
            openPositions = emptyList(),
            unsyncedEdits = listOf(noteEdit(noteId = 1, action = COMMENT, pos = pos))
        )

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    @Test
    fun `unsynced CLOSE on open note excludes position`() {
        val pos = p(1.0, 2.0)
        stub(
            openPositions = listOf(pos),
            unsyncedEdits = listOf(noteEdit(noteId = 1, action = CLOSE, pos = pos))
        )

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    @Test
    fun `successive unsynced edits apply in order`() {
        val pos = p(5.0, 5.0)
        stub(
            openPositions = listOf(pos),
            unsyncedEdits = listOf(
                noteEdit(noteId = 1, action = COMMENT, pos = pos, timestamp = 100),
                noteEdit(noteId = 1, action = CLOSE, pos = pos, timestamp = 200),
            )
        )

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    @Test
    fun `CREATE then CLOSE for same note yields no position`() {
        val pos = p(2.0, 3.0)
        stub(
            unsyncedEdits = listOf(
                noteEdit(noteId = -1, action = CREATE, pos = pos, timestamp = 100),
                noteEdit(noteId = -1, action = CLOSE, pos = pos, timestamp = 200),
            )
        )

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    @Test
    fun `edits outside bbox are not returned by source`() {
        val inPos = p(1.0, 1.0)
        stub(openPositions = listOf(inPos), unsyncedEdits = emptyList())

        assertEquals(listOf(inPos), src.getAllPositions(queryBbox))
        verify { noteEditsSource.getAllUnsynced(queryBbox) }
    }

    @Test
    fun `duplicate positions from database and CREATE are preserved in list`() {
        val pos = p(1.0, 1.0)
        stub(
            openPositions = listOf(pos),
            unsyncedEdits = listOf(noteEdit(noteId = -2, action = CREATE, pos = pos))
        )

        val positions = src.getAllPositions(queryBbox)
        assertEquals(listOf(pos, pos), positions)
    }

    @Test
    fun `does not load full notes through note source or cache paths`() {
        every { noteController.getAllOpenPositions(any()) } returns emptyList()
        every { noteEditsSource.getAllUnsynced(any()) } returns emptyList()

        src.getAllPositions(queryBbox)

        verify(not) { noteController.getAll(any<BoundingBox>()) }
        verify(not) { noteController.get(any<Long>()) }
    }
}

/** End-to-end DB open/closed filtering via [NoteControllerImpl] and real [NoteDao]. */
class NotesWithEditsSourceGetAllPositionsDatabaseTest : StreetCompleteDatabaseTestCase() {

    private lateinit var dao: NoteDao
    private lateinit var src: NotesWithEditsSource
    private lateinit var noteEditsSource: NoteEditsSource

    private val queryBbox = BoundingBox(0.0, 0.0, 1.0, 1.0)

    override fun onDatabaseInitialized(database: Database) {
        dao = NoteDao(database)
        val noteSource = NoteControllerImpl(dao)
        noteEditsSource = mock {
            every { addListener(any()) } calls { }
        }
        val userDataSource = mock<UserDataSource> {
            every { userId } returns 1
            every { userName } returns "user"
        }
        src = NotesWithEditsSourceImpl(noteSource, noteEditsSource, userDataSource)
        every { noteEditsSource.getAllUnsynced(queryBbox) } returns emptyList()
    }

    @Test
    fun `open note in database contributes position`() {
        dao.put(openNote(1, LatLon(0.5, 0.5)))

        assertEquals(listOf(LatLon(0.5, 0.5)), src.getAllPositions(queryBbox))
    }

    @Test
    fun `closed note in database does not contribute position`() {
        dao.put(
            openNote(1, LatLon(0.5, 0.5)).copy(
                status = Note.Status.CLOSED,
                timestampClosed = 6000
            )
        )

        assertTrue(src.getAllPositions(queryBbox).isEmpty())
    }

    private fun openNote(id: Long, position: LatLon): Note {
        val user = User(1, "u")
        return Note(
            position,
            id,
            5000,
            null,
            Note.Status.OPEN,
            listOf(NoteComment(5000, NoteComment.Action.OPENED, "hi", user))
        )
    }
}
