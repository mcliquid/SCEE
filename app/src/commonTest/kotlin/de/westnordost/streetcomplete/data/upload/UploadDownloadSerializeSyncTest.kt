package de.westnordost.streetcomplete.data.upload

import de.westnordost.streetcomplete.data.download.DownloadProgressSource
import de.westnordost.streetcomplete.data.download.Downloader
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesController
import de.westnordost.streetcomplete.data.download.tiles.DownloadedTilesDao
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.maptiles.MapTilesDownloader
import de.westnordost.streetcomplete.data.osm.edits.ElementEdit
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditUploader
import de.westnordost.streetcomplete.data.osm.edits.upload.ElementEditsUploader
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataApiClient
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataController
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataDownloader
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osmnotes.NoteController
import de.westnordost.streetcomplete.data.osmnotes.NotesApiClient
import de.westnordost.streetcomplete.data.osmnotes.NotesDownloader
import de.westnordost.streetcomplete.data.osmnotes.PhotoServiceApiClient
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsUploader
import de.westnordost.streetcomplete.data.osmtracks.TracksApiClient
import de.westnordost.streetcomplete.data.user.UserDataSource
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.statistics.StatisticsController
import de.westnordost.streetcomplete.testutils.bbox
import de.westnordost.streetcomplete.testutils.edit
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadDownloadSerializeSyncTest {

    @Test fun `upload that owns SerializeSync finishes remaining work before waiting download`() = runBlocking {
        val env = SerializeSyncHarness()
        val edit1 = edit(id = 1L)
        val edit2 = edit(id = 2L)
        env.queueElementEdits(edit1, edit2)
        env.gateFirstElementUpload()

        val upload = async(Dispatchers.IO) { env.uploader.upload() }
        env.firstElementEntered.awaitSoon()
        assertTrue(env.serializeSync.isLocked)
        assertTrue(env.uploader.isUploadInProgress)

        val download = async(Dispatchers.IO) {
            env.downloader.download(bbox(), isUserInitiated = true, ignoreCache = true)
        }
        env.downloadStarted.awaitSoon()
        env.serializeSync.secondLockAttempted.awaitSoon()

        assertTrue(env.downloader.isDownloadInProgress)
        assertTrue(env.serializeSync.isLocked)
        assertFalse(env.downloadMutationEntered.isCompleted)
        assertFalse(env.secondElementEntered.isCompleted)

        env.releaseFirstElement.complete(Unit)
        env.secondElementEntered.awaitSoon()
        env.noteUploadEntered.awaitSoon()
        env.externalUploadEntered.awaitSoon()

        assertFalse(env.downloadMutationEntered.isCompleted)
        assertTrue(env.downloader.isDownloadInProgress)

        upload.awaitSoon()
        env.downloadMutationEntered.awaitSoon()
        env.releaseDownload.complete(Unit)
        download.awaitSoon()

        env.assertNoOverlap()
        verifySuspend(exactly(1)) { env.singleUploader.upload(edit1, any()) }
        verifySuspend(exactly(1)) { env.singleUploader.upload(edit2, any()) }
        verify { env.noteEditsController.getOldestUnsynced() }
        verifySuspend { env.externalSourceQuestController.upload() }
        verifySuspend { env.mapDataApi.getMap(any(), any()) }
    }

    @Test fun `download that owns SerializeSync blocks upload until download releases it`() = runBlocking {
        val env = SerializeSyncHarness()
        val edit1 = edit(id = 1L)
        val edit2 = edit(id = 2L)
        env.queueElementEdits(edit1, edit2)
        env.recordElementUploads()

        val download = async(Dispatchers.IO) {
            env.downloader.download(bbox(), isUserInitiated = true, ignoreCache = true)
        }
        env.downloadMutationEntered.awaitSoon()
        assertTrue(env.serializeSync.isLocked)
        assertTrue(env.downloader.isDownloadInProgress)

        val upload = async(Dispatchers.IO) { env.uploader.upload() }
        env.uploadStarted.awaitSoon()
        env.serializeSync.secondLockAttempted.awaitSoon()

        assertTrue(env.uploader.isUploadInProgress)
        assertFalse(env.firstElementEntered.isCompleted)
        assertFalse(env.noteUploadEntered.isCompleted)
        assertFalse(env.externalUploadEntered.isCompleted)

        env.releaseDownload.complete(Unit)
        download.awaitSoon()

        env.firstElementEntered.awaitSoon()
        env.secondElementEntered.awaitSoon()
        env.noteUploadEntered.awaitSoon()
        env.externalUploadEntered.awaitSoon()
        upload.awaitSoon()

        env.assertNoOverlap()
        verifySuspend(exactly(1)) { env.singleUploader.upload(edit1, any()) }
        verifySuspend(exactly(1)) { env.singleUploader.upload(edit2, any()) }
        verify { env.noteEditsController.getOldestUnsynced() }
        verifySuspend { env.externalSourceQuestController.upload() }
    }
}

