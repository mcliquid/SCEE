package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.screens.main.map.toLineGeometry
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

@Composable @MaplibreComposable
fun PedestrianRouteLayer(points: List<LatLon>) {
    val geometry = remember(points) { points.toLineGeometry() } ?: return
    val source = rememberGeoJsonSource(data = GeoJsonData.Features(geometry))
    LineLayer(id = "pedestrian-route-casing", source = source,
        color = const(Color.White), width = const(8.dp), cap = const(LineCap.Round))
    LineLayer(id = "pedestrian-route", source = source,
        color = const(Color(0xff9c27b0)), width = const(5.dp), cap = const(LineCap.Round))
}
