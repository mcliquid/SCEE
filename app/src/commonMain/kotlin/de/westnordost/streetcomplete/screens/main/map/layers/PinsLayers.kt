package de.westnordost.streetcomplete.screens.main.map.layers

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp as composeSp
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.pin_circle
import de.westnordost.streetcomplete.screens.main.map.MapImages
import de.westnordost.streetcomplete.screens.main.map.isArea
import de.westnordost.streetcomplete.screens.main.map.isLines
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import org.maplibre.compose.expressions.dsl.all
import org.maplibre.compose.expressions.dsl.any
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.convertToColor
import org.maplibre.compose.expressions.dsl.convertToNumber
import org.maplibre.compose.expressions.dsl.convertToString
import org.maplibre.compose.expressions.dsl.gte
import org.maplibre.compose.expressions.dsl.div
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.gt
import org.maplibre.compose.expressions.dsl.image
import org.maplibre.compose.expressions.dsl.log2
import org.maplibre.compose.expressions.dsl.lte
import org.maplibre.compose.expressions.dsl.offset
import org.maplibre.compose.expressions.dsl.plus
import org.maplibre.compose.expressions.dsl.sp
import org.maplibre.compose.expressions.dsl.step
import org.maplibre.compose.expressions.dsl.textOffset
import org.maplibre.compose.expressions.dsl.zoom
import org.maplibre.compose.expressions.value.LineCap
import org.maplibre.compose.expressions.value.TranslateAnchor
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.expressions.value.SymbolAnchor
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.FillLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.layers.SymbolLayer
import org.maplibre.compose.map.LocalMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.GeoJsonOptions
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.StyleHandleException
import org.maplibre.compose.util.DpPadding
import org.maplibre.compose.util.MaplibreComposable
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Geometry
import org.maplibre.spatialk.geojson.Point

/** Display pins on the map, e.g. quest pins or pins for recent edits. Clicking a cluster of pins
 *  reports its expansion zoom via [onZoomToCluster]. */
