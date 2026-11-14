package de.westnordost.streetcomplete.data.upload

import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesController
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditsUploader
import de.westnordost.streetcomplete.data.osmnotes.edits.*
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.UserLoginSource
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.noteEdit
import dev.mokkery.mock
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.matcher.any
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode.Companion.exactly
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlin.test.*

class UploaderContinuationTest {
    private val edits = mock<NoteEditsController> {
        every { getOldestNeedingImagesActivation() } returns null
        every { getOldestUnsynced() } returns noteEdit(action = NoteEditAction.CREATE)
        every { getCreationAttempt(any()) } returns NoteCreationAttempt("prepared", null)
        every { getOldestUnsyncedExcluding(any()) } returns null
    }
    private val external = mock<ExternalSourceQuestController>()
    private val client = HttpClient(MockEngine { respondOk("") })
    private val elementController = mock<ElementEditsController> {
        every { claimOldestUnsynced(any()) } returns null
        every { getAllUnsynced() } returns emptyList()
    }
    private val elements = ElementEditsUploader(
        elementController, edits,
        mock(), mock(), mock(), mock(), external, mock())
    private val uploader = Uploader(
        NoteEditsUploader(edits, mock(), mock(), mock(), mock(), mock()), elements,
        DownloadedTilesController(mock()), mock<UserLoginSource> { every { isLoggedIn } returns true },
        VersionIsBannedChecker(client, "https://example.invalid/ban", "test"),
        UserLoginController(inMemoryPrefs()), Mutex(), external, mock())
    private val listener = mock<UploadProgressSource.Listener>().also { uploader.addListener(it) }
    @AfterTest fun close() { client.close() }

    @Test fun `uncertain note still uploads external changes and remains reported`() = runBlocking {
        val error = assertFailsWith<NoteCreationUncertainException> { uploader.upload() }
        assertEquals(1L, error.editId)
        verify { listener.onError(error) }
        verifySuspend(exactly(1)) { external.upload() }
        assertFalse(uploader.isUploadInProgress)
    }

    @Test fun `ordinary note transport failure aborts without running external upload`() = runBlocking {
        val error = ConnectionException("note transport")
        every { edits.getOldestUnsynced() } throws error
        assertEquals(error.message, assertFailsWith<ConnectionException> { uploader.upload() }.message)
        verifySuspend(exactly(0)) { external.upload() }
    }

    @Test fun `external transport failure takes precedence and is not suppressed`() = runBlocking {
        val error = ConnectionException("external transport")
        everySuspend { external.upload() } throws error
        assertEquals(error.message, assertFailsWith<ConnectionException> { uploader.upload() }.message)
        verifySuspend(exactly(1)) { external.upload() }
    }

    @Test fun `definitively rejected note is reported after independent external upload`() = runBlocking {
        val error = NoteWriteRejectedException(1, IllegalStateException("rejected"))
        every { edits.getOldestUnsynced() } throws error
        assertFailsWith<NoteWriteRejectedException> { uploader.upload() }
        verifySuspend(exactly(1)) { external.upload() }
        verify { listener.onError(error) }
    }

    @Test fun `ordinary successful upload remains successful`() = runBlocking {
        every { edits.getOldestUnsynced() } returns null
        uploader.upload()
        verifySuspend(exactly(1)) { external.upload() }
    }
    @Test fun `cancelled overlapping request leaves progress active until the other upload finishes`() = runBlocking {
        every { edits.getOldestUnsynced() } returns null
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        everySuspend { external.upload() } calls { entered.complete(Unit); release.await() }
        val first = async(Dispatchers.Default) { uploader.upload() }
        try {
            withTimeout(10000) { entered.await() }
            val second = launch(start = CoroutineStart.UNDISPATCHED) { uploader.upload() }
            assertTrue(uploader.isUploadInProgress)
            second.cancelAndJoin()
            assertTrue(uploader.isUploadInProgress, "finishing one request must not hide the other")
        } finally { release.complete(Unit) }
        withTimeout(10000) { first.await() }
        assertFalse(uploader.isUploadInProgress)
    }

    @Test fun `rejected element still allows note and external uploads before reporting review`() = runBlocking<Unit> {
        every { edits.getOldestUnsynced() } returns null
        val rejected = de.westnordost.streetcomplete.testutils.edit().copy(review =
            de.westnordost.streetcomplete.data.osm.edits.ElementEditReview(1, 400, "invalid", 123))
        every { elementController.getAllUnsynced() } returns listOf(rejected)
        assertFailsWith<de.westnordost.streetcomplete.data.osm.edits.ElementEditsNeedReviewException> { uploader.upload() }
        verify { edits.getOldestUnsynced() }
        verifySuspend(exactly(1)) { external.upload() }
        assertFalse(uploader.isUploadInProgress)
    }

}
