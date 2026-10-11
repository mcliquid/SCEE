package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.routing.PedestrianRoute
import de.westnordost.streetcomplete.data.routing.RoutingError
import de.westnordost.streetcomplete.data.routing.RoutingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PedestrianRouteUiState(
    val route: PedestrianRoute? = null,
    val loading: Boolean = false,
    val error: RoutingError? = null,
)

/** Owned by the navigation host's ViewModel scope; survives a settings/preset round trip. */
class PedestrianRouteState(
    private val scope: CoroutineScope,
    private val request: suspend (List<LatLon>) -> PedestrianRoute,
) {
    private val mutableState = MutableStateFlow(PedestrianRouteUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0

    fun start(start: LatLon, destination: LatLon) {
        job?.cancel()
        val current = ++generation
        mutableState.value = mutableState.value.copy(loading = true, error = null)
        job = scope.launch {
            try {
                val route = request(listOf(start, destination))
                ensureActive()
                if (generation == current) {
                    mutableState.value = PedestrianRouteUiState(route)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == current) mutableState.value = mutableState.value.copy(
                    loading = false,
                    error = (e as? RoutingException)?.error ?: RoutingError.Network,
                )
            }
        }
    }

    fun clear() {
        ++generation
        job?.cancel()
        job = null
        mutableState.value = PedestrianRouteUiState()
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }
}
