package de.westnordost.streetcomplete.data.osm.edits.upload

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataApiClient
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataController
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.upload.OnUploadedChangeListener
import de.westnordost.streetcomplete.data.user.statistics.StatisticsController
import de.westnordost.streetcomplete.testutils.edit
import de.westnordost.streetcomplete.testutils.mockPrefs2
import de.westnordost.streetcomplete.testutils.node
import dev.mokkery.answering.calls
import dev.mokkery.answering.repeat
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifyNoMoreCalls
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test

class ElementEditsUploaderTest {

    private lateinit var elementEditsController: ElementEditsController
    private lateinit var mapDataController: MapDataController
    private lateinit var noteEditsController: NoteEditsController
    private lateinit var singleUploader: ElementEditUploader
    private lateinit var mapDataApi: MapDataApiClient
    private lateinit var statisticsController: StatisticsController
    private lateinit var externalSourceQuestController: ExternalSourceQuestController

    private lateinit var uploader: ElementEditsUploader
    private lateinit var listener: OnUploadedChangeListener

    @BeforeTest fun setUp() {
        elementEditsController = mock()
        mapDataController = mock()
        noteEditsController = mock()

        singleUploader = mock()
        mapDataApi = mock()
        statisticsController = mock()
        externalSourceQuestController = mock()

        listener = mock()

        uploader = ElementEditsUploader(elementEditsController, noteEditsController, mapDataController, singleUploader, mapDataApi, statisticsController, externalSourceQuestController, mockPrefs2())
        uploader.uploadedChangeListener = listener
    }

    @Test fun `crossing conflict refresh still fetches nodes absent from complete responses`() = runBlocking {
        val f = WaterwayCrossingTestData()
        val edit = edit(action = f.bridge)
        every { elementEditsController.getOldestUnsynced() } sequentially { returns(edit); repeat { returns(null) } }
        everySuspend { singleUploader.upload(any(), any()) } throws ConflictException()
        everySuspend { mapDataApi.getWayComplete(f.road.id) } returns null
        everySuspend { mapDataApi.getWayComplete(f.water.id) } returns f.repo.getWayComplete(f.water.id)
        for (id in f.road.nodeIds) everySuspend { mapDataApi.getNode(id) } returns f.data.getNode(id)
        uploader.upload()
        verifySuspend(exactly(4)) { mapDataApi.getNode(any()) }
        verifySuspend(exactly(0)) { mapDataApi.getNode(5) }
        verifySuspend(exactly(0)) { mapDataApi.getNode(6) }
    }

    @Test fun `crossing conflict refresh reuses complete way nodes and still fetches missing ones`() = runBlocking {
        val f = WaterwayCrossingTestData()
        val edit = edit(action = f.bridge)
        every { elementEditsController.getOldestUnsynced() } sequentially { returns(edit); repeat { returns(null) } }
        everySuspend { singleUploader.upload(any(), any()) } throws ConflictException()
        everySuspend { mapDataApi.getWayComplete(f.road.id) } returns f.repo.getWayComplete(f.road.id)
        everySuspend { mapDataApi.getWayComplete(f.water.id) } returns f.repo.getWayComplete(f.water.id)
        uploader.upload()
        verifySuspend(exactly(0)) { mapDataApi.getNode(any()) }
        verifySuspend { mapDataApi.getWayComplete(f.road.id) }
        verifySuspend { mapDataApi.getWayComplete(f.water.id) }
    }

    @Test fun `cancel upload finishes current edit`() = runBlocking {
        val edit = edit()
        val updates = MapDataUpdates()
        val job = launch(start = CoroutineStart.LAZY) { uploader.upload() }
        every { elementEditsController.getOldestUnsynced() } sequentially {
            returns(edit)
            repeat { returns(null) }
        }
        everySuspend { singleUploader.upload(any(), any()) } calls {
            job.cancel()
            updates
        }

        job.start()
        job.join()

        verify(exactly(1)) { elementEditsController.getOldestUnsynced() }
        verify { elementEditsController.markSynced(edit, updates) }
        verify { noteEditsController.updateElementIds(any()) }
        verify { mapDataController.updateAll(updates) }
        verify { statisticsController.addOne(any(), any()) }
    }

    @Test fun `upload works`() = runBlocking {
        val edit = edit()
        val updates = MapDataUpdates()

        every { elementEditsController.getOldestUnsynced() } sequentially {
            returns(edit)
            repeat { returns(null) }
        }
        everySuspend { singleUploader.upload(any(), any()) } returns updates

        uploader.upload()

        verifySuspend { singleUploader.upload(edit, any()) }
        verify { listener.onUploaded(any(), any()) }
        verify { elementEditsController.markSynced(edit, updates) }
        verify { noteEditsController.updateElementIds(any()) }
        verify { mapDataController.updateAll(updates) }

        verify { statisticsController.addOne(any(), any()) }
    }

    @Test fun `upload catches conflict exception`() = runBlocking {
        // edit modifies node 1 and way 1
        val node1 = node()
        val action: ElementEditAction = mock()
        every { action.elementKeys } returns listOf(
            ElementKey(ElementType.NODE, 1),
            ElementKey(ElementType.WAY, 1),
        )
        val edit = edit(action = action)

        // ...but way 1 is gone
        everySuspend { mapDataApi.getNode(1) } returns node1
        everySuspend { mapDataApi.getWayComplete(1) } returns null

        // the edit is the first in the upload queue and the uploader throws a conflict exception
        every { elementEditsController.getOldestUnsynced() } sequentially {
            returns(edit)
            repeat { returns(null) }
        }
        everySuspend { singleUploader.upload(any(), any()) } throws ConflictException()

        uploader.upload()

        verifySuspend { singleUploader.upload(edit, any()) }
        verify { listener.onDiscarded(any(), any()) }

        verify { externalSourceQuestController.onSyncEditFailed(edit) }
        verify { elementEditsController.markSyncFailed(edit) }
        verifyNoMoreCalls(statisticsController)

        verify {
            mapDataController.updateAll(MapDataUpdates(
                updated = listOf(node1),
                deleted = listOf(ElementKey(ElementType.WAY, 1))
            ))
        }
    }
}
