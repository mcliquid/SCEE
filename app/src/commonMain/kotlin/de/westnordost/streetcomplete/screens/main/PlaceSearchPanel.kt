package de.westnordost.streetcomplete.screens.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.search.*
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun PlaceSearchPanel(state: PlaceSearchUiState, onQuery: (String) -> Unit,
    onClose: () -> Unit, onSelect: (PhotonPlace) -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    Surface(modifier.widthIn(max = 560.dp).fillMaxWidth(), elevation = 6.dp, shape = MaterialTheme.shapes.small) {
        Column {
            Row {
                OutlinedTextField(value = state.query, onValueChange = onQuery,
                    label = { Text(stringResource(Res.string.place_search_hint)) }, singleLine = true,
                    modifier = Modifier.weight(1f).padding(8.dp).focusRequester(focus))
                IconButton(onClick = onClose) {
                    Icon(painterResource(Res.drawable.ic_clear_24), stringResource(Res.string.cancel))
                }
            }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val message = when {
                state.error != null -> when (state.error) {
                    SearchError.Timeout -> Res.string.place_search_timeout
                    SearchError.Server -> Res.string.place_search_server
                    SearchError.InvalidResponse -> Res.string.place_search_response
                    else -> Res.string.place_search_network
                }
                state.query.trim().length < 3 -> Res.string.place_search_minimum
                !state.loading && state.results.isEmpty() -> Res.string.no_search_results
                else -> null
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                if (message != null) item {
                    Text(stringResource(message), Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    if (state.error != null) TextButton(onClick = onRetry) { Text(stringResource(Res.string.place_search_retry)) }
                }
                itemsIndexed(state.results) { _, place ->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(12.dp)) {
                        Text(place.name, style = MaterialTheme.typography.subtitle1)
                        Text(stringResource(place.type.label), style = MaterialTheme.typography.caption)
                        if (place.context.isNotEmpty()) Text(place.context, style = MaterialTheme.typography.body2)
                        // Coordinates also distinguish equal names/types when Photon has no address context.
                        if (state.results.count { it.name == place.name && it.context == place.context && it.type == place.type } > 1) {
                            Text("${place.position.latitude}, ${place.position.longitude}", style = MaterialTheme.typography.caption)
                        }
                    }
                    Divider()
                }

            }
        }
    }
}

private val PlaceType.label: StringResource get() = when (this) {
    PlaceType.Attraction -> Res.string.place_type_attraction
    PlaceType.Station -> Res.string.place_type_station
    PlaceType.Stop -> Res.string.place_type_stop
    PlaceType.Address -> Res.string.place_type_address
    PlaceType.Street -> Res.string.place_type_street
    PlaceType.City -> Res.string.place_type_city
    PlaceType.Region -> Res.string.place_type_region
    PlaceType.Country -> Res.string.place_type_country
    PlaceType.District -> Res.string.place_type_district
    PlaceType.Cafe -> Res.string.place_type_cafe
    PlaceType.Museum -> Res.string.place_type_museum
    PlaceType.Place -> Res.string.place_type_place
}
