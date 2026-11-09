package de.westnordost.streetcomplete.ui.common.dialogs

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesSource
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.bottom_sheet.note.CreateNoteForm
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class NoteSubmissionTest {
    @get:Rule val compose = createComposeRule()
    private val prefs = inMemoryPrefs()
    private val tiles = mock<DownloadedTilesSource> { every { contains(any(), any()) } returns true }
    private val koin = koinApplication { modules(module {
        single<Preferences> { prefs }
        single<DownloadedTilesSource> { tiles }
        single<FileSystem> { SystemFileSystem }
    }) }
    @After fun close() { koin.close() }

    @Test fun `downloaded area callback runs after composition and only once across recompositions`() {
        val revision = mutableStateOf(0)
        var submissions = 0
        compose.setContent {
            MaterialTheme {
                Text("revision ${revision.value}")
                val currentRevision = revision.value
                OutsideAreaDialog(LatLon(1.0, 1.0), tiles, { check(currentRevision >= 0); submissions++ }, {})
            }
        }
        repeat(25) { compose.runOnIdle { revision.value++ }; compose.waitForIdle() }
        assertEquals(1, submissions)
    }

    @Test fun `rapid submit and retained form recomposition persist only one pending note`() {
        Prefs.preferences = prefs
        val revision = mutableStateOf(0)
        var submissions = 0
        compose.setContent {
            KoinIsolatedContext(koin) {
                MaterialTheme {
                    Text("revision ${revision.value}")
                    CreateNoteForm(
                        onLeaveNote = { _, _, _, _ -> submissions++ },
                        onDismiss = {}, trackpoints = null, position = LatLon(1.0 + revision.value * 0.000001, 1.0), lastMapClick = null,
                    )
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("One note")
        compose.onNodeWithContentDescription("OK").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("OK").performClick()
        repeat(25) { compose.runOnIdle { revision.value++ }; compose.waitForIdle() }
        assertEquals(1, submissions)
    }

    @Test fun `outside area dismissal allows a later confirmed submission`() {
        every { tiles.contains(any(), any()) } returns false
        val show = mutableStateOf(true)
        var submissions = 0
        compose.setContent {
            MaterialTheme {
                if (show.value) OutsideAreaDialog(LatLon(1.0, 1.0), tiles, { submissions++ }, { show.value = false })
            }
        }
        assertEquals(0, submissions)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, submissions)
        compose.runOnIdle { show.value = true }
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, submissions)
    }
    private fun repeatedComment(close: Boolean) {
        Prefs.preferences = prefs
        prefs.expertMode = true
        var submissions = 0
        var submittedClose = false
        compose.setContent {
            KoinIsolatedContext(koin) {
                MaterialTheme {
                    de.westnordost.streetcomplete.quests.note_comments.AddNoteCommentForm(
                        onDismiss = {}, onCommentNote = { _, _, value -> submissions++; submittedClose = value },
                        onHideQuest = {}, quest = de.westnordost.streetcomplete.testutils.osmNoteQuest(),
                        note = de.westnordost.streetcomplete.testutils.note().copy(comments = emptyList()),
                        lastMapClick = null, fileSystem = SystemFileSystem,
                        avatarsCacheDir = kotlinx.io.files.Path("unused-test-avatars"),
                        questsHiddenSource = mock { every { get(any()) } returns null },
                    )
                }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("One comment")
        if (close) compose.onNodeWithText("Close note (only do this if you actually solve the note!)").performClick()
        repeat(3) { compose.onNodeWithContentDescription("OK").performClick(); compose.waitForIdle() }
        assertEquals(1, submissions)
        assertEquals(close, submittedClose)
    }

    @Test fun `repeated COMMENT taps create one edit`() = repeatedComment(false)
    @Test fun `repeated CLOSE taps create one edit`() = repeatedComment(true)

}
