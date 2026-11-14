package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import dev.mokkery.mock
import de.westnordost.streetcomplete.testutils.p
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GpxNotesStoreTest {
    private val directory = Path("gpx-notes-test-${Uuid.random()}")
    private val notesFile = Path(directory, "notex.gpx")

    @AfterTest fun tearDown() {
        val files = runCatching { SystemFileSystem.list(directory) }.getOrNull().orEmpty()
        files.forEach { SystemFileSystem.delete(it, mustExist = false) }
        SystemFileSystem.delete(directory, mustExist = false)
    }

    @Test fun `two notes in a row are both stored once`() = runBlocking {
        val store = store()
        store.append("first", emptyList(), p(1.0, 2.0), null)
        store.append("second", emptyList(), p(3.0, 4.0), null)
        val text = readNotes()
        assertEquals(1, text.occurrences("first"))
        assertEquals(1, text.occurrences("second"))
        assertFalse(SystemFileSystem.exists(Path(directory, "notex.gpx.tmp")))
    }

    @Test fun `parallel writes do not drop or duplicate a note`() = runBlocking {
        val store = store()
        coroutineScope {
            listOf("alpha", "beta").map { name ->
                async { store.append(name, emptyList(), p(), null) }
            }.awaitAll()
        }
        val text = readNotes()
        assertEquals(1, text.occurrences("alpha"))
        assertEquals(1, text.occurrences("beta"))
    }

    @Test fun `an existing gpx file is extended`() = runBlocking {
        SystemFileSystem.createDirectories(directory, mustCreate = false)
        val serializer = GpxNoteSerializer("test")
        SystemFileSystem.sink(notesFile).buffered().use {
            it.writeString(serializer.appendWaypoint(null, p(9.0, 8.0), "kept"))
        }
        store().append("added", emptyList(), p(1.0, 1.0), null)
        val text = readNotes()
        assertEquals(1, text.occurrences("kept"))
        assertEquals(1, text.occurrences("added"))
    }

    @Test fun `a failed write does not block the next note and keeps the old file`() = runBlocking {
        store().append("kept", emptyList(), p(), null)
        val before = readNotes()
        var fail = true
        val flaky = GpxNotesStore(SystemFileSystem, directory) { if (fail) throw IOException("disk") }
        var threw = false
        try {
            flaky.append("lost", emptyList(), p(), null)
        } catch (_: IOException) {
            threw = true
        }
        assertTrue(threw)
        assertEquals(before, readNotes())
        fail = false
        flaky.append("after", emptyList(), p(), null)
        val text = readNotes()
        assertEquals(1, text.occurrences("kept"))
        assertEquals(0, text.occurrences("lost"))
        assertEquals(1, text.occurrences("after"))
    }

    @Test fun `parallel tracks use distinct track files`() = runBlocking {
        val point = listOf(Trackpoint(p(), 1, 1f, 1f))
        val store = store()
        coroutineScope {
            listOf("one", "two").map { name ->
                async { store.append(name, emptyList(), p(), point) }
            }.awaitAll()
        }
        val text = readNotes()
        assertEquals(1, text.occurrences("track_1.gpx"))
        assertEquals(1, text.occurrences("track_2.gpx"))
        assertTrue(SystemFileSystem.exists(Path(directory, "track_1.gpx")))
        assertTrue(SystemFileSystem.exists(Path(directory, "track_2.gpx")))
    }

    @Test fun `the controller reports a failed gpx save and accepts the next one`() = runBlocking {
        var fail = true
        val controller = NoteEditsControllerImpl(
            mock<NoteEditsDao>(),
            SystemFileSystem,
            directory,
        ) { if (fail) throw IOException("disk") }
        assertFalse(controller.saveGpxNote(p(), "nope", emptyList(), null))
        fail = false
        assertTrue(controller.saveGpxNote(p(), "yes", emptyList(), null))
        assertEquals(1, readNotes().occurrences("yes"))
        assertEquals(0, readNotes().occurrences("nope"))
    }

    private fun store() = GpxNotesStore(SystemFileSystem, directory)

    private fun readNotes() = SystemFileSystem.source(notesFile).buffered().use { it.readString() }

    private fun String.occurrences(part: String) = split(part).size - 1
}
