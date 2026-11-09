package de.westnordost.streetcomplete.ui.common.dialogs

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.westnordost.streetcomplete.data.osmnotes.edits.*
import de.westnordost.streetcomplete.screens.main.errors.UncertainNoteDialog
import dev.mokkery.mock
import dev.mokkery.every
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class UncertainNoteDialogTest {
    @get:Rule val compose = createComposeRule()
    private val controller = mock<NoteEditsController> {
        every { get(any()) } returns de.westnordost.streetcomplete.testutils.noteEdit(action = NoteEditAction.CREATE)
        every { getCreationAttempt(any()) } returns NoteCreationAttempt("Preserved note and photo URL", null)
    }
    private val api = mock<de.westnordost.streetcomplete.data.osmnotes.NotesApiClient>()
    private val uploader = NoteEditsUploader(controller, mock(), mock(), api, mock(), mock())
    private val koin = koinApplication { modules(module {
        single<NoteEditsController> { controller }
        single { uploader }
    }) }
    @After fun close() { koin.close() }
    private fun show(onDismiss: () -> Unit = {}) {
        compose.setContent { KoinIsolatedContext(koin) { MaterialTheme { UncertainNoteDialog(1, onDismiss) } } }
        compose.waitForIdle()
    }
    @Test fun `link requires positive ID and explicit confirmation`() {
        show()
        compose.onNodeWithText("Preserved note and photo URL").assertExists()
        compose.onNodeWithText("Link existing note").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("123")
        compose.onNodeWithText("Link existing note").performClick()
        compose.onNodeWithText("I checked this existing note. Link it without creating a new note: 123").assertExists()
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        verify(exactly(0)) { controller.acknowledgeCreation(any(), any()) }
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
    }
    @Test fun `discard cancellation preserves local data`() {
        show()
        compose.onNodeWithText("Discard local change").performClick()
        compose.onNodeWithText("Delete this pending local change and its local photos? The server may already have applied it. This does not change any OSM note.").assertExists()
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
        compose.onAllNodesWithText("Cancel").onLast().performClick()
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
    }
    @Test fun `discard executes exactly once only after explicit confirmation`() {
        var dismissed = false
        show { dismissed = true }
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
        compose.onNodeWithText("Discard local change").performClick()
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil { dismissed }
        verify(exactly(1)) { controller.discardUncertainCreation(1) }
    }
    @Test fun `confirmed link persists selected server ID and completes local edit`() {
        val linked = de.westnordost.streetcomplete.testutils.note(123).copy(
            position = de.westnordost.streetcomplete.testutils.noteEdit().position,
            comments = listOf(de.westnordost.streetcomplete.testutils.comment(
                text = "Preserved note and photo URL", user = de.westnordost.streetcomplete.data.user.User(0, "test"))))
        dev.mokkery.everySuspend { api.lookup(123) } returns de.westnordost.streetcomplete.data.osmnotes.NoteLookup.Available(linked)
        var dismissed = false
        show { dismissed = true }
        compose.onNode(hasSetTextAction()).performTextInput("123")
        compose.onNodeWithText("Link existing note").performClick()
        verify(exactly(0)) { controller.acknowledgeCreation(any(), any()) }
        compose.onNodeWithText("OK").performClick()
        compose.waitUntil { dismissed }
        verify(exactly(1)) { controller.acknowledgeCreation(1, 123) }
        verify(exactly(1)) { controller.markSynced(any(), linked) }
    }

    @Test fun `uncertain COMMENT offers explicit discard without unsafe automatic matching`() {
        every { controller.get(any()) } returns de.westnordost.streetcomplete.testutils.noteEdit(action = NoteEditAction.COMMENT)
        show()
        compose.onNodeWithText("Link existing note").assertDoesNotExist()
        compose.onNodeWithText("View note history").assertExists()
        compose.onNodeWithText("Discard local change").assertExists()
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
    }

    @Test fun `stale resolved edit dismisses without destructive actions`() {
        every { controller.get(any()) } returns null
        var dismissed = false
        show { dismissed = true }
        compose.waitUntil { dismissed }
        verify(exactly(0)) { controller.discardUncertainCreation(any()) }
    }

}
