package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osmnotes.NoteController
import de.westnordost.streetcomplete.data.osmnotes.NotesApiClient
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.NoteLookup
import de.westnordost.streetcomplete.data.osmnotes.NoteUnavailableException
import de.westnordost.streetcomplete.util.ktx.format
import de.westnordost.streetcomplete.data.osmnotes.PhotoServiceApiClient
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.CLOSE
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.COMMENT
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction.CREATE
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.osmtracks.TracksApiClient
import de.westnordost.streetcomplete.data.upload.OnUploadedChangeListener
import de.westnordost.streetcomplete.data.user.UserDataSource
import de.westnordost.streetcomplete.util.logs.Log
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NoteEditsUploader(
    private val noteEditsController: NoteEditsController,
    private val noteController: NoteController,
    private val userDataSource: UserDataSource,
    private val notesApi: NotesApiClient,
    private val tracksApi: TracksApiClient,
    private val imageUploader: PhotoServiceApiClient,
) {
    var uploadedChangeListener: OnUploadedChangeListener? = null

    private val mutex = Mutex()

    /** Upload independent note edits, retaining rejected and uncertain writes for review. */
    suspend fun upload() = mutex.withLock { withContext(Dispatchers.IO) {
        // first look if any images have not been activated yet
        uploadMissedImageActivations()
        // then do the usual stuff
        uploadEdits()
    } }

    private suspend fun uploadMissedImageActivations() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val edit = noteEditsController.getOldestNeedingImagesActivation() ?: break
            // see uploadEdits
            withContext(NonCancellable) {
                imageUploader.activate(edit.noteId)
                noteEditsController.markImagesActivated(edit.id)
            }
        }
    }

    private suspend fun uploadEdits() {
        val blockedNotes = mutableSetOf<Long>()
        var uncertain: Exception? = null
        while (true) {
            currentCoroutineContext().ensureActive()
            val edit = (if (blockedNotes.isEmpty()) noteEditsController.getOldestUnsynced()
                else noteEditsController.getOldestUnsyncedExcluding(blockedNotes)) ?: break
            /* the sync of local change -> API and its response should not be cancellable because
             * otherwise an inconsistency in the data would occur. E.g. a note could be uploaded
             * twice  */
            try {
                withContext(NonCancellable) { uploadEdit(edit) }
            } catch (e: NoteCreationUncertainException) {
                blockedNotes.add(edit.noteId)
                if (uncertain == null) uncertain = e
            } catch (e: NoteWriteRejectedException) {
                blockedNotes.add(edit.noteId)
                if (uncertain == null) uncertain = e
            }
        }
        uncertain?.let { throw it }
    }

    /** Call only after explicit user confirmation; this performs GET, never CREATE. */
    suspend fun resolveCreation(editId: Long, serverNoteId: Long) = mutex.withLock {
        require(serverNoteId > 0)
        val edit = checkNotNull(noteEditsController.get(editId))
        val attempt = checkNotNull(noteEditsController.getCreationAttempt(editId))
        check(!edit.isSynced && edit.action == CREATE && attempt.noteId == null)
        val note = getAcknowledgedNote(serverNoteId)
        val opening = note.comments.firstOrNull()
        require(note.id == serverNoteId && note.position.latitude == edit.position.latitude.format(7).toDouble() &&
            note.position.longitude == edit.position.longitude.format(7).toDouble() && opening?.text == attempt.text &&
            opening.user?.id == userDataSource.userId) { "The selected note does not match this upload" }
        withContext(NonCancellable) {
            noteEditsController.acknowledgeCreation(editId, serverNoteId)
            noteEditsController.markSynced(edit, note)
            noteController.put(note)
        }
    }

    /** Explicitly confirmed discard; serialize against CREATE and reconciliation. */
    suspend fun discardUncertainCreation(editId: Long) = mutex.withLock {
        val attempt = checkNotNull(noteEditsController.getCreationAttempt(editId))
        if (attempt.noteId != null) {
            // Acknowledged writes finish through sync; only an unavailable target permits discard.
            check(notesApi.lookup(attempt.noteId) is NoteLookup.Unavailable) {
                "The acknowledged note is available; synchronize to finish this write"
            }
        }
        noteEditsController.discardUncertainCreation(editId)
    }

    private suspend fun getAcknowledgedNote(id: Long): Note = when (val result = notesApi.lookup(id)) {
        is NoteLookup.Available -> result.note
        is NoteLookup.Unavailable -> throw NoteUnavailableException(result)
    }

    private suspend fun uploadEdit(edit: NoteEdit) {
        val attempt = noteEditsController.getCreationAttempt(edit.id)
        if (attempt != null && attempt.noteId == null) throw NoteCreationUncertainException(edit.id)
        val note = if (attempt?.noteId != null) {
            try {
                getAcknowledgedNote(attempt.noteId)
            } catch (e: NoteUnavailableException) {
                throw NoteCreationUncertainException(edit.id, e)
            }
        } else {
            val imageText = uploadAndGetAttachedPhotosText(edit.imagePaths)
            val trackText = uploadAndGetAttachedTrackText(edit.track, edit.text)
            val text = edit.text.orEmpty() + imageText + trackText
            if (!noteEditsController.beginCreation(edit.id, text)) {
                // Undo can remove the edit before the atomic reservation; no POST happened.
                if (noteEditsController.get(edit.id) == null) return
                throw NoteCreationUncertainException(edit.id)
            }
            try {
                val response = when (edit.action) {
                    CREATE -> notesApi.create(edit.position, text)
                    COMMENT -> notesApi.comment(edit.noteId, text)
                    CLOSE -> notesApi.close(edit.noteId, text)
                }
                noteEditsController.acknowledgeCreation(edit.id, response.id)
                response
            } catch (e: Exception) {
                if (e.isDefinitiveNoteWriteRejection()) {
                    noteEditsController.clearRejectedCreation(edit.id)
                    if (e is de.westnordost.streetcomplete.data.AuthorizationException) throw e
                    throw NoteWriteRejectedException(edit.id, e)
                }
                throw NoteCreationUncertainException(edit.id, e)
            }
        }
        Log.d(TAG, "Uploaded a ${edit.action.name} to ${note.id}")
        noteEditsController.markSynced(edit, note)
        noteController.put(note)
        uploadedChangeListener?.onUploaded(NOTE, edit.position)
        if (edit.imagePaths.isNotEmpty()) {
            imageUploader.activate(note.id)
            noteEditsController.markImagesActivated(edit.id)
        }
    }

    private suspend fun uploadAndGetAttachedPhotosText(imagePaths: List<String>): String {
        if (imagePaths.isNotEmpty()) {
            val urls = imageUploader.upload(imagePaths)
            if (urls.isNotEmpty()) {
                return "\n\nAttached photo(s):\n" + urls.joinToString("\n")
            }
        }
        return ""
    }

    private suspend fun uploadAndGetAttachedTrackText(
        trackpoints: List<Trackpoint>?,
        noteText: String?
    ): String {
        if (trackpoints.isNullOrEmpty()) return ""
        val trackId = tracksApi.create(
            trackpoints = trackpoints,
            creator = ApplicationConstants.USER_AGENT,
            description = noteText,
            tags = listOf(ApplicationConstants.NAME)
        )
        val encodedUsername = userDataSource.userName!!.encodeURLPathPart()
        return "\n\nGPS Trace: https://www.openstreetmap.org/user/$encodedUsername/traces/$trackId\n"
    }

    companion object {
        private const val TAG = "NoteEditsUploader"
        private const val NOTE = "NOTE"
    }
}
