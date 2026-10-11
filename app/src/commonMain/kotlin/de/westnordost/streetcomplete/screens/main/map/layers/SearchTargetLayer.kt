package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickEvent
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

@Composable
@MaplibreComposable
fun SearchTargetLayer(position: LatLon, onClick: (ClickEvent) -> ClickResult) {
    val data = remember(position) { GeoJsonData.Features(position.toGeometry()) }
    val source = rememberGeoJsonSource(data)
    CircleLayer(id = "search-target", source = source, color = const(Color(0xffd32f2f)),
        radius = const(12.dp), strokeWidth = const(3.dp), strokeColor = const(Color.White), onClick = { onClick(this) })
}
