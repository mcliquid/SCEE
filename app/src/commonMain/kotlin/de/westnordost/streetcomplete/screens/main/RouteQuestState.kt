package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.routing.RouteCorridorTooLargeException
import de.westnordost.streetcomplete.data.routing.RouteQuestCorridor
import de.westnordost.streetcomplete.data.routing.RouteQuestDataSource
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

/** Navigation-host-owned work; changes originate in quest data, never in GPS/camera updates. */
class RouteQuestState(private val scope: CoroutineScope, private val source: RouteQuestDataSource) {
    private val mutableState = MutableStateFlow(RouteQuestUiState())
    val state = mutableState.asStateFlow()
    private var points: List<LatLon> = emptyList()
    private var corridor: RouteQuestCorridor? = null
    private var job: Job? = null
    private var generation = 0L

    init {
        scope.launch {
            source.changes.conflate().collect {
                // Downloads generate many quest events. Their completion always counts the latest
                // data; do not restart the download or query its partly processed cache.
                if (corridor != null && mutableState.value.status != RouteQuestStatus.Loading) {
                    evaluate(download = false, debounce = true)
                }
            }
        }
    }

    fun setRoute(newPoints: List<LatLon>) {
        if (points == newPoints) return
        points = newPoints
        corridor = null
        evaluate(download = true)
    }

    fun retry() { if (points.isNotEmpty()) evaluate(download = true) }

    private fun evaluate(download: Boolean, debounce: Boolean = false) {
        job?.cancel()
        val request = ++generation
        val currentPoints = points
        mutableState.value = RouteQuestUiState(currentPoints,
            if (download) RouteQuestStatus.Loading else RouteQuestStatus.Counting)
        if (currentPoints.isEmpty()) return
        job = scope.launch {
            try {
                if (debounce) delay(150)
                val plan = corridor ?: source.prepare(currentPoints).also {
                    if (request == generation) corridor = it
                }
                var covered = source.isCovered(plan)
                if (!covered && download) {
                    try {
                        source.load(plan)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("RouteQuests", "Corridor download incomplete", e)
                        if (request == generation) mutableState.value = RouteQuestUiState(currentPoints, RouteQuestStatus.Incomplete)
                        return@launch
                    }
                    covered = source.isCovered(plan)
                }
                if (!covered) {
                    if (request == generation) mutableState.value = RouteQuestUiState(currentPoints, RouteQuestStatus.Incomplete)
                    return@launch
                }
                if (request == generation) mutableState.value = RouteQuestUiState(currentPoints, RouteQuestStatus.Counting)
                val keys = source.quests(plan)
                // Recheck coverage too: another operation may clear/invalidate downloaded data.
                val complete = source.isCovered(plan)
                if (request == generation) mutableState.value = RouteQuestUiState(currentPoints,
                    if (complete) RouteQuestStatus.Complete else RouteQuestStatus.Incomplete,
                    if (complete) keys else emptySet())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("RouteQuests", "Unable to count corridor quests", e)
                if (request == generation) mutableState.value = RouteQuestUiState(currentPoints,
                    if (e is RouteCorridorTooLargeException) RouteQuestStatus.TooLarge else RouteQuestStatus.Error)
            }
        }
    }
}
