package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import de.westnordost.streetcomplete.screens.main.map.isArea
import de.westnordost.streetcomplete.screens.main.map.isPoint
import de.westnordost.streetcomplete.util.logs.Log
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.gte
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.util.MaplibreComposable

/** User-provided GeoJSON, read from the `name` property as a label. */
@MaplibreComposable
@Composable
fun CustomGeometryLayers(geoJson: String?) {
    val data = remember(geoJson) {
        if (geoJson.isNullOrBlank()) {
            GeoJsonData.Features(org.maplibre.spatialk.geojson.GeometryCollection(emptyList()))
        } else {
            runCatching { GeoJsonData.JsonString(geoJson) }
                .getOrElse {
                    Log.e("CustomGeometrySource", "error setting geoJson: $it")
                    GeoJsonData.Features(org.maplibre.spatialk.geojson.GeometryCollection(emptyList()))
                }
        }
    }
    val source = rememberGeoJsonSource(data = data)
    val color = Color(0xff9e319e)

    FillLayer(
        id = "custom-geo-fill",
        source = source,
        filter = feature.isArea(),
        color = const(color),
        opacity = const(0.3f),
    )
    LineLayer(
        id = "custom-geo-lines",
        source = source,
        filter = !feature.isPoint(),
        color = const(color),
        opacity = const(0.5f),
        width = const(8.dp),
        cap = const(LineCap.Round),
    )
    CircleLayer(
        id = "custom-geo-circle",
        source = source,
        filter = feature.isPoint(),
        color = const(color),
        radius = const(8.dp),
        opacity = const(0.6f),
    )
    SymbolLayer(
        id = "custom-geo-text",
        source = source,
        filter = all(feature.has("name"), zoom().gte(const(14))),
        textField = feature["name"].convertToString(),
        textFont = const(listOf("Roboto Regular")),
        textColor = const(color),
        textSize = const(16.sp),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 1.em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
    )
}
