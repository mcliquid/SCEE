package de.westnordost.streetcomplete.ui.common.dialogs

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.osmnotes.edits.*
import de.westnordost.streetcomplete.screens.main.edithistory.EditHistorySidebar
import de.westnordost.streetcomplete.screens.main.edithistory.EditItem
import de.westnordost.streetcomplete.testutils.noteEdit
import dev.mokkery.mock
import dev.mokkery.every
import dev.mokkery.answering.returns
import dev.mokkery.matcher.any
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.core.qualifier.named
import kotlinx.io.files.FileSystem
import kotlinx.io.files.SystemFileSystem
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class EditHistoryProtectedUndoTest {
    @get:Rule val compose = createComposeRule()
    private val edit = noteEdit(action = NoteEditAction.CREATE, text = "preserved").copy(track = null)
    private val controller = mock<NoteEditsController> {
        every { get(any()) } returns edit
        every { getCreationAttempt(any()) } returns NoteCreationAttempt("preserved", null)
    }
    private val koin = koinApplication { modules(module {
        single<NoteEditsController> { controller }
        single { NoteEditsUploader(controller, mock(), mock(), mock(), mock(), mock()) }
        single<Lazy<FeatureDictionary>>(named("FeatureDictionaryLazy")) { lazy { error("unused") } }
        single<de.westnordost.streetcomplete.util.platform.PlatformToaster> { mock() }
        single<FileSystem> { SystemFileSystem }
    }) }
    @After fun close() { koin.close() }
    private fun show(attempt: suspend () -> NoteCreationAttempt?, undo: suspend () -> Boolean,
        shownEdit: de.westnordost.streetcomplete.data.edithistory.Edit = edit) {
        compose.setContent { KoinIsolatedContext(koin) { MaterialTheme {
            EditHistorySidebar(listOf(EditItem(shownEdit, false, false)), shownEdit, false, {},
                onUndoEdit = { undo() }, onDismissRequest = {}, getEditElement = { null },
                getNoteCreationAttempt = { attempt() })
        } } }
    }
    @Test fun `journal protected history action opens resolution instead of undo confirmation`() {
        var undos = 0
        show({ NoteCreationAttempt("preserved", null) }, { undos++; true })
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("Existing OSM note ID").assertExists()
        assertEquals(0, undos)
    }
    @Test fun `reservation created after opening confirmation redirects without undo`() {
        var reserved = false
        var undos = 0
        show({ if (reserved) NoteCreationAttempt("preserved", null) else null }, { undos++; true })
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.runOnIdle { reserved = true }
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Existing OSM note ID").assertExists()
        assertEquals(0, undos)
    }
    @Test fun `backend refusal during undo redirects to resolution instead of silent failure`() {
        var checks = 0
        var undos = 0
        show({ checks++; if (checks >= 3) NoteCreationAttempt("preserved", null) else null },
            { undos++; false })
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("Existing OSM note ID").assertExists()
        assertEquals(1, undos)
    }
    @Test fun `acknowledged CREATE explains synchronization without offering local undo`() {
        var undos = 0
        every { controller.getCreationAttempt(any()) } returns NoteCreationAttempt("preserved", 123)
        show({ NoteCreationAttempt("preserved", 123) }, { undos++; true })
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("The server acknowledged this change. Synchronize to finish local processing. If the note is missing or hidden, you can explicitly discard this local edit after checking its history; no new request will be sent.").assertExists()
        assertEquals(0, undos)
    }

    @Test fun `normal confirmed undo runs once`() {
        var undos = 0
        show({ null }, { undos++; true })
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil { undos == 1 }
        assertEquals(1, undos)
    }
    @Test fun `controller claim refusal shows upload retry message`() {
        show({ null }, { throw de.westnordost.streetcomplete.data.osm.edits.ElementEditUploadInProgressException() },
            shownEdit = de.westnordost.streetcomplete.testutils.edit())
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithText("This edit is being uploaded. Try undo again after the upload finishes.").assertExists()
    }

}
