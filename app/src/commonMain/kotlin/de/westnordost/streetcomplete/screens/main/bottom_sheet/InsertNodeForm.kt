package de.westnordost.streetcomplete.screens.main.bottom_sheet

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import de.westnordost.streetcomplete.ui.common.NonPredictiveBackHandler
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.osmfeatures.GeometryType
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfos
import de.westnordost.streetcomplete.screens.main.addRecentFeatureId
import de.westnordost.streetcomplete.screens.main.recentFeatureIds
import de.westnordost.streetcomplete.data.meta.get
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.findWaterwayCrossings
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossing
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.visiblequests.LevelFilter
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.cancel
import de.westnordost.streetcomplete.resources.crosshair
import de.westnordost.streetcomplete.resources.insert_node_choose_target
import de.westnordost.streetcomplete.resources.insert_node_select_way
import de.westnordost.streetcomplete.resources.insert_node_target_existing_node
import de.westnordost.streetcomplete.resources.insert_node_target_new_connection
import de.westnordost.streetcomplete.resources.insert_node_target_shared_node_note
import de.westnordost.streetcomplete.resources.insert_node_target_shared_segment
import de.westnordost.streetcomplete.resources.waterway_crossing_edit
import de.westnordost.streetcomplete.screens.main.map.getIcon
import de.westnordost.streetcomplete.screens.main.map.getTitle
import de.westnordost.streetcomplete.ui.common.FloatingOkButton
import de.westnordost.streetcomplete.ui.common.SwitchMapBackgroundButton
import de.westnordost.streetcomplete.ui.common.bottom_sheet.BottomSheetFormScaffold
import de.westnordost.streetcomplete.ui.common.feature.FeatureSearchDialog
import de.westnordost.streetcomplete.ui.common.quest.LocalGetOffsetCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.pxToDp
import de.westnordost.streetcomplete.ui.ktx.toPx
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.util.getNameAndLocationLabel
import de.westnordost.streetcomplete.util.math.PositionOnCrossingWaySegments
import de.westnordost.streetcomplete.util.math.PositionOnWay
import de.westnordost.streetcomplete.util.math.PositionOnWaySegment
import de.westnordost.streetcomplete.util.math.PositionOnWaysSegment
import de.westnordost.streetcomplete.util.math.VertexOfWay
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.math.affectedWayIds
import de.westnordost.streetcomplete.util.math.getInsertNodeTargets
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InsertNodeForm(
    position: LatLon,
    onDismiss: () -> Unit,
    highlightGeometries: ((Collection<ElementGeometry>) -> Unit)?,
    onSelectFeature: (Feature, PositionOnWay) -> Unit,
    mapPosition: LatLon = position,
    onCrossingEdit: (ElementEditAction, LatLon) -> Unit = { _, _ -> },
) {
    val prefs: Preferences = koinInject()
    val mapDataSource: MapDataWithEditsSource = koinInject()
    val levelFilter: LevelFilter = koinInject()
    val featureDictionary: FeatureDictionary = koinInject()
    val initialBackground = rememberSaveable { prefs.getString(Prefs.THEME_BACKGROUND, "MAP") }
    val restoreBackgroundAndDismiss = {
        if (prefs.getString(Prefs.THEME_BACKGROUND, "MAP") != initialBackground) {
            prefs.putString(Prefs.THEME_BACKGROUND, initialBackground)
        }
        onDismiss()
    }
    val metersPerDp = LocalMapMetersPerDp.current
    val maxDistanceToCrosshair = (metersPerDp * 24).dp.toPx().toDouble()
    val snapToVertexDistance = (metersPerDp * 12).dp.toPx().toDouble()
    var mapDataBbox by remember { mutableStateOf(position.enclosingBoundingBox(100.0)) }
    var mapData: MapDataWithGeometry by remember { mutableStateOf(mapDataSource.getMapDataWithGeometry(mapDataBbox)) }
    LaunchedEffect(position) {
        // reload mapData if necessary
        if (mapDataBbox.contains(position)) return@LaunchedEffect
        mapDataBbox = position.enclosingBoundingBox(100.0)
        mapData = mapDataSource.getMapDataWithGeometry(mapDataBbox)
    }
    // all loaded ways with complete geometry (also those hidden by the level filter, so that shared
    // nodes and segments are recognized as such). Ways with incomplete geometry are skipped, but
    // then nothing is preselected so that this can't silently lead to choosing another way.
    val (ways, hasIncompleteWays) = remember(position, mapData) {
        var incomplete = false
        val complete = mapData.ways.mapNotNull { way ->
            val positions = way.nodeIds.mapNotNull { mapData.getNode(it)?.position }
            if (positions.size != way.nodeIds.size) {
                incomplete = true
                return@mapNotNull null
            }
            way to positions
        }
        complete to incomplete
    }
    val waysById = remember(ways) { ways.associate { it.first.id to it.first } }
    NonPredictiveBackHandler { restoreBackgroundAndDismiss() }
    val targets = remember(position, ways) {
        position.getInsertNodeTargets(
            ways = ways,
            isCandidate = { levelFilter.levelAllowed(it) },
            maxDistance = maxDistanceToCrosshair,
            snapToVertexDistance = snapToVertexDistance,
            ambiguityDistance = maxDistanceToCrosshair / 6,
            allowPreselection = !hasIncompleteWays,
        )
    }
    // explicit selection survives target recomputation while the same candidate remains valid
    var userSelected by remember { mutableStateOf<PositionOnWay?>(null) }
    val positionOnWay = remember(userSelected, targets) {
        userSelected?.takeIf { sel -> targets.candidates.any { it == sel } } ?: targets.preselected
    }
    val highlightedWays = remember(targets, positionOnWay) {
        val wayIds = positionOnWay?.affectedWayIds ?: targets.candidates.flatMap { it.affectedWayIds }
        wayIds.distinct().mapNotNull { waysById[it] }
    }
    // The existing node insertion query is node-based. A wider local query also finds waterways
    // whose segment crosses here but whose endpoints are outside the 100 m insertion box.
    var crossing by remember(position) { mutableStateOf<WaterwayCrossing?>(null) }
    LaunchedEffect(position) {
        crossing = withContext(Dispatchers.IO) {
            findWaterwayCrossings(mapDataSource.getMapDataWithGeometry(position.enclosingBoundingBox(500.0)), position, 15.0).singleOrNull()
        }
    }
    val selectedCrossing = crossing
    var editCrossing by remember { mutableStateOf(false) }
    if (editCrossing && selectedCrossing != null) {
        WaterwayCrossingForm(selectedCrossing, mapPosition,
            onDismiss = restoreBackgroundAndDismiss,
            onConfirmed = { onCrossingEdit(it, selectedCrossing.position) })
        return
    }
    val showMarkers = LocalMapMarkersCallback.current
    LaunchedEffect(highlightedWays) {
        showMarkers?.invoke(highlightedWays.flatMap { way ->
            way.nodeIds.mapNotNull {
                val node = mapData.getNode(it) ?: return@mapNotNull null
                if (node.tags.isEmpty()) return@mapNotNull null
                Marker(
                    ElementPointGeometry(node.position),
                    getIcon(featureDictionary, node),
                    getTitle(node.tags)
                )
            }
        })
        highlightGeometries?.invoke(highlightedWays.mapNotNull { mapData.getWayGeometry(it.id) })
    }
    var showFeatureSearch by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        val offset = LocalGetOffsetCallback.current?.invoke(positionOnWay?.position ?: position) ?: Offset(0f, 0f)
        Icon(
            painter = painterResource(Res.drawable.crosshair),
            contentDescription = null,
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .size(72.dp)
                .absoluteOffset(
                    x = offset.x.pxToDp() - 36.dp,
                    y = offset.y.pxToDp() - 36.dp
                ),
            tint = MaterialTheme.colors.onSurface
        )
        BottomSheetFormScaffold(
            onDismissRequest = restoreBackgroundAndDismiss,
            content = {
                val featureDictionary: FeatureDictionary = koinInject()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(6.dp)) {
                    fun wayLabel(way: Way): String =
                        runBlocking { getNameAndLocationLabel(getSystemResourceEnvironment(), LayoutDirection.Ltr, way, featureDictionary, false) }
                            ?.text ?: "${way.type} ${way.id}"
                    if (targets.candidates.size > 1 && positionOnWay == null) {
                        Text(stringResource(Res.string.insert_node_choose_target))
                    }
                    targets.candidates.forEach { target ->
                        val labels = target.affectedWayIds.mapNotNull { waysById[it] }.joinToString(", ") { wayLabel(it) }
                        val text = when (target) {
                            is VertexOfWay -> stringResource(Res.string.insert_node_target_existing_node, labels)
                            is PositionOnWaySegment -> labels
                            is PositionOnWaysSegment -> stringResource(Res.string.insert_node_target_shared_segment, labels)
                            is PositionOnCrossingWaySegments -> stringResource(Res.string.insert_node_target_new_connection, labels)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = target == positionOnWay, onClick = { userSelected = target })
                        ) {
                            RadioButton(selected = target == positionOnWay, onClick = null)
                            Text(text, Modifier.padding(start = 4.dp))
                        }
                    }
                    Divider()
                    val selected = positionOnWay
                    val details = when {
                        selected == null -> stringResource(Res.string.insert_node_select_way)
                        selected is VertexOfWay -> listOfNotNull(
                            if (selected.wayIds.size > 1) stringResource(Res.string.insert_node_target_shared_node_note) else null,
                            mapData.getNode(selected.nodeId)?.tags?.entries?.joinToString("\n") { "${it.key} = ${it.value}" }
                        ).joinToString("\n\n")
                        else -> selected.affectedWayIds.mapNotNull { waysById[it] }.joinToString("\n\n") { way ->
                            way.tags.entries.joinToString("\n") { "${it.key} = ${it.value}" }
                        }
                    }
                    Text(details, Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()))
                    Divider()
                    if (crossing != null) {
                        TextButton(onClick = { editCrossing = true }) { Text(stringResource(Res.string.waterway_crossing_edit)) }
                    }
                    Row {
                        TextButton({ restoreBackgroundAndDismiss() }) { Text(stringResource(Res.string.cancel)) }
                        SwitchMapBackgroundButton()
                    }
                }
            },
            fab = {
                if (positionOnWay != null)
                    FloatingOkButton({ showFeatureSearch = true })
            }
        )
        if (showFeatureSearch) {
            val countryBoundaries: CountryBoundaries = koinInject()
            val countryInfos: CountryInfos = koinInject()
            val countryInfo = remember(position) { countryInfos.get(countryBoundaries, position) }
            val defaultFeatureIds = recentFeatureIds(prefs, Prefs.INSERT_NODE_RECENT_FEATURE_IDS, listOf("amenity/post_box", "barrier/gate", "highway/crossing/unmarked", "highway/crossing/uncontrolled", "highway/traffic_signals", "barrier/bollard", "traffic_calming/table"))
            FeatureSearchDialog(
                onDismissRequest = { showFeatureSearch = false },
                onSelectedFeature = {
                    if (prefs.getString(Prefs.THEME_BACKGROUND, "MAP") != initialBackground)
                        prefs.putString(Prefs.THEME_BACKGROUND, initialBackground)
                    addRecentFeatureId(prefs, Prefs.INSERT_NODE_RECENT_FEATURE_IDS, it.id)
                    onSelectFeature(it, positionOnWay!!)
                },
                featureDictionary = featureDictionary,
                geometryType = GeometryType.VERTEX,
                countryCode = countryInfo.countryOrSubdivisionCode,
                officialLanguages = countryInfo.officialLanguages,
                filterFn = { true },
                codesOfDefaultFeatures = defaultFeatureIds.reversed(),
                showKeyboardInitially = prefs.getBoolean(Prefs.CREATE_NODE_SHOW_KEYBOARD, true),
            )
        }
    }
}
