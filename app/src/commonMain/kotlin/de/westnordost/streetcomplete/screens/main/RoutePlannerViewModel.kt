package de.westnordost.streetcomplete.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.westnordost.streetcomplete.data.routing.PedestrianRoutingClient
import de.westnordost.streetcomplete.data.routing.RouteQuestService
import kotlinx.coroutines.launch

/** Scoped above navigation entries: opening quest settings may dispose MainScreen, but must
 * retain the route when the map returns. */
class RoutePlannerViewModel(client: PedestrianRoutingClient, source: RouteQuestService) : ViewModel() {
    val quests = RouteQuestState(viewModelScope, source)
    val routing = PedestrianRouteState(viewModelScope, client::routes)

    init {
        viewModelScope.launch {
            routing.state.collect { state ->
                quests.setRoutes(state.routes.map { it.points }, state.selectedIndex)
            }
        }
    }
}
