package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.routing.PedestrianRoute
import de.westnordost.streetcomplete.data.routing.RouteQuestCorridor
import de.westnordost.streetcomplete.data.routing.RouteQuestDataSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteQuestStateTest {
    private val route = listOf(LatLon(52.5, 13.3), LatLon(52.6, 13.4))
    private val other = listOf(route.first(), LatLon(52.7, 13.5))

    @Test fun `standard route stays visible during corridor download and removal prevents late work`() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        val source = FakeRouteQuests().apply { covered = false; pendingDownload = pending }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val standard = PedestrianRoute(route, 800.0, 600.0)
        var requests = 0
        val routing = PedestrianRouteState(scope) { requests++; standard }
        val quests = RouteQuestState(scope, source)
        scope.launch {
            routing.state.collect { state ->
                quests.setRoute(if (state.loading) emptyList() else state.route?.points.orEmpty())
            }
        }
        try {
            routing.start(route.first(), route.last())
            assertEquals(standard, routing.state.value.route)
            assertTrue(!routing.state.value.loading)
            assertEquals(RouteQuestStatus.Loading, quests.state.value.status)
            assertEquals(1, source.downloads)
            source.changes.emit(Unit)
            assertEquals(1, source.downloads)
            assertEquals(1, requests)
            routing.clear()
            pending.complete(Unit)
            assertEquals(PedestrianRouteUiState(), routing.state.value)
            assertTrue(quests.state.value.points.isEmpty())
            assertEquals(0, source.counts)
        } finally { scope.cancel() }
    }

    @Test fun `fresh coverage counts without downloading and repeated route does no work`() = runBlocking {
        val source = FakeRouteQuests()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
            assertEquals(source.keys, state.state.value.keys)
            state.setRoute(route)
            assertEquals(1, source.counts)
            assertEquals(0, source.downloads)
        } finally { scope.cancel() }
    }

    @Test fun `missing or failed coverage never counts as zero and retry completes`() = runBlocking {
        val source = FakeRouteQuests().apply { covered = false; failDownload = true }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            assertEquals(RouteQuestStatus.Incomplete, state.state.value.status)
            assertEquals(0, source.counts)
            assertTrue(state.state.value.keys.isEmpty())
            source.failDownload = false
            state.retry()
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
            assertEquals(2, source.downloads)
        } finally { scope.cancel() }
    }

    @Test fun `download returning without coverage remains incomplete`() = runBlocking {
        val source = FakeRouteQuests().apply { covered = false; coverAfterDownload = false }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            assertEquals(RouteQuestStatus.Incomplete, state.state.value.status)
            assertEquals(0, source.counts)
        } finally { scope.cancel() }
    }

    @Test fun `new route and removal discard pending evaluations`() = runBlocking {
        val source = FakeRouteQuests()
        val pending = CompletableDeferred<Set<QuestKey>>()
        source.pending = pending
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            source.pending = null
            state.setRoute(other)
            pending.complete(emptySet())
            assertEquals(other, state.state.value.points)
            assertEquals(source.keys, state.state.value.keys)
            val next = CompletableDeferred<Set<QuestKey>>()
            source.pending = next
            state.setRoute(route)
            state.setRoute(emptyList())
            next.complete(source.keys)
            assertTrue(state.state.value.points.isEmpty())
            assertTrue(state.state.value.keys.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `new route and removal cancel pending downloads`() = runBlocking {
        val source = FakeRouteQuests().apply { covered = false }
        val pending = CompletableDeferred<Unit>()
        source.pendingDownload = pending
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            state.setRoute(emptyList())
            pending.complete(Unit)
            assertTrue(state.state.value.points.isEmpty())
            assertEquals(0, source.counts)
            source.pendingDownload = null
            state.setRoute(other)
            assertEquals(other, state.state.value.points)
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
        } finally { scope.cancel() }
    }

    @Test fun `preset change and answered quest events recount without redownload`() = runBlocking {
        val source = FakeRouteQuests()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            source.keys = setOf(OsmNoteQuestKey(2), OsmNoteQuestKey(3))
            source.changes.emit(Unit) // preset switched
            withTimeout(2000) { while (state.state.value.status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(2, state.state.value.keys.size)
            source.keys = setOf(OsmNoteQuestKey(3))
            source.changes.emit(Unit) // quest answered
            withTimeout(2000) { while (state.state.value.status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(1, state.state.value.keys.size)
            assertEquals(0, source.downloads)
            assertEquals(1, source.preparations)
        } finally { scope.cancel() }
    }

    @Test fun `screen disposal prevents late result`() = runBlocking {
        val source = FakeRouteQuests().apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val state = RouteQuestState(scope, source)
        state.setRoute(route)
        scope.cancel()
        source.pending!!.complete(source.keys)
        assertEquals(RouteQuestStatus.Counting, state.state.value.status)
    }
}

private class FakeRouteQuests : RouteQuestDataSource {
    override val changes = MutableSharedFlow<Unit>()
    var covered = true
    var failDownload = false
    var coverAfterDownload = true
    var downloads = 0
    var counts = 0
    var preparations = 0
    var keys: Set<QuestKey> = setOf(OsmNoteQuestKey(1))
    var pending: CompletableDeferred<Set<QuestKey>>? = null
    var pendingDownload: CompletableDeferred<Unit>? = null
    override suspend fun prepare(points: List<LatLon>): RouteQuestCorridor {
        preparations++
        return RouteQuestCorridor(points, emptySet())
    }
    override suspend fun load(corridor: RouteQuestCorridor) {
        downloads++
        if (failDownload) error("Offline / download failed")
        pendingDownload?.await()
        covered = coverAfterDownload
    }
    override suspend fun isCovered(corridor: RouteQuestCorridor) = covered
    override suspend fun quests(corridor: RouteQuestCorridor): Set<QuestKey> {
        counts++
        return pending?.await() ?: keys
    }
}
