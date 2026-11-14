package de.westnordost.streetcomplete.ui.common.dialogs

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.upload.UploadController
import de.westnordost.streetcomplete.screens.main.edithistory.RejectedElementEditDialog
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.mock
import dev.mokkery.every
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.matcher.any
import dev.mokkery.verify
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.koin.compose.KoinIsolatedContext
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class RejectedElementEditDialogTest {
    @get:Rule val compose = createComposeRule()
    private val controller = mock<ElementEditsController>()
    private var scheduled = 0
    private val uploads = mock<UploadController> { every { upload(any()) } calls { scheduled++ } }
    private val koin = koinApplication { modules(module {
        single<ElementEditsController> { controller }
        single<UploadController> { uploads }
    }) }
    @After fun close() { koin.close() }

    private fun show(root: Long = 1, undo: () -> Unit = {}) {
        every { controller.get(any()) } returns edit(id = 1).copy(review = ElementEditReview(root, 400, "Invalid tag", 123))
        compose.setContent { KoinIsolatedContext(koin) { MaterialTheme {
            RejectedElementEditDialog(1, onDismiss = {}, onUndo = undo)
        } } }
        compose.waitUntil { compose.onAllNodesWithText("Invalid tag").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `review displays persisted diagnosis and retry schedules upload`() {
        every { controller.retryRejectedEdit(1) } returns true
        show()
        compose.onNodeWithText("Local edit #1").assertExists()
        compose.onNodeWithText("The server rejected this edit (HTTP 400). Automatic retries are blocked.").assertExists()
        compose.onNodeWithText("Retry upload").performClick()
        compose.waitUntil { scheduled == 1 }
        verify { controller.retryRejectedEdit(1) }
        verify { uploads.upload(true) }
    }

    @Test fun `dependent review identifies and retries original rejected edit`() {
        every { controller.retryRejectedEdit(9) } returns true
        show(root = 9)
        compose.onNodeWithText("Blocked by rejected local edit #9. Retry retries that original edit.").assertExists()
        compose.onNodeWithText("Retry upload").performClick()
        compose.waitUntil { scheduled == 1 }
        verify { controller.retryRejectedEdit(9) }
    }

    @Test fun `undo routes to existing lifecycle without retrying`() {
        var undos = 0
        show(undo = { undos++ })
        compose.onNodeWithText("Undo this edit").performClick()
        assertEquals(1, undos)
    }

    @Test fun `claim refusal remains visible and does not claim retry success`() {
        every { controller.retryRejectedEdit(1) } throws ElementEditUploadInProgressException()
        show()
        compose.onNodeWithText("Retry upload").performClick()
        compose.waitUntil { compose.onAllNodesWithText("Action unavailable. Wait for the upload or try again.").fetchSemanticsNodes().isNotEmpty() }
    }
}
