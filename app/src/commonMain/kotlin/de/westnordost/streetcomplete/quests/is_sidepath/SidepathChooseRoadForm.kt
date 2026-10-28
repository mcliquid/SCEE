package de.westnordost.streetcomplete.quests.is_sidepath

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.map.getIcon
import de.westnordost.streetcomplete.screens.main.map.getTitle
import de.westnordost.streetcomplete.ui.common.NonPredictiveBackHandler
import de.westnordost.streetcomplete.ui.common.RadioGroup
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.getNameLabel
import de.westnordost.streetcomplete.util.ktx.getFeature
import de.westnordost.streetcomplete.util.math.enlargedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun <T> SidepathChooseRoadForm(
    on: (QuestAction<T>) -> Unit,
    pathGeometry: ElementGeometry,
    mapAnswer: (ofName: String?, ofHighway: String?) -> T,
    onBack: (() -> Unit)? = null,
    mapDataSource: MapDataWithEditsSource = koinInject(),
    featureDictionary: FeatureDictionary = koinInject(),
) {
    var candidates by rememberSerializable {
        mutableStateOf<List<CandidateRoad>>(emptyList())
    }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var selection by rememberSerializable {
        mutableStateOf<SidepathRoadSelection?>(null)
    }

    LaunchedEffect(pathGeometry.center) {
        loaded = false
        val bbox = pathGeometry.bounds.enlargedBy(
            MAX_SIDEPATH_DISTANCE_METERS + SIDEPATH_MAP_DATA_QUERY_PADDING_METERS
        )
        val mapData = withContext(Dispatchers.IO) {
            mapDataSource.getMapDataWithGeometry(bbox)
        }
        val found = findCandidateRoads(pathGeometry, mapData)
        candidates = found
        if (selection == null && found.isNotEmpty()) {
            selection = SidepathRoadSelection.Road(found.first().element.id)
        }
        loaded = true
    }

    val mapMarkersCallback = LocalMapMarkersCallback.current
    LaunchedEffect(selection, candidates) {
        val selectedId = (selection as? SidepathRoadSelection.Road)?.elementId
        val markers = if (selectedId == null) {
            emptyList()
        } else {
            candidates.filter { it.element.id == selectedId }.map { candidate ->
                Marker(
                    geometry = candidate.geometry,
                    icon = getIcon(featureDictionary, candidate.element),
                    title = getTitle(candidate.element.tags),
                )
            }
        }
        mapMarkersCallback?.invoke(markers)
    }

    fun clearAndGoBack() {
        selection = null
        mapMarkersCallback?.invoke(emptyList())
        onBack?.invoke()
    }

    QuestForm(
        on = on,
        title = stringResource(Res.string.quest_is_sidepath_road_title),
        isComplete = loaded && selection != null,
        hasChanges = selection != null,
        onClickOk = {
            when (val selected = selection) {
                is SidepathRoadSelection.Road -> {
                    val candidate = candidates.firstOrNull { it.element.id == selected.elementId }
                    on(
                        Answer(
                            mapAnswer(
                                candidate?.name,
                                candidate?.highway,
                            )
                        )
                    )
                }
                SidepathRoadSelection.CantSay, null ->
                    on(Answer(mapAnswer(null, null)))
            }
        },
    ) {
        if (!loaded) {
            Text(stringResource(Res.string.quest_is_sidepath_road_loading))
        } else {
            val options = buildList {
                addAll(candidates.map { SidepathRoadSelection.Road(it.element.id) })
                add(SidepathRoadSelection.CantSay)
            }
            RadioGroup(
                options = options,
                onSelectionChange = { selection = it },
                selectedOption = selection,
                itemContent = { option ->
                    when (option) {
                        is SidepathRoadSelection.Road -> {
                            val candidate = candidates.first { it.element.id == option.elementId }
                            CandidateRoadLabel(candidate, featureDictionary)
                        }
                        SidepathRoadSelection.CantSay -> {
                            Text(stringResource(Res.string.quest_is_sidepath_road_cant_say))
                        }
                    }
                },
            )
        }
    }

    if (onBack != null) {
        NonPredictiveBackHandler { clearAndGoBack() }
    }
}

@Composable
private fun CandidateRoadLabel(
    candidate: CandidateRoad,
    featureDictionary: FeatureDictionary,
) {
    val nameLabel = getNameLabel(candidate.element.tags)
    val featureName = featureDictionary.getFeature(candidate.element)?.name
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (nameLabel != null) {
            Text(nameLabel)
            if (featureName != null) {
                Text(
                    text = featureName,
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
        } else if (featureName != null) {
            Text(stringResource(Res.string.quest_is_sidepath_unnamed_road, featureName))
        } else {
            Text(stringResource(Res.string.quest_is_sidepath_unnamed_road, candidate.highway ?: "road"))
        }
    }
}

@Serializable
private sealed interface SidepathRoadSelection {
    @Serializable
    data class Road(val elementId: Long) : SidepathRoadSelection
    @Serializable
    data object CantSay : SidepathRoadSelection
}
