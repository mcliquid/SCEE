package de.westnordost.streetcomplete.data.osmnotes

import android.content.SharedPreferences
import android.util.Log
import de.westnordost.streetcomplete.data.NotesApi

import javax.inject.Inject

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.quest.QuestStatus
import de.westnordost.streetcomplete.Prefs
import de.westnordost.osmapi.map.data.BoundingBox
import de.westnordost.osmapi.notes.Note
import de.westnordost.osmapi.notes.NoteComment
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestController
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestType
import de.westnordost.streetcomplete.data.user.UserStore
import java.util.concurrent.atomic.AtomicBoolean

/** Takes care of downloading notes, creating quests out of them and persisting them */
class OsmNotesDownloader @Inject constructor(
    private val notesApi: NotesApi,
    private val osmNoteQuestController: OsmNoteQuestController,
    private val preferences: SharedPreferences,
    private val questType: OsmNoteQuestType,
    private val avatarsDownloader: OsmAvatarsDownloader,
    private val userStore: UserStore
) : Downloader {

    override fun download(bbox: BoundingBox, cancelState: AtomicBoolean) {
        if (cancelState.get()) return

        val userId: Long = userStore.userId.takeIf { it != -1L } ?: return
        // do not download notes if not logged in because notes shall only be downloaded if logged in

        val time = System.currentTimeMillis()

        val quests = ArrayList<OsmNoteQuest>()
        val noteCommentUserIds = HashSet<Long>()

        var i = 0
        notesApi.getAll(bbox, { note ->
            ++i
            if (note.comments.isNotEmpty()) { // exclude invalid notes (#1338)
                val quest = OsmNoteQuest(note, questType)
                if (shouldMakeNoteClosed(userId, note)) {
                    quest.status = QuestStatus.CLOSED
                } else if (shouldMakeNoteInvisible(quest)) {
                    quest.status = QuestStatus.INVISIBLE
                }
                quests.add(quest)
                for (comment in note.comments) {
                    if (comment.user != null) noteCommentUserIds.add(comment.user.id)
                }
            }
        }, 10000, 0)

        val seconds = (System.currentTimeMillis() - time) / 1000
        Log.i(TAG, "Downloaded $i notes in ${seconds}s")

        osmNoteQuestController.replaceInBBox(quests, bbox)

        avatarsDownloader.download(noteCommentUserIds)
    }

    // the difference to hidden is that is that invisible quests may turn visible again, dependent
    // on the user's settings while hidden quests are "dead"
    private fun shouldMakeNoteInvisible(quest: OsmNoteQuest): Boolean {
        /* many notes are created to report problems on the map that cannot be resolved
         * through an on-site survey rather than questions from other (armchair) mappers
         * that want something cleared up on-site.
         * Likely, if something is posed as a question, the reporter expects someone to
         * answer/comment on it, so let's only show these */
        val showNonQuestionNotes = preferences.getBoolean(Prefs.SHOW_NOTES_NOT_PHRASED_AS_QUESTIONS, false)
        return !(quest.probablyContainsQuestion() || showNonQuestionNotes)
    }

    private fun shouldMakeNoteClosed(userId: Long?, note: Note): Boolean {
        if (userId == null) return false
        /* hide a note if he already contributed to it. This can also happen from outside
           this application, which is why we need to overwrite its quest status. */
        return note.containsCommentFromUser(userId) || note.probablyCreatedByUserInApp(userId)
    }

    companion object {
        private const val TAG = "QuestDownload"
    }
}

private fun Note.containsCommentFromUser(userId: Long): Boolean {
    for (comment in comments) {
        val isComment = comment.action == NoteComment.Action.COMMENTED
        if (comment.isFromUser(userId) && isComment) return true
    }
    return false
}

private fun Note.probablyCreatedByUserInApp(userId: Long): Boolean {
    val firstComment = comments.first()
    val isViaApp = firstComment.text.contains("via " + ApplicationConstants.NAME)
    return firstComment.isFromUser(userId) && isViaApp
}

private fun NoteComment.isFromUser(userId: Long): Boolean {
    return user != null && user.id == userId
}
