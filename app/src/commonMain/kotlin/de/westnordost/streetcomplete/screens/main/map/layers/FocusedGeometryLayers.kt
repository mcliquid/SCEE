package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.map_oneway_arrow
import de.westnordost.streetcomplete.screens.main.map.has
import de.westnordost.streetcomplete.screens.main.map.isArea
import de.westnordost.streetcomplete.screens.main.map.isLines
import de.westnordost.streetcomplete.screens.main.map.isPoint
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.not
import org.maplibre.compose.expressions.value.IconRotationAlignment
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.LineJoin
import org.maplibre.compose.expressions.value.SymbolPlacement
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.compose.util.MaplibreComposable
import kotlin.math.PI
import kotlin.math.cos

/** Display focused element geometry. The focused element geometry is highlighted with a sort of
 *  breathing (sinusoid) animation */
@MaplibreComposable
@Composable
fun FocusedGeometryLayers(geometry: ElementGeometry) {
    val prefs: Preferences = koinInject()
    val showArrows = geometry is ElementPolylinesGeometry &&
        prefs.getBoolean(Prefs.SHOW_WAY_DIRECTION, false)

    val highlightColor = MaterialTheme.colors.secondary
    // breathing effect for highlight
    val highlightTransition = rememberInfiniteTransition()
    val highlight by highlightTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, 0, LinearEasing))
    )
    val breathing = -cos(highlight * 2 * PI) / 2.0 + 0.5 // 0..1
    val opacity = ((1 - breathing) * 0.5 + 0.5).toFloat() // 1 .. 0.5
    val lineWidth = ((breathing + 1) * 8).dp // 8..16
    val circleRadius = ((breathing + 1) * 10).dp // 10..20

    val data = remember(geometry, showArrows) {
        if (showArrows) {
            GeoJsonData.Features(FeatureCollection(listOf(
                Feature(
                    geometry.toGeometry(),
                    JsonObject(mapOf("arrows" to JsonPrimitive("yes"))),
                )
            )))
        } else {
            GeoJsonData.Features(geometry.toGeometry())
        }
    }
    val source = rememberGeoJsonSource(data = data)

    FillLayer(
        id = "focus-geo-fill",
        source = source,
        filter = feature.isArea(),
        opacity = const(0.3f),
        color = const(highlightColor),
    )
    LineLayer(
        id = "focus-geo-lines",
        source = source,
        filter = !feature.isPoint(),
        opacity = const(opacity),
        color = const(highlightColor),
        width = const(lineWidth),
        cap = const(LineCap.Round),
        join = const(LineJoin.Round)
    )
    if (showArrows) {
        SymbolLayer(
            id = "focus-geo-arrows",
            source = source,
            filter = feature.has("arrows", "yes"),
            placement = const(SymbolPlacement.Line),
            iconImage = image(painterResource(Res.drawable.map_oneway_arrow)),
            iconColor = const(highlightColor),
            iconOpacity = const(0.7f),
            iconAllowOverlap = const(true),
            iconIgnorePlacement = const(true),
            iconRotate = const(90),
            iconRotationAlignment = const(IconRotationAlignment.Map),
        )
    }
    CircleLayer(
        id = "focus-geo-circle",
        source = source,
        filter = feature.isPoint(),
        opacity = const(opacity),
        color = const(highlightColor),
        radius = const(circleRadius),
    )
}
