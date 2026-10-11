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
    val routes: List<PedestrianRoute> = emptyList(),
    val selectedIndex: Int = 0,
    val loading: Boolean = false,
    val error: RoutingError? = null,
) {
    val route: PedestrianRoute? get() = routes.getOrNull(selectedIndex)
}

/** Owned by the navigation host's ViewModel scope; survives a settings/preset round trip. */
class PedestrianRouteState(
    private val scope: CoroutineScope,
    private val request: suspend (List<LatLon>) -> List<PedestrianRoute>,
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
                val routes = request(listOf(start, destination))
                require(routes.isNotEmpty())
                ensureActive()
                if (generation == current) {
                    mutableState.value = PedestrianRouteUiState(routes)
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

    fun select(index: Int) {
        if (index in mutableState.value.routes.indices) mutableState.value = mutableState.value.copy(selectedIndex = index)
    }

    fun clear() {
        ++generation
        job?.cancel()
        job = null
        mutableState.value = PedestrianRouteUiState()
    }

    fun dismissError() { mutableState.value = mutableState.value.copy(error = null) }
}
