package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.download.tiles.TilePos
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

    @Test fun `large download requires confirmation and stale approval cannot start another route`() = runBlocking {
        val source = FakeRouteQuests().apply {
            covered = false
            tiles = (0..40).mapTo(linkedSetOf()) { TilePos(it, 20000) }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoute(route)
            val approval = state.confirmation.value!!
            assertEquals(0, source.downloads)
            assertEquals(RouteQuestStatus.AwaitingConfirmation, state.state.value.status)
            state.cancelDownload()
            assertEquals(RouteQuestStatus.Incomplete, state.state.value.status)
            assertEquals(route, state.state.value.points)
            state.retry()
            state.confirmDownload(state.confirmation.value!!)
            assertEquals(1, source.downloads)
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
            source.covered = false
            state.setRoute(other)
            state.confirmDownload(approval)
            assertEquals(1, source.downloads)
            assertEquals(other, state.state.value.points)
        } finally { scope.cancel() }
    }

    @Test fun `selection preserves pending shared download and does not request another confirmation`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            state.select(1)
            assertEquals(1, source.downloads.size)
            assertEquals(other, state.state.value.points)
            assertEquals(RouteQuestStatus.Loading, state.state.value.status)
            assertEquals(null, state.confirmation.value)
            source.pending!!.complete(Unit)
            assertEquals(1, source.downloads.size)
            assertTrue(source.coverage.containsAll(source.firstTiles + source.secondTiles))
            assertTrue(state.alternatives.value.all { it.status == RouteQuestStatus.Complete })
            assertEquals(2, state.state.value.keys.size)
        } finally { scope.cancel() }
    }

    @Test fun `selection outside pending plan cancels and loads the newly selected corridor`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = false).apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            state.select(1)
            assertEquals(2, source.downloads.size)
            source.pending!!.complete(Unit)
            assertEquals(source.secondTiles, source.coverage)
            assertEquals(RouteQuestStatus.Incomplete, state.alternatives.value[0].status)
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
        } finally { scope.cancel() }
    }

    @Test fun `batch events and answered quests produce one final count after shared download`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            val counts = source.counts
            val checks = source.coverageChecks
            source.firstKeys = emptySet() // Answered during download
            repeat(20) {
                source.coverage.add(source.firstTiles.first())
                source.changes.emit(Unit)
            }
            delay(200)
            assertEquals(counts, source.counts)
            assertEquals(checks, source.coverageChecks)
            state.select(1)
            source.pending!!.complete(Unit)
            withTimeout(2000) { while (state.state.value.status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(counts + 1, source.counts)
            assertEquals(listOf(0, 2), state.alternatives.value.map { it.keys.size })
            assertEquals(1, source.downloads.size)
            source.secondKeys = emptySet()
            source.changes.emit(Unit)
            delay(200)
            assertEquals(0, state.state.value.keys.size)
            assertEquals(counts + 2, source.counts)
        } finally { scope.cancel() }
    }

    @Test fun `partial download failure recounts covered alternatives once and keeps updates enabled`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply {
            pending = CompletableDeferred()
            failAfterPending = true
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            val counts = source.counts
            source.coverage.addAll(source.firstTiles)
            source.firstKeys = emptySet()
            repeat(10) { source.changes.emit(Unit) }
            assertEquals(counts, source.counts)
            source.pending!!.complete(Unit)
            withTimeout(2000) { while (state.alternatives.value[0].status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(counts + 1, source.counts)
            assertEquals(RouteQuestStatus.Incomplete, state.alternatives.value[1].status)
            assertEquals(0, state.alternatives.value[0].keys.size)
            assertEquals(source.firstTiles, source.coverage)
            source.coverage.addAll(source.secondTiles)
            source.changes.emit(Unit)
            withTimeout(2000) { while (state.alternatives.value[1].status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(counts + 2, source.counts)
            assertEquals(1, source.downloads.size)
        } finally { scope.cancel() }
    }

    @Test fun `cancelling after batch events recounts partial coverage once and updates resume`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            val counts = source.counts
            source.coverage.add(source.firstTiles.first())
            repeat(10) { source.changes.emit(Unit) }
            state.cancelDownload()
            source.pending!!.complete(Unit)
            delay(200)
            assertEquals(counts + 1, source.counts)
            assertTrue(state.alternatives.value.all { it.status == RouteQuestStatus.Incomplete })
            assertEquals(1, source.coverage.size)
            source.coverage.addAll(source.firstTiles + source.secondTiles)
            source.changes.emit(Unit)
            withTimeout(2000) { while (state.state.value.status != RouteQuestStatus.Complete) delay(10) }
            assertEquals(counts + 2, source.counts)
            assertEquals(1, source.downloads.size)
        } finally { scope.cancel() }
    }

    @Test fun `overlapping alternatives share one corridor download and independent counts`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            assertEquals(1, source.downloads.size)
            assertEquals(6, source.downloads.single().tiles.size)
            assertEquals(listOf(1, 2), state.alternatives.value.map { it.keys.size })
            assertTrue(state.alternatives.value.all { it.status == RouteQuestStatus.Complete })
            state.select(1)
            assertEquals(other, state.state.value.points)
            assertEquals(2, state.state.value.keys.size)
            assertEquals(1, source.downloads.size)
            state.setRoutes(listOf(route, other), 1)
            assertEquals(1, source.downloads.size)
            assertEquals(2, source.preparations)
        } finally { scope.cancel() }
    }

    @Test fun `large union loads selected only and another selection reuses shared tiles`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            assertEquals(source.firstTiles, source.downloads.single().tiles)
            assertEquals(RouteQuestStatus.Complete, state.alternatives.value[0].status)
            assertEquals(RouteQuestStatus.Incomplete, state.alternatives.value[1].status)
            assertTrue(state.alternatives.value[1].keys.isEmpty())
            state.select(1)
            assertEquals(2, source.downloads.size)
            assertTrue(source.missingDownloads[0].intersect(source.missingDownloads[1]).isEmpty())
            assertEquals(listOf(1, 2), state.alternatives.value.map { it.keys.size })
        } finally { scope.cancel() }
    }

    @Test fun `profile and answers update all alternatives without downloading or changing selection`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply { coverage.addAll(firstTiles + secondTiles) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other), 1)
            assertEquals(listOf(1, 2), state.alternatives.value.map { it.keys.size })
            source.secondKeys = setOf(OsmNoteQuestKey(3))
            source.changes.emit(Unit)
            withTimeout(2000) { while (state.alternatives.value.any { it.status != RouteQuestStatus.Complete }) delay(10) }
            assertEquals(listOf(1, 1), state.alternatives.value.map { it.keys.size })
            source.firstKeys = emptySet()
            source.changes.emit(Unit)
            withTimeout(2000) { while (state.alternatives.value.any { it.status != RouteQuestStatus.Complete }) delay(10) }
            assertEquals(listOf(0, 1), state.alternatives.value.map { it.keys.size })
            assertEquals(other, state.state.value.points)
            assertTrue(source.downloads.isEmpty())
            assertEquals(2, source.preparations)
        } finally { scope.cancel() }
    }

    @Test fun `replacing and removing alternatives cancels pending shared data work`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = true).apply { pending = CompletableDeferred() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            val previous = source.pending!!
            source.pending = null
            state.setRoutes(listOf(other))
            previous.complete(Unit)
            assertEquals(listOf(other), state.alternatives.value.map { it.points })
            assertEquals(2, state.state.value.keys.size)
            source.coverage.clear()
            source.pending = CompletableDeferred()
            state.retry()
            state.setRoutes(emptyList())
            source.pending!!.complete(Unit)
            assertTrue(state.alternatives.value.isEmpty())
            assertTrue(state.state.value.points.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `download failure keeps independent alternatives incomplete and selectable`() = runBlocking {
        val source = AlternativeQuestSource(route, other, smallUnion = false).apply { fail = true }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val state = RouteQuestState(scope, source)
            state.setRoutes(listOf(route, other))
            assertTrue(state.alternatives.value.all { it.status == RouteQuestStatus.Incomplete })
            state.select(1)
            assertEquals(other, state.state.value.points)
            assertTrue(state.state.value.keys.isEmpty())
            source.fail = false
            state.retry()
            assertEquals(RouteQuestStatus.Complete, state.state.value.status)
            assertEquals(2, state.state.value.keys.size)
        } finally { scope.cancel() }
    }

    @Test fun `standard route stays visible during corridor download and removal prevents late work`() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        val source = FakeRouteQuests().apply { covered = false; pendingDownload = pending }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val standard = PedestrianRoute(route, 800.0, 600.0)
        var requests = 0
        val routing = PedestrianRouteState(scope) { requests++; listOf(standard) }
        val quests = RouteQuestState(scope, source)
        scope.launch {
            routing.state.collect { state ->
                quests.setRoutes(state.routes.map { it.points }, state.selectedIndex)
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
            val equalPoints = route.map { it.copy() }
            state.setRoute(equalPoints)
            // Equal new geometry retains the existing count and its original list reference.
            // The map must therefore match points by value, not by reference.
            assertTrue(state.state.value.points !== equalPoints)
            assertEquals(equalPoints, state.state.value.points)
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
    var tiles = emptySet<TilePos>()
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
        return RouteQuestCorridor(points, tiles)
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

private class AlternativeQuestSource(
    private val first: List<LatLon>, private val second: List<LatLon>, smallUnion: Boolean,
) : RouteQuestDataSource {
    override val changes = MutableSharedFlow<Unit>()
    val firstTiles = (10..14).mapTo(hashSetOf()) { TilePos(it, 100) }
    val secondTiles = (if (smallUnion) 11..15 else 14..18).mapTo(hashSetOf()) { TilePos(it, 100) }
    val coverage = hashSetOf<TilePos>()
    val downloads = mutableListOf<RouteQuestCorridor>()
    val missingDownloads = mutableListOf<Set<TilePos>>()
    var firstKeys: Set<QuestKey> = setOf(OsmNoteQuestKey(1))
    var secondKeys: Set<QuestKey> = setOf(OsmNoteQuestKey(2), OsmNoteQuestKey(3))
    var pending: CompletableDeferred<Unit>? = null
    var fail = false
    var failAfterPending = false
    var preparations = 0
    var counts = 0
    var coverageChecks = 0
    override suspend fun questsFor(corridors: List<RouteQuestCorridor>): List<Set<QuestKey>> {
        counts++
        return corridors.map { quests(it) }
    }
    override suspend fun prepare(points: List<LatLon>): RouteQuestCorridor {
        preparations++
        return RouteQuestCorridor(points, if (points == first) firstTiles else secondTiles)
    }
    override suspend fun isCovered(corridor: RouteQuestCorridor): Boolean {
        coverageChecks++
        return coverage.containsAll(corridor.tiles)
    }
    override suspend fun load(corridor: RouteQuestCorridor) {
        downloads.add(corridor)
        missingDownloads.add(corridor.tiles - coverage)
        if (fail) error("Offline")
        pending?.await()
        if (failAfterPending) error("Offline after partial download")
        coverage.addAll(corridor.tiles)
    }
    override suspend fun quests(corridor: RouteQuestCorridor) = if (corridor.points == first) firstKeys else secondKeys
}
