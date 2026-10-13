package de.westnordost.streetcomplete.screens.main.bottom_sheet

import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.*
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.*
import de.westnordost.streetcomplete.data.osm.geometry.*
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.bottom_sheet.split_way.SplitWayForm
import de.westnordost.streetcomplete.ui.common.quest.*
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Surveyed bounds are kept in the split form; validation never reserves persistent IDs. */
@Composable
fun WaterwayCrossingForm(
    crossing: WaterwayCrossing,
    mapPosition: LatLon,
    onDismiss: () -> Unit,
    onConfirmed: (ElementEditAction) -> Unit,
    onQuestAction: ((Action) -> Unit)? = null,
) {
    val source: MapDataWithEditsSource = koinInject()
    val markers = LocalMapMarkersCallback.current
    var structure by remember { mutableStateOf<WaterwayStructure?>(null) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var submitted by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var saveJob by remember { mutableStateOf<Job?>(null) }

    fun cancelSave() { saveJob?.cancel(); saving = false }

    fun save(action: ElementEditAction) {
        if (submitted || saving) return
        saving = true
        saveJob = scope.launch {
            try {
                val refreshed = withContext(Dispatchers.IO) {
                    val current = refreshCrossingContext(action, crossing, source)
                    validateCrossingAction(current, crossing, source)
                    current
                }
                submitted = true
                onConfirmed(refreshed)
            } catch (e: ConflictException) {
                submitted = false
                error = crossingErrorMessage(e)
            } finally {
                if (saveJob == coroutineContext[Job]) saving = false
            }
        }
    }

    LaunchedEffect(structure) {
        if (structure == null) markers?.invoke(listOf(
            Marker(ElementPolylinesGeometry(listOf(crossing.roadPositions), crossing.position)),
            Marker(ElementPolylinesGeometry(listOf(crossing.waterwayPositions), crossing.position)),
            Marker(ElementPointGeometry(crossing.position), Res.drawable.crosshair),
        ))
    }

    val selected = structure
    if (selected != null) {
        val way = if (selected == WaterwayStructure.BRIDGE) crossing.road else crossing.waterway
        val positions = if (selected == WaterwayStructure.BRIDGE) crossing.roadPositions else crossing.waterwayPositions
        SplitWayForm(
            onConfirmed = { cuts -> save(SetWaterwayStructureAction(crossing, selected, cuts)) },
            onDismiss = { cancelSave(); structure = null },
            mapPosition = mapPosition,
            way = way,
            wayGeometry = ElementPolylinesGeometry(listOf(positions), crossing.position),
            requiredCuts = 2,
            confirmLabel = stringResource(Res.string.waterway_crossing_save),
            contextMarkers = listOf(
                Marker(ElementPolylinesGeometry(listOf(positions), crossing.position)),
                Marker(ElementPointGeometry(crossing.position), Res.drawable.crosshair),
            ),
        )
    } else {
        QuestForm(
            on = { action -> cancelSave(); if (onQuestAction != null) onQuestAction(action) else onDismiss() },
            title = stringResource(Res.string.waterway_crossing_edit),
            subtitle = null,
            hintText = null,
            hintImages = emptyList(),
            note = null,
            answers = if (submitted || saving) emptyList() else buildList {
                if (crossing.road.nodeIds.none { it in crossing.waterway.nodeIds }) addAll(listOf(
                    AnswerItem(stringResource(Res.string.waterway_crossing_bridge)) { structure = WaterwayStructure.BRIDGE },
                    AnswerItem(stringResource(Res.string.waterway_crossing_culvert)) { structure = WaterwayStructure.CULVERT },
                ))
                if (crossing.road.allowsSimpleWaterwayFord()) add(
                    AnswerItem(stringResource(Res.string.waterway_crossing_ford)) { save(CreateWaterwayFordAction(crossing)) }
                )
            },
            otherAnswers = {
                if (onQuestAction != null) listOf(
                    AnswerItem(stringResource(Res.string.leave_note)) { onQuestAction(Action.LeaveNote) }
                ) else emptyList()
            },
        )
    }

    val problem = error
    if (problem != null) AlertDialog(
        onDismissRequest = { error = null },
        text = { Text(stringResource(problem)) },
        confirmButton = { TextButton(onClick = { error = null }) { Text(stringResource(Res.string.ok)) } },
    )
}

private fun crossingErrorMessage(error: ConflictException): StringResource = when {
    error.message?.startsWith("Additional") == true || error.message == "Crossing is no longer unique" ->
        Res.string.waterway_crossing_multiple
    error.message == "Crossing too close to an existing node" -> Res.string.waterway_crossing_near_node
    else -> Res.string.waterway_crossing_rejected
}

private fun validateCrossingAction(action: ElementEditAction, crossing: WaterwayCrossing, source: MapDataWithEditsSource) {
    val count = action.newElementsCount
    val ids = List(count.nodes) { ElementKey(ElementType.NODE, -Long.MAX_VALUE + it) } +
        List(count.ways) { ElementKey(ElementType.WAY, -Long.MAX_VALUE + it) }
    val changes = action.createUpdates(source, ElementIdProvider(ids))
    // In phase 1 the structure may cross only the chosen counterpart. A larger structure
    // involving additional transport ways requires a survey of all their relative layers.
    val editedWays = (changes.creations + changes.modifications).filterIsInstance<Way>()
    val target = editedWays.find { "bridge" in it.tags || "tunnel" in it.tags }
    if (target != null) {
        val createdNodes = changes.creations.filterIsInstance<Node>().associateBy { it.id }
        val positions = target.nodeIds.map { createdNodes[it]?.position ?: (source.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry")) }
        val nearby = source.getMapDataWithGeometry(crossing.position.enclosingBoundingBox(500.0))
        val others = nearby.ways.filter { it.id != crossing.road.id && it.id != crossing.waterway.id
            && ("highway" in it.tags || "waterway" in it.tags || "railway" in it.tags) }
        for (other in others) {
            val otherPositions = other.nodeIds.map { source.getNode(it)?.position ?: throw ConflictException("Incomplete surrounding geometry") }
            if (crossingPoints(positions, otherPositions).isNotEmpty() || hasOverlappingCrossingEdges(positions, otherPositions))
                throw ConflictException("Additional crossing in selected structure; use manual editing")
        }
    } else {
        val nearby = source.getMapDataWithGeometry(crossing.position.enclosingBoundingBox(500.0))
        for (other in nearby.ways.filter { it.id != crossing.road.id && it.id != crossing.waterway.id }) {
            val positions = other.nodeIds.map { source.getNode(it)?.position ?: throw ConflictException("Incomplete surrounding geometry") }
            if (positions.zipWithNext().any { (a, b) -> crossing.position.distanceToArc(a, b) < 1.0 })
                throw ConflictException("Additional way at ford; use manual editing")
        }
    }
}

private fun refreshCrossingContext(action: ElementEditAction, crossing: WaterwayCrossing, source: MapDataWithEditsSource): ElementEditAction {
    val nearby = source.getMapDataWithGeometry(crossing.position.enclosingBoundingBox(500.0))
    // A long selected way can have no nodes in the spatial cache's queried area.
    val road = source.getWayComplete(crossing.road.id) ?: throw ConflictException("Crossing road unavailable")
    val water = source.getWayComplete(crossing.waterway.id) ?: throw ConflictException("Crossing waterway unavailable")
    val data = MutableMapDataWithGeometry(nearby.toList() + road.toList() + water.toList(), emptyList())
    val current = findWaterwayCrossings(data, crossing.position, 0.05)
        .singleOrNull { it.road.id == crossing.road.id && it.waterway.id == crossing.waterway.id }
        ?: run {
            // Explain known safety failures without repeating a successful action validation.
            crossing.validate(source, allowSharedFord = action is CreateWaterwayFordAction)
            throw ConflictException("Crossing unavailable or ambiguous")
        }
    return when (action) {
        is SetWaterwayStructureAction -> action.copy(crossing = action.crossing.copy(nearbyWays = (action.crossing.nearbyWays + current.nearbyWays).distinctBy { it.way.id }))
        is CreateWaterwayFordAction -> action.copy(crossing = action.crossing.copy(nearbyWays = (action.crossing.nearbyWays + current.nearbyWays).distinctBy { it.way.id }))
        else -> action
    }
}