private const val AWAIT_MS = 10_000L

private suspend fun <T> CompletableDeferred<T>.awaitSoon(): T =
    withTimeout(AWAIT_MS) { await() }

private suspend fun <T> kotlinx.coroutines.Deferred<T>.awaitSoon(): T =
    withTimeout(AWAIT_MS) { await() }

private class LockObservingMutex(
    private val delegate: Mutex = Mutex()
) : Mutex by delegate {
    val secondLockAttempted = CompletableDeferred<Unit>()
    private val attempts = AtomicInteger(0)

    override suspend fun lock(owner: Any?) {
        if (attempts.incrementAndGet() == 2) secondLockAttempted.complete(Unit)
        delegate.lock(owner)
    }
}

private class UploadDownloadOverlapGuard {
    private val lock = Any()
    private var uploadActive = 0
    private var downloadActive = 0
    val events = CopyOnWriteArrayList<String>()

    fun enterUpload(label: String) = synchronized(lock) {
        events += "enter-upload:$label"
        check(downloadActive == 0) { "upload '$label' overlapped download; events=$events" }
        uploadActive++
    }

    fun exitUpload(label: String) = synchronized(lock) {
        events += "exit-upload:$label"
        uploadActive--
    }

    fun enterDownload(label: String) = synchronized(lock) {
        events += "enter-download:$label"
        check(uploadActive == 0) { "download '$label' overlapped upload; events=$events" }
        downloadActive++
    }

    fun exitDownload(label: String) = synchronized(lock) {
        events += "exit-download:$label"
        downloadActive--
    }
}

private class SerializeSyncHarness {
    val serializeSync = LockObservingMutex()
    val overlap = UploadDownloadOverlapGuard()

    val downloadStarted = CompletableDeferred<Unit>()
    val uploadStarted = CompletableDeferred<Unit>()
    val downloadMutationEntered = CompletableDeferred<Unit>()
    val releaseDownload = CompletableDeferred<Unit>()
    val firstElementEntered = CompletableDeferred<Unit>()
    val releaseFirstElement = CompletableDeferred<Unit>()
    val secondElementEntered = CompletableDeferred<Unit>()
    val noteUploadEntered = CompletableDeferred<Unit>()
    val externalUploadEntered = CompletableDeferred<Unit>()

    val elementEditsController: ElementEditsController = mock()
    val mapDataController: MapDataController = mock()
    val noteEditsController: NoteEditsController = mock()
    val noteController: NoteController = mock()
    val singleUploader: ElementEditUploader = mock()
    val mapDataApi: MapDataApiClient = mock()
    val notesApi: NotesApiClient = mock()
    val statisticsController: StatisticsController = mock()
    val externalSourceQuestController: ExternalSourceQuestController = mock()
    val mapTilesDownloader: MapTilesDownloader = mock()
    val downloadedTilesDao: DownloadedTilesDao = mock()
    val userDataSource: UserDataSource = mock()
    val tracksApi: TracksApiClient = mock()
    val imageUploader: PhotoServiceApiClient = mock()

    private val prefs = inMemoryPrefs()
    private val userLoginController = UserLoginController(prefs).also { it.logIn("test-token") }

    val elementEditsUploader = ElementEditsUploader(
        elementEditsController,
        noteEditsController,
        mapDataController,
        singleUploader,
        mapDataApi,
        statisticsController,
        externalSourceQuestController,
        prefs.prefs,
    )

