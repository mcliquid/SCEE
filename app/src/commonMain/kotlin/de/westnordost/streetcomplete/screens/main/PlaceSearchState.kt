package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.search.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaceSearchUiState(
    val open: Boolean = false,
    val query: String = "",
    val loading: Boolean = false,
    val results: List<PhotonPlace> = emptyList(),
    val error: SearchError? = null,
    val selected: PhotonPlace? = null,
)

/** Search selection has no reference to routing: moving the map cannot change a route. */
class PlaceSearchState(private val scope: CoroutineScope, private val search: suspend (PhotonQuery) -> List<PhotonPlace>) {
    private val mutableState = MutableStateFlow(PlaceSearchUiState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0
    private var request: PhotonQuery? = null

    fun open() { mutableState.value = mutableState.value.copy(open = true) }
    fun close() {
        generation++
        job?.cancel()
        request = null
        mutableState.value = mutableState.value.copy(open = false, query = "", loading = false, results = emptyList(), error = null)
    }
    fun select(place: PhotonPlace) {
        close()
        mutableState.value = mutableState.value.copy(selected = place)
    }
    fun removeMarker() { mutableState.value = mutableState.value.copy(selected = null) }
    fun retry() {
        val previous = request ?: return
        if (mutableState.value.error == null) return
        request = null
        update(previous.copy(text = mutableState.value.query))
    }

    fun update(query: PhotonQuery) {
        if (!mutableState.value.open) return
        val normalized = query.copy(text = query.text.trim())
        mutableState.value = mutableState.value.copy(query = query.text)
        if (request == normalized) return
        request = normalized
        val version = ++generation
        job?.cancel()
        mutableState.value = mutableState.value.copy(loading = normalized.text.length >= 3, results = emptyList(), error = null)
        if (normalized.text.length < 3) return
        job = scope.launch {
            delay(450)
            try {
                val results = search(normalized)
                ensureActive()
                if (version == generation) mutableState.value = mutableState.value.copy(loading = false, results = results)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (version == generation) mutableState.value = mutableState.value.copy(loading = false,
                    error = (e as? SearchException)?.error ?: SearchError.Network)
            }
        }
    }
}
