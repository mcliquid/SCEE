package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.routing.RouteCorridorTooLargeException
import de.westnordost.streetcomplete.data.routing.RouteQuestCorridor
import de.westnordost.streetcomplete.data.routing.RouteQuestDataSource
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.math.area
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

enum class RouteQuestStatus { Loading, Counting, Complete, Incomplete, Error, TooLarge }
data class RouteQuestUiState(
    val points: List<LatLon> = emptyList(),
    val status: RouteQuestStatus = RouteQuestStatus.Loading,
    val keys: Set<QuestKey> = emptySet(),
)

/** One owner for shared downloads and per-route counts. Never requests or selects a route. */
class RouteQuestState(private val scope: CoroutineScope, private val source: RouteQuestDataSource) {
    private val mutableState = MutableStateFlow(RouteQuestUiState())
    val state = mutableState.asStateFlow()
    private val mutableAlternatives = MutableStateFlow<List<RouteQuestUiState>>(emptyList())
    val alternatives = mutableAlternatives.asStateFlow()
    private var points: List<List<LatLon>> = emptyList()
    private var plans: MutableList<RouteQuestCorridor?> = mutableListOf()
    private var selected = 0
    private var preparationJob: Job? = null
    private var downloadJob: Job? = null
    private var countJob: Job? = null
    private var generation = 0L
    private var countGeneration = 0L
    private var downloadGeneration = 0L
    private var downloading = emptySet<Int>()

    init {
        scope.launch {
            source.changes.conflate().collect {
                if (plans.any { it != null }) refresh(debounce = true)
            }
        }
    }

    fun setRoute(newPoints: List<LatLon>) = setRoutes(if (newPoints.isEmpty()) emptyList() else listOf(newPoints))

    fun setRoutes(newPoints: List<List<LatLon>>, selectedIndex: Int = 0) {
        if (points == newPoints) { select(selectedIndex); return }
        ++generation
        ++countGeneration
        ++downloadGeneration
        preparationJob?.cancel()
        countJob?.cancel()
        downloadJob?.cancel()
        downloading = emptySet()
        points = newPoints
        selected = selectedIndex.coerceIn(0, (newPoints.size - 1).coerceAtLeast(0))
        plans = MutableList(newPoints.size) { null }
        publish(newPoints.map { RouteQuestUiState(it) })
        if (newPoints.isEmpty()) return
        val current = generation
        preparationJob = scope.launch {
            for ((index, route) in newPoints.withIndex()) {
                try {
                    val plan = source.prepare(route)
                    ensureActive()
                    if (current != generation) return@launch
                    plans[index] = plan
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    if (current != generation) return@launch
                    update(index, if (e is RouteCorridorTooLargeException) RouteQuestStatus.TooLarge else RouteQuestStatus.Error)
                }
            }
            refresh()
            loadSelected()
        }
    }

    fun select(index: Int) {
        if (index !in points.indices || index == selected) return
        selected = index
        publish(mutableAlternatives.value)
        if (preparationJob?.isActive != true) loadSelected()
    }

    fun retry() { if (points.isNotEmpty()) loadSelected(force = true) }

    private fun loadSelected(force: Boolean = false) {
        val chosen = plans.getOrNull(selected) ?: return
        if (!force && downloadJob?.isActive == true && selected in downloading) return
        val wasDownloading = downloading.isNotEmpty()
        val download = ++downloadGeneration
        downloadJob?.cancel()
        downloading = emptySet()
        val current = generation
        val selectedAtStart = selected
        downloadJob = scope.launch {
            var attempted = false
            try {
                val target = sharedRouteDownloadPlan(chosen, plans.filterNotNull())
                if (source.isCovered(target)) return@launch
                ensureActive()
                if (current != generation || download != downloadGeneration) return@launch
                attempted = true
                downloading = plans.indices.filterTo(hashSetOf()) { plans[it]?.tiles?.all { tile -> tile in target.tiles } == true }
                for (index in downloading) update(index, RouteQuestStatus.Loading)
                source.load(target)
                ensureActive()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RouteQuests", "Corridor download incomplete", e)
                attempted = true
            } finally {
                if (current == generation && download == downloadGeneration) {
                    downloading = emptySet()
                    if (attempted || wasDownloading || force) refresh()
                }
            }
            if (current == generation && selected != selectedAtStart) loadSelected()
        }
    }

    private fun refresh(debounce: Boolean = false) {
        countJob?.cancel()
        val count = ++countGeneration
        val current = generation
        val currentPlans = plans.toList()
        val currentPoints = points
        publish(mutableAlternatives.value.mapIndexed { index, entry ->
            if (currentPlans.getOrNull(index) != null && entry.status != RouteQuestStatus.Loading)
                entry.copy(status = RouteQuestStatus.Counting, keys = emptySet()) else entry
        })
        countJob = scope.launch {
            try {
                if (debounce) delay(150)
                val covered = currentPlans.indices.filter { index -> currentPlans[index]?.let { source.isCovered(it) } == true }
                ensureActive()
                if (current != generation || count != countGeneration) return@launch
                publish(mutableAlternatives.value.mapIndexed { index, entry ->
                    if (index in covered) entry.copy(status = RouteQuestStatus.Counting, keys = emptySet()) else entry
                })
                val keys = source.questsFor(covered.map { currentPlans[it]!! })
                ensureActive()
                if (current != generation || count != countGeneration) return@launch
                val results = mutableAlternatives.value.toMutableList()
                for (index in currentPlans.indices) {
                    val plan = currentPlans[index] ?: continue
                    val position = covered.indexOf(index)
                    val complete = position >= 0 && source.isCovered(plan)
                    ensureActive()
                    if (current != generation || count != countGeneration) return@launch
                    results[index] = RouteQuestUiState(currentPoints[index],
                        if (complete) RouteQuestStatus.Complete else if (index in downloading) RouteQuestStatus.Loading else RouteQuestStatus.Incomplete,
                        if (complete) keys[position] else emptySet())
                }
                publish(results)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                Log.e("RouteQuests", "Unable to count route quests", e)
                if (current == generation && count == countGeneration) publish(mutableAlternatives.value.mapIndexed { index, entry ->
                    if (currentPlans[index] == null) entry else entry.copy(status = RouteQuestStatus.Error, keys = emptySet())
                })
            }
        }
    }

    private fun update(index: Int, status: RouteQuestStatus) {
        publish(mutableAlternatives.value.mapIndexed { i, entry -> if (i == index) entry.copy(status = status, keys = emptySet()) else entry })
    }

    private fun publish(entries: List<RouteQuestUiState>) {
        mutableAlternatives.value = entries
        mutableState.value = entries.getOrNull(selected) ?: RouteQuestUiState()
    }
}

/** Load the tile union only for small incremental cost; otherwise selected route first.
 * Tile sets preserve bends and holes, unlike a bounding box around all alternatives. */
internal fun sharedRouteDownloadPlan(selected: RouteQuestCorridor, plans: List<RouteQuestCorridor>): RouteQuestCorridor {
    val tiles = plans.flatMapTo(linkedSetOf()) { it.tiles }
    fun area(set: Set<de.westnordost.streetcomplete.data.download.tiles.TilePos>) =
        set.sumOf { it.asBoundingBox(ApplicationConstants.DOWNLOAD_TILE_ZOOM).area() / 1e6 }
    val selectedArea = area(selected.tiles)
    val unionArea = area(tiles)
    return if (unionArea <= ApplicationConstants.MAX_DOWNLOADABLE_AREA_IN_SQKM && unionArea <= selectedArea * 1.25)
        selected.copy(tiles = tiles) else selected
}