    val noteEditsUploader = NoteEditsUploader(
        noteEditsController,
        noteController,
        userDataSource,
        notesApi,
        tracksApi,
        imageUploader,
    )

    val uploader = Uploader(
        noteEditsUploader,
        elementEditsUploader,
        DownloadedTilesController(downloadedTilesDao),
        userLoginController,
        VersionIsBannedChecker(HttpClient(MockEngine { respondOk("") }), "http://banned.test", "test-agent"),
        userLoginController,
        serializeSync,
        externalSourceQuestController,
        prefs.prefs,
    )

    val downloader = Downloader(
        NotesDownloader(notesApi, noteController),
        MapDataDownloader(mapDataApi, mapDataController),
        mapTilesDownloader,
        DownloadedTilesController(downloadedTilesDao),
        userLoginController,
        serializeSync,
        externalSourceQuestController,
    )

    init {
        every { noteEditsController.getOldestUnsynced() } returns null
        every { noteEditsController.getOldestNeedingImagesActivation() } calls {
            overlap.enterUpload("notes")
            try {
                noteUploadEntered.complete(Unit)
                null
            } finally {
                overlap.exitUpload("notes")
            }
        }
        everySuspend { externalSourceQuestController.upload() } calls {
            overlap.enterUpload("external")
            try {
                externalUploadEntered.complete(Unit)
            } finally {
                overlap.exitUpload("external")
            }
        }
        everySuspend { notesApi.getAllOpen(any(), any()) } calls {
            overlap.enterDownload("notes")
            try {
                emptyList()
            } finally {
                overlap.exitDownload("notes")
            }
        }
        everySuspend { mapTilesDownloader.download(any()) } calls {
            overlap.enterDownload("mapTiles")
            overlap.exitDownload("mapTiles")
        }
        everySuspend { mapDataApi.getMap(any(), any()) } calls {
            overlap.enterDownload("mapData")
            try {
                downloadMutationEntered.complete(Unit)
                runBlocking { releaseDownload.await() }
                MutableMapData()
            } finally {
                overlap.exitDownload("mapData")
            }
        }
        everySuspend { externalSourceQuestController.download(any()) } calls {
            overlap.enterDownload("external")
            overlap.exitDownload("external")
        }

        uploader.addListener(object : UploadProgressSource.Listener {
            override fun onStarted() {
                uploadStarted.complete(Unit)
            }
        })
        downloader.addListener(object : DownloadProgressSource.Listener {
            override fun onStarted() {
                downloadStarted.complete(Unit)
            }
        })
    }

    fun queueElementEdits(vararg edits: ElementEdit) {
        val remaining = edits.toMutableList()
        every { elementEditsController.getOldestUnsynced() } calls {
            remaining.removeFirstOrNull()
        }
    }

    fun gateFirstElementUpload() {
        everySuspend { singleUploader.upload(any(), any()) } calls { (edit: ElementEdit) ->
            overlap.enterUpload("element-${edit.id}")
            try {
                if (edit.id == 1L) {
                    firstElementEntered.complete(Unit)
                    runBlocking { releaseFirstElement.await() }
                } else {
                    secondElementEntered.complete(Unit)
                }
                MapDataUpdates()
            } finally {
                overlap.exitUpload("element-${edit.id}")
            }
        }
    }

    fun recordElementUploads() {
        everySuspend { singleUploader.upload(any(), any()) } calls { (edit: ElementEdit) ->
            overlap.enterUpload("element-${edit.id}")
            try {
                if (edit.id == 1L) firstElementEntered.complete(Unit)
                else secondElementEntered.complete(Unit)
                MapDataUpdates()
            } finally {
                overlap.exitUpload("element-${edit.id}")
            }
        }
    }

    fun assertNoOverlap() {
        val uploadEvents = overlap.events.filter { it.contains("-upload:") }
        val downloadEvents = overlap.events.filter { it.contains("-download:") }
        assertTrue(uploadEvents.isNotEmpty(), "expected upload mutation events, got ${overlap.events}")
        assertTrue(downloadEvents.isNotEmpty(), "expected download mutation events, got ${overlap.events}")
    }
}
