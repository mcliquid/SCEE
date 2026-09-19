package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.edithistory.Edit

/** Whether the main-screen undo control may open the history / start undo.
 *  Download does not block this; an in-progress upload does. */
fun isMainUndoEnabled(isUploading: Boolean): Boolean = !isUploading

/** Whether the user may request a manual upload.
 *  Download does not block this: the upload queues behind the shared sync mutex.
 *  A second upload while one is already in progress stays disabled. */
fun isManualUploadEnabled(isUploading: Boolean): Boolean = !isUploading

/** Combined sync indicator used by progress UI (stars counter, etc.). */
fun isSyncProgressVisible(isUploading: Boolean, isDownloading: Boolean): Boolean =
    isUploading || isDownloading

/** True when this edit must not be deleted because it may currently be uploaded. */
fun isUnsyncedEditBlockedByUpload(edit: Edit, isUploading: Boolean): Boolean =
    isUploading && edit.isSynced == false

/**
 * Whether this history edit may be undone now.
 *
 * Unsynced element/note edits may already be in-flight during upload, so deleting them is unsafe.
 * Synced-edit revert (`isSynced == true`) and local unhide (`isSynced == null`) remain allowed.
 * Download does not affect this.
 */
fun isEditUndoEnabled(edit: Edit, isUploading: Boolean): Boolean {
    if (!edit.isUndoable) return false
    if (isUnsyncedEditBlockedByUpload(edit, isUploading)) return false
    return true
}
