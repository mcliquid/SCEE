package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.testutils.edit
import de.westnordost.streetcomplete.testutils.noteEdit
import de.westnordost.streetcomplete.testutils.noteQuestHidden
import de.westnordost.streetcomplete.testutils.questHidden
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncActionStateTest {

    @Test fun `download only keeps main undo enabled`() {
        assertTrue(isMainUndoEnabled(isUploading = false))
    }

    @Test fun `download only keeps manual upload available`() {
        assertTrue(isManualUploadEnabled(isUploading = false))
    }

    @Test fun `upload disables main undo`() {
        assertFalse(isMainUndoEnabled(isUploading = true))
    }

    @Test fun `upload disables duplicate manual upload`() {
        assertFalse(isManualUploadEnabled(isUploading = true))
    }

    @Test fun `sync progress represents upload and download`() {
        assertFalse(isSyncProgressVisible(isUploading = false, isDownloading = false))
        assertTrue(isSyncProgressVisible(isUploading = true, isDownloading = false))
        assertTrue(isSyncProgressVisible(isUploading = false, isDownloading = true))
        assertTrue(isSyncProgressVisible(isUploading = true, isDownloading = true))
    }

    @Test fun `sidebar cannot undo unsynced element edit while uploading`() {
        val unsynced = edit(isSynced = false)
        assertFalse(isEditUndoEnabled(unsynced, isUploading = true))
        assertTrue(isEditUndoEnabled(unsynced, isUploading = false))
    }

    @Test fun `sidebar cannot undo unsynced note edit while uploading`() {
        val unsynced = noteEdit(isSynced = false)
        assertFalse(isEditUndoEnabled(unsynced, isUploading = true))
        assertTrue(isEditUndoEnabled(unsynced, isUploading = false))
    }

    @Test fun `sidebar may revert synced edit while uploading`() {
        val synced = edit(isSynced = true)
        assertTrue(synced.isUndoable)
        assertTrue(isEditUndoEnabled(synced, isUploading = true))
    }

    @Test fun `sidebar may unhide while uploading`() {
        assertTrue(isEditUndoEnabled(questHidden(), isUploading = true))
        assertTrue(isEditUndoEnabled(noteQuestHidden(), isUploading = true))
    }

    @Test fun `sidebar still rejects edits that are not undoable`() {
        val syncedNote = noteEdit(isSynced = true)
        assertFalse(syncedNote.isUndoable)
        assertFalse(isEditUndoEnabled(syncedNote, isUploading = false))
        assertFalse(isEditUndoEnabled(syncedNote, isUploading = true))
    }
}
