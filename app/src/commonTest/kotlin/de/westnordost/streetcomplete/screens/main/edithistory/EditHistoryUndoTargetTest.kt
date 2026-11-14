package de.westnordost.streetcomplete.screens.main.edithistory

import de.westnordost.streetcomplete.screens.main.retainedEditHistoryKey
import de.westnordost.streetcomplete.testutils.edit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EditHistoryUndoTargetTest {

    @Test fun `undo confirms the edit the dialog was opened for`() {
        val original = edit(id = 1, isSynced = false)
        val other = edit(id = 2, isSynced = false)
        val target = undoDialogTargetId(original)
        assertEquals(original, editToUndo(listOf(other, original), target, isUploading = false))
    }

    @Test fun `a missing edit is not replaced by whatever is selected now`() {
        val original = edit(id = 1, isSynced = false)
        val synced = edit(id = 2, isSynced = true)
        val target = undoDialogTargetId(original)
        assertNull(resolveUndoDialogEdit(listOf(synced), target))
        assertNull(editToUndo(listOf(synced), target, isUploading = false))
        assertNull(retainedEditHistoryKey(listOf(synced), original.key))
    }

    @Test fun `changing the surrounding selection does not retarget the dialog`() {
        val original = edit(id = 1, isSynced = false)
        val other = edit(id = 2, isSynced = true)
        val target = undoDialogTargetId(original)
        assertEquals(original, resolveUndoDialogEdit(listOf(other, original), target))
        assertEquals(other.key, retainedEditHistoryKey(listOf(other, original), other.key))
        assertEquals(original.key, retainedEditHistoryKey(listOf(other, original), original.key))
    }

    @Test fun `an in-flight unsynced edit is not undone and a different synced edit is not substituted`() {
        val original = edit(id = 1, isSynced = false)
        val synced = edit(id = 2, isSynced = true)
        assertNull(editToUndo(listOf(original, synced), undoDialogTargetId(original), isUploading = true))
        assertEquals(synced, editToUndo(listOf(original, synced), undoDialogTargetId(synced), isUploading = true))
    }
}
