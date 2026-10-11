package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.routing.PedestrianRoute
import de.westnordost.streetcomplete.screens.main.map.toLineGeometry
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

/** Stable index colors shared with the overview. White casing separates them from map styles. */
fun pedestrianRouteColor(index: Int): Color = when (index) {
    1 -> Color(0xff0072b2)
    2 -> Color(0xffd55e00)
    else -> Color(0xff9c27b0)
}

@Composable @MaplibreComposable
fun PedestrianRouteLayer(routes: List<PedestrianRoute>, selectedIndex: Int) {
    // Sources and geometries depend only on the route list, not on the current selection.
    val sources = routes.mapIndexed { index, route ->
        key(index) {
            val geometry = remember(route.points) { route.points.toLineGeometry()!! }
            rememberGeoJsonSource(data = remember(geometry) { GeoJsonData.Features(geometry) })
        }
    }
    // Keep unselected routes underneath the selected route, including common segments.
    for (index in routes.indices.filter { it != selectedIndex } + listOfNotNull(selectedIndex.takeIf { it in routes.indices })) {
        key(index) {
            val selected = index == selectedIndex
            LineLayer(id = "pedestrian-route-casing-$index", source = sources[index],
                color = const(Color.White), width = const(if (selected) 8.dp else 5.dp),
                opacity = const(if (selected) 1f else 0.65f), cap = const(LineCap.Round))
            LineLayer(id = "pedestrian-route-$index", source = sources[index],
                color = const(pedestrianRouteColor(index)), width = const(if (selected) 5.dp else 3.dp),
                opacity = const(if (selected) 1f else 0.65f), cap = const(LineCap.Round))
        }
    }
}
