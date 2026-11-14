package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.ElementIdUpdate
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.screens.settings.gpxNotesDir
import de.westnordost.streetcomplete.util.Listeners
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import io.github.vinceglb.filekit.path
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

class NoteEditsControllerImpl(
    private val editsDB: NoteEditsDao,
    private val fileSystem: FileSystem,
    gpxNotesDirectory: Path? = null,
    gpxBeforeCommit: () -> Unit = {},
) : NoteEditsController {

    private val listeners = Listeners<NoteEditsSource.Listener>()

    private val lock = ReentrantLock()
    private val gpxScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gpxStore by lazy {
        GpxNotesStore(
            fileSystem,
            gpxNotesDirectory ?: Path(gpxNotesDir.path),
            gpxBeforeCommit,
        )
    }

    override fun add(
        noteId: Long,
        action: NoteEditAction,
        position: LatLon,
        text: String?,
        imagePaths: List<String>,
        track: List<Trackpoint>?,
        isGpxNote: Boolean,
    ): Boolean {
        val edit = NoteEdit(
            0,
            noteId,
            position,
            action,
            text,
            imagePaths,
            nowAsEpochMilliseconds(),
            false,
            imagePaths.isNotEmpty(),
            track,
        )
        if (isGpxNote) {
            val saved = runBlocking { saveGpxNote(position, text, imagePaths, track) }
            if (!saved) Log.w(TAG, "GPX note was not saved")
            return saved
        }
        lock.withLock { editsDB.add(edit) }
        onAddedEdit(edit)
        return true
    }

    override suspend fun saveGpxNote(
        position: LatLon,
        text: String?,
        imagePaths: List<String>,
        track: List<Trackpoint>?,
    ): Boolean {
        val write = gpxScope.async {
            try {
                gpxStore.append(text ?: "", imagePaths, position, track)
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "GPX note was not saved", e)
                false
            }
        }
        return write.await()
    }

    override fun get(id: Long): NoteEdit? =
        editsDB.get(id)

    override fun getAllUnsynced(): List<NoteEdit> =
        editsDB.getAllUnsynced()

    override fun getAll(): List<NoteEdit> =
        editsDB.getAll()

    override fun getOldestUnsynced(): NoteEdit? =
        editsDB.getOldestUnsynced()

    override fun getUnsyncedCount(): Int =
        editsDB.getUnsyncedCount()

    override fun getAllUnsyncedForNote(noteId: Long): List<NoteEdit> =
        editsDB.getAllUnsyncedForNote(noteId)

    override fun getAllUnsyncedForNotes(noteIds: Collection<Long>): List<NoteEdit> =
        editsDB.getAllUnsyncedForNotes(noteIds)

    override fun getAllUnsynced(bbox: BoundingBox): List<NoteEdit> =
        editsDB.getAllUnsynced(bbox)

    override fun getAllUnsyncedPositions(bbox: BoundingBox): List<LatLon> =
        editsDB.getAllUnsyncedPositions(bbox)

    override fun getOldestNeedingImagesActivation(): NoteEdit? =
        editsDB.getOldestNeedingImagesActivation()

    override fun markImagesActivated(id: Long): Boolean =
        lock.withLock { editsDB.markImagesActivated(id) }

    override fun getOldestUnsyncedExcluding(noteIds: Set<Long>): NoteEdit? = editsDB.getOldestUnsyncedExcluding(noteIds)

    override fun getCreationAttempt(id: Long): NoteCreationAttempt? = editsDB.getCreationAttempt(id)

    override fun beginCreation(id: Long, text: String): Boolean = editsDB.beginCreation(id, text)

    override fun clearRejectedCreation(id: Long) = editsDB.clearRejectedCreation(id)

    override fun discardUncertainCreation(id: Long) {
        val edit = lock.withLock { editsDB.discardUncertainCreation(id) }
        onDeletedEdits(listOf(edit))
        for (imagePath in edit.imagePaths) fileSystem.delete(Path(imagePath), mustExist = false)
    }

    override fun acknowledgeCreation(id: Long, noteId: Long) = editsDB.acknowledgeCreation(id, noteId)

    override fun markSynced(edit: NoteEdit, note: Note) {
        val markSyncedSuccess = lock.withLock { editsDB.markSynced(edit, note.id) }
        // Persist success before deleting attachments or notifying fallible listeners.
        for (imagePath in edit.imagePaths) {
            fileSystem.delete(Path(imagePath), mustExist = false)
        }
        if (markSyncedSuccess) {
            onSyncedEdit(edit.copy(noteId = note.id, isSynced = true))
        }
    }

    override fun markSyncFailed(edit: NoteEdit): Boolean =
        delete(edit)

    override fun undo(edit: NoteEdit): Boolean {
        // Every attempted note write requires the explicit resolution/discard confirmation.
        val deleted = lock.withLock { editsDB.deleteUnattemptedCreation(edit.id) }
        if (deleted) {
            onDeletedEdits(listOf(edit))
            for (imagePath in edit.imagePaths) fileSystem.delete(Path(imagePath), mustExist = false)
        }
        return deleted
    }

    override fun deleteSyncedOlderThan(timestamp: Long): Int {
        var deletedCount = 0
        var deleteEdits = listOf<NoteEdit>()
        lock.withLock {
            deleteEdits = editsDB.getSyncedOlderThan(timestamp)
            if (deleteEdits.isEmpty()) return 0
            deletedCount = editsDB.deleteAll(deleteEdits.map { it.id })
        }
        onDeletedEdits(deleteEdits)
        return deletedCount
    }

    private fun delete(edit: NoteEdit): Boolean {
        for (imagePath in edit.imagePaths) {
            fileSystem.delete(Path(imagePath), mustExist = false)
        }
        val deleteSuccess = lock.withLock { editsDB.delete(edit.id) }
        if (deleteSuccess) {
            onDeletedEdits(listOf(edit))
            return true
        }
        return false
    }

    override fun updateElementIds(idUpdates: Collection<ElementIdUpdate>) {
        for (idUpdate in idUpdates) {
            val elementType = idUpdate.elementType.name.lowercase()
            editsDB.replaceTextInUnsynced(
                "osm.org/$elementType/${idUpdate.oldElementId} ",
                "osm.org/$elementType/${idUpdate.newElementId} ",
            )
        }
    }

    /* ------------------------------------ Listeners ------------------------------------------- */

    override fun addListener(listener: NoteEditsSource.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: NoteEditsSource.Listener) {
        listeners.remove(listener)
    }

    private fun onAddedEdit(edit: NoteEdit) {
        listeners.forEach { it.onAddedEdit(edit) }
    }

    private fun onSyncedEdit(edit: NoteEdit) {
        listeners.forEach { it.onSyncedEdit(edit) }
    }

    private fun onDeletedEdits(edits: List<NoteEdit>) {
        listeners.forEach { it.onDeletedEdits(edits) }
    }

    private companion object {
        const val TAG = "NoteEditsController"
    }
}
