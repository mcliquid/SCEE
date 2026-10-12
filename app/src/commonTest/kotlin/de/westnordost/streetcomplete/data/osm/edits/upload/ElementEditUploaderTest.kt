package de.westnordost.streetcomplete.data.osm.edits.upload

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.CreateWaterwayFordAction
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.member
import de.westnordost.streetcomplete.testutils.p
import dev.mokkery.answering.calls
import dev.mokkery.verify
import dev.mokkery.verifyNoMoreCalls
import de.westnordost.streetcomplete.data.osm.edits.upload.changesets.OpenChangesetsManager
import de.westnordost.streetcomplete.data.osm.mapdata.ChangesetTooLargeException
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataApiClient
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataChanges
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataSource
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.testutils.edit
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentially
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ElementEditUploaderTest {

    private lateinit var changesetManager: OpenChangesetsManager
    private lateinit var mapDataApi: MapDataApiClient
    private lateinit var mapDataSource: MapDataSource
    private lateinit var uploader: ElementEditUploader

    @BeforeTest fun setUp() {
        changesetManager = mock()
        mapDataApi = mock()
        mapDataSource = mock()

        uploader = ElementEditUploader(changesetManager, mapDataApi, mapDataSource)
    }

    @Test fun `create new changeset when changeset is too large`(): Unit = runBlocking {
        val action = mock<ElementEditAction>()
        val edit = edit(action = action)
        every { action.createUpdates(any(), any()) } returns MapDataChanges()

        // current changeset is 1
        everySuspend { changesetManager.getOrCreateChangeset(any(), any(), any(), any()) } returns 1L
        // but when uploading using this changeset, exception is thrown
        everySuspend { mapDataApi.uploadChanges(1L, any(), any()) } throws ChangesetTooLargeException()

        // creating a changeset yields id 2
        everySuspend { changesetManager.createChangeset(any(), any(), any()) } returns 2
        // and uploading changes to this changeset yields some result
        val mapDataUpdates = MapDataUpdates()
        everySuspend { mapDataApi.uploadChanges(2L, any(), any()) } returns mapDataUpdates

        assertEquals(
            mapDataUpdates,
            uploader.upload(edit, { ElementIdProvider(emptyList()) })
        )
    }

    @Test fun `passes on conflict exception`(): Unit = runBlocking {
        val action = mock<ElementEditAction>()
        val edit = edit(action = action)
        every { action.createUpdates(any(), any()) } returns MapDataChanges()

        everySuspend { changesetManager.getOrCreateChangeset(any(), any(), any(), any()) } returns 1
        everySuspend { changesetManager.createChangeset(any(), any(), any()) } returns 1
        everySuspend { mapDataApi.uploadChanges(any(), any(), any()) } throws ConflictException()

        assertFailsWith<ConflictException> {
            uploader.upload(edit, { ElementIdProvider(emptyList()) })
        }
    }

    @Test fun `handles changeset conflict exception`(): Unit = runBlocking {
        val action = mock<ElementEditAction>()
        val edit = edit(action = action)
        every { action.createUpdates(any(), any()) } returns MapDataChanges()

        everySuspend { changesetManager.getOrCreateChangeset(any(), any(), any(), any()) } returns 1
        everySuspend { changesetManager.createChangeset(any(), any(), any()) } returns 1
        everySuspend { mapDataApi.uploadChanges(any(), any(), any()) } sequentially {
            throws(ConflictException())
            returns(MapDataUpdates())
        }
        every { mapDataSource.getNodes(emptySet()) } returns emptyList()
        uploader.upload(edit, { ElementIdProvider(emptyList()) })
    }

    @Test fun `crossing edits always use remote data and upload all changes together`(): Unit = runBlocking {
        val f = WaterwayCrossingTestData()
        // This relation is absent from the local source. Splitting must fetch it from the API.
        val route = rel(30, listOf(member(ElementType.WAY, 10)), mapOf("type" to "route", "route" to "bus"))
        everySuspend { mapDataApi.getWayComplete(any()) } calls { (id: Long) -> f.repo.getWayComplete(id) }
        everySuspend { mapDataApi.getNode(any()) } calls { (id: Long) -> f.repo.getNode(id) }
        everySuspend { mapDataApi.getRelationsForWay(any()) } calls { (id: Long) -> if (id == 10L) listOf(route) else emptyList() }
        everySuspend { changesetManager.getOrCreateChangeset(any(), any(), any(), any()) } returns 1L
        val uploaded = mutableListOf<MapDataChanges>()
        everySuspend { mapDataApi.uploadChanges(any(), any(), any()) } calls {
            (_: Long, changes: MapDataChanges, _: (Map<String, String>, Int) -> Boolean) ->
            uploaded.add(changes)
            MapDataUpdates()
        }
        every { mapDataSource.getNodes(emptySet()) } returns emptyList()
        for (action in listOf(f.bridge, f.culvert, CreateWaterwayFordAction(f.crossing))) {
            uploader.upload(edit(action = action), f::idProvider)
        }
        assertEquals(3, uploaded.size)
        val bridge = uploaded[0]
        assertEquals(2, bridge.creations.filterIsInstance<Node>().size)
        assertEquals(2, bridge.creations.filterIsInstance<Way>().size)
        assertTrue(bridge.modifications.any { it is Relation && it.id == 30L && it.members.size == 3 })
        assertTrue((bridge.creations + bridge.modifications).any { it is Way && it.tags["bridge"] == "yes" })
        verify { mapDataSource.getNodes(emptySet()) }
        verifyNoMoreCalls(mapDataSource)
    }

    @Test fun `crossing moved remotely conflicts before any upload`(): Unit = runBlocking {
        val f = WaterwayCrossingTestData()
        f.data.putElement(f.data.getNode(2)!!.copy(position = p(0.0001, -0.0002)))
        everySuspend { mapDataApi.getWayComplete(any()) } calls { (id: Long) -> f.repo.getWayComplete(id) }
        everySuspend { mapDataApi.uploadChanges(any(), any(), any()) } throws AssertionError("Must not upload a moved crossing")
        assertFailsWith<ConflictException> { uploader.upload(edit(action = f.bridge), f::idProvider) }
    }
}