@MaplibreComposable
@Composable
fun PinsLayers(
    pins: Collection<Pin>,
    mapImages: MapImages,
    onClickPin: (properties: JsonObject) -> ClickResult,
    onZoomToCluster: (targetZoom: Double) -> Unit,
) {
    val mapState = checkNotNull(LocalMapState.current)
    val coroutineScope = rememberCoroutineScope()

    val prefs: Preferences = koinInject()
    val offsetFix = prefs.getBoolean(Prefs.OFFSET_FIX, false)
    val labelColor = if (isSystemInDarkTheme()) Color(0xffccccff) else Color(0xff112244)
    val labelHalo = if (isSystemInDarkTheme()) Color(0xff2e2e48) else Color.White

    val iconPins = remember(pins) { pins.filter { it.dotColor == null } }
    val dotPins = remember(pins) { pins.filter { it.dotColor != null } }
    val pinIcons = remember(iconPins) { iconPins.mapTo(LinkedHashSet()) { it.icon } }
    val pinPainters = pinIcons.associateWith { painterResource(it) }
    LaunchedEffect(mapImages, pinPainters) { mapImages.addPins(pinPainters) }

    val features by produceState<List<Feature<Point, JsonObject>>>(emptyList(), iconPins) {
        value = withContext(Dispatchers.Default) { iconPins.map { it.toGeoJsonFeature() } }
    }
    val dotFeatures by produceState<List<Feature<Point, JsonObject>>>(emptyList(), dotPins) {
        value = withContext(Dispatchers.Default) { dotPins.map { it.toDotFeature() } }
    }
    val geometryFeatures by produceState<List<Feature<Geometry, JsonObject>>>(emptyList(), iconPins) {
        value = withContext(Dispatchers.Default) {
            iconPins.mapNotNull { it.geometry }.distinct().map { geometry ->
                Feature(geometry.toGeometry(), JsonObject(emptyMap()))
            }
        }
    }
    val options = remember {
        GeoJsonOptions(
            cluster = true,
            clusterMaxZoom = CLUSTER_MAX_ZOOM,
            clusterRadius = 55,
        )
    }

    val source = rememberGeoJsonSource(
        data = GeoJsonData.Features(FeatureCollection(features)),
        options = options
    )
    val dotSource = rememberGeoJsonSource(
        data = GeoJsonData.Features(FeatureCollection(dotFeatures)),
    )
    val geometrySource = rememberGeoJsonSource(
        data = GeoJsonData.Features(FeatureCollection(geometryFeatures)),
    )

    val currentOnZoomToCluster by rememberUpdatedState(onZoomToCluster)
    val currentOnClickPin by rememberUpdatedState(onClickPin)

    fun onClickClusterFeature(features: List<Feature<Geometry, JsonObject?>>): ClickResult {
        val feature = features.firstOrNull() ?: return ClickResult.Pass
        val currentHandle = mapState.style.sources[source] ?: return ClickResult.Pass
        coroutineScope.launch {
            val zoom = try {
                currentHandle.getClusterExpansionZoom(feature)
            } catch (e: StyleHandleException) {
                if (mapState.style.sources[source] !== currentHandle) return@launch
                throw e
            }
            currentOnZoomToCluster(zoom)
        }
        return ClickResult.Consume
    }

    fun onClick(features: List<Feature<Geometry, JsonObject?>>): ClickResult {
        val properties = features.firstOrNull()?.properties ?: return ClickResult.Pass
        return currentOnClickPin(properties)
    }

    LineLayer(
        id = "pins-geometry-lines-layer",
        source = geometrySource,
        filter = all(feature.isLines(), zoom().gte(const(16))),
        color = const(Color(0xff0092d1)),
        opacity = const(0.4f),
        width = const(10.dp),
        cap = const(LineCap.Round),
    )
    FillLayer(
        id = "pins-geometry-fill-layer",
        source = geometrySource,
        filter = all(feature.isArea(), zoom().gte(const(17))),
        color = const(Color(0xff0092d1)),
        opacity = const(0.2f),
    )
    SymbolLayer(
        id = "pin-dot-label-layer",
        source = dotSource,
        filter = all(zoom().gt(const(CLUSTER_MAX_ZOOM)), feature.has("label")),
        textField = feature["label"].convertToString(),
        textFont = const(listOf("Roboto Regular")),
        textSize = const(16.composeSp),
        textColor = const(labelColor),
        textHaloColor = const(labelHalo),
        textAnchor = const(SymbolAnchor.Top),
        textOffset = textOffset(0.em, 0.5.em),
        textHaloWidth = const(2.5.dp),
        textOptional = const(true),
        textAllowOverlap = step(
            input = zoom(),
            fallback = const(false),
            21 to const(true),
        ),
        sortKey = feature["dot-order"].convertToNumber(),
    )
    SymbolLayer(
        id = "pin-cluster-layer",
        source = source,
        minZoom = CLUSTER_MIN_ZOOM.toFloat(),
        filter = all(
            zoom().lte(const(CLUSTER_MAX_ZOOM)),
            feature["point_count"].convertToNumber().gt(const(1)),
        ),
        iconImage = image(painterResource(Res.drawable.pin_circle)),
        iconSize = const(0.5f) + (log2(feature["point_count"].convertToNumber()) / const(10f)),
        iconAllowOverlap = const(true),
        iconIgnorePlacement = const(true),
        textField = feature["point_count"].convertToString(),
        textSize = (const(15f) + log2(feature["point_count"].convertToNumber()) / const(1.5f)).sp,
        textFont = const(listOf("Roboto Regular")),
        textOffset = textOffset(0.em, 0.1.em),
        textAllowOverlap = const(true),
        textIgnorePlacement = const(true),
        onClick = ::onClickClusterFeature,
    )
    CircleLayer(
        id = "pin-dot-layer",
        source = source,
        minZoom = CLUSTER_MAX_ZOOM.toFloat(),
        filter = any(
            zoom().gt(const(CLUSTER_MAX_ZOOM)),
            feature["point_count"].convertToNumber(const(1)).lte(const(1)),
        ),
        color = const(Color.White),
        radius = const(5.dp),
        strokeColor = const(Color(0xffaaaaaa)),
        strokeWidth = const(1.dp),
        translate = offset(0.dp, if (offsetFix) 0.dp else (-8).dp), // so that it hides behind the pin
        translateAnchor = const(TranslateAnchor.Viewport),
    )
    SymbolLayer(
        id = "pins-layer",
        source = source,
        filter = zoom().gt(const(CLUSTER_MAX_ZOOM)),
        sortKey = feature["icon-order"].convertToNumber(),
        iconImage = image(feature["icon-image"].convertToString()),
        // constant icon size because click area would become a bit too small and more
        // importantly, dynamic size per zoom + collision doesn't work together well, it
        // results in a lot of flickering.
        iconSize = const(1f),
        iconPadding = const(DpPadding(
            left = 2.5.dp,
            top = -2.5.dp,
            right = 0.dp,
            bottom = -7.dp,
        )),
        iconOffset = const(DpOffset((-4.5).dp, (-34.5).dp)),
        iconAllowOverlap = const(false),
        iconIgnorePlacement = const(false),
        onClick = ::onClick,
    )
    CircleLayer(
        id = "pin-quest-dot-layer",
        source = dotSource,
        filter = zoom().gt(const(16)),
        color = feature["dot-color"].convertToColor(),
        radius = const(8.dp),
        strokeColor = const(if (isSystemInDarkTheme()) Color(0xff333333) else Color(0xff666666)),
        strokeWidth = const(1.dp),
        sortKey = feature["dot-order"].convertToNumber(),
        onClick = ::onClick,
    )
}

private fun Pin.toDotFeature() =
    Feature(
        geometry = position.toGeometry(),
        properties = JsonObject(
            (listOf(
                "dot-order" to kotlinx.serialization.json.JsonPrimitive(order),
                "dot-color" to kotlinx.serialization.json.JsonPrimitive(dotColor),
            ) + properties).toMap()
        ),
    )

private const val CLUSTER_MIN_ZOOM = 13
private const val CLUSTER_MAX_ZOOM = 14
