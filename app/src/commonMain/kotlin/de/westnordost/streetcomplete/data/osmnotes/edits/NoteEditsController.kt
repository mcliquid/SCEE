package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.data.osm.mapdata.ElementIdUpdate
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint

interface NoteEditsController : NoteEditsSource {
    fun add(
        noteId: Long,
        action: NoteEditAction,
        position: LatLon,
        text: String? = null,
        imagePaths: List<String> = emptyList(),
        track: List<Trackpoint>? = null,
        isGpxNote: Boolean = false
    )

    fun getOldestNeedingImagesActivation(): NoteEdit?

    fun markImagesActivated(id: Long): Boolean

    fun getOldestUnsyncedExcluding(noteIds: Set<Long>): NoteEdit?

    fun getCreationAttempt(id: Long): NoteCreationAttempt?

    fun beginCreation(id: Long, text: String): Boolean

    fun clearRejectedCreation(id: Long)

    fun discardUncertainCreation(id: Long)

    fun acknowledgeCreation(id: Long, noteId: Long)

    fun markSynced(edit: NoteEdit, note: Note)

    fun markSyncFailed(edit: NoteEdit): Boolean

    fun undo(edit: NoteEdit): Boolean

    fun deleteSyncedOlderThan(timestamp: Long): Int

    fun updateElementIds(idUpdates: Collection<ElementIdUpdate>)
}
