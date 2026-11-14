package de.westnordost.streetcomplete.quests.surface

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.SurfaceTracktypeResolution
import de.westnordost.streetcomplete.osm.surface.hasSurfaceTracktypeConflict
import de.westnordost.streetcomplete.osm.surface.icon
import de.westnordost.streetcomplete.osm.surface.parseSurface
import de.westnordost.streetcomplete.osm.surface.resolutionAfterSurfaceAndTracktypeEdit
import de.westnordost.streetcomplete.osm.surface.title
import de.westnordost.streetcomplete.quests.tracktype.Tracktype
import de.westnordost.streetcomplete.quests.tracktype.icon
import de.westnordost.streetcomplete.quests.tracktype.title
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.painterResource

private enum class ConflictStep { Overview, PickSurface, PickTracktype }

@Composable
fun SurfaceTracktypeConflictForm(
    tags: Map<String, String>,
    countryInfo: CountryInfo,
    on: (QuestAction<SurfaceTracktypeResolution>) -> Unit,
    onBack: () -> Unit,
    initialSurface: Surface? = null,
    initialTracktype: Tracktype? = null,
) {
    val tagSurface = parseSurface(tags["surface"])
    val tagTracktype = Tracktype.entries.find { it.osmValue == tags["tracktype"] }
    if (tagSurface == null || tagTracktype == null) {
        onBack()
        return
    }

    var step by rememberSerializable { mutableStateOf(ConflictStep.Overview) }
    var draftSurface by rememberSerializable { mutableStateOf(initialSurface ?: tagSurface) }
    var draftTracktype by rememberSerializable { mutableStateOf(initialTracktype ?: tagTracktype) }

    fun submitIfConsistent() {
        val resolution = resolutionAfterSurfaceAndTracktypeEdit(
            tags,
            draftSurface,
            draftTracktype.osmValue,
        )
        on(Answer(resolution))
    }

    when (step) {
        ConflictStep.Overview -> {
            QuestForm(
                on = on,
                modifier = Modifier.testTag("surface_tracktype_conflict_overview"),
                answers = listOf(
                    AnswerItem(stringResource(Res.string.quest_surface_tracktype_conflict_correct_surface)) {
                        step = ConflictStep.PickSurface
                    },
                    AnswerItem(stringResource(Res.string.quest_surface_tracktype_conflict_correct_tracktype)) {
                        step = ConflictStep.PickTracktype
                    },
                    AnswerItem(stringResource(Res.string.quest_surface_tracktype_conflict_keep_both)) {
                        on(Answer(SurfaceTracktypeResolution.JustifiedException(draftSurface, draftTracktype.osmValue)))
                    },
                ),
                otherAnswers = {
                    listOf(
                        AnswerItem(stringResource(Res.string.action_back)) { onBack() },
                        AnswerItem(stringResource(Res.string.quest_generic_answer_differs_along_the_way)) {
                            on(Action.CantSay)
                        },
                    )
                },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(Res.string.quest_surface_tracktype_conflict_explanation),
                        style = MaterialTheme.typography.body2,
                    )
                    Text(
                        stringResource(
                            Res.string.quest_surface_tracktype_conflict_current,
                            stringResource(draftSurface.title),
                            stringResource(draftTracktype.title),
                        ),
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.testTag("surface_tracktype_conflict_values"),
                    )
                }
            }
        }
        ConflictStep.PickSurface -> {
            ItemSelectQuestForm(
                on = { action ->
                    when (action) {
                        is Answer<Surface> -> {
                            draftSurface = action.value
                            if (!hasSurfaceTracktypeConflict(draftSurface.osmValue, draftTracktype.osmValue)) {
                                submitIfConsistent()
                            } else {
                                step = ConflictStep.Overview
                            }
                        }
                        is Action -> on(action)
                    }
                },
                items = Surface.getSelectableValuesForWays(countryInfo.countryCode),
                itemContent = { item ->
                    ImageWithLabel(item.icon?.let { painterResource(it) }, stringResource(item.title))
                },
                submitOnSelection = true,
                title = stringResource(Res.string.quest_surface_tracktype_conflict_correct_surface),
                otherAnswers = {
                    listOf(
                        AnswerItem(stringResource(Res.string.action_back)) {
                            step = ConflictStep.Overview
                        },
                    )
                },
            )
        }
        ConflictStep.PickTracktype -> {
            ItemSelectQuestForm(
                on = { action ->
                    when (action) {
                        is Answer<Tracktype> -> {
                            draftTracktype = action.value
                            if (!hasSurfaceTracktypeConflict(draftSurface.osmValue, draftTracktype.osmValue)) {
                                submitIfConsistent()
                            } else {
                                step = ConflictStep.Overview
                            }
                        }
                        is Action -> on(action)
                    }
                },
                items = Tracktype.entries,
                itemContent = { ImageWithLabel(painterResource(it.icon), stringResource(it.title)) },
                submitOnSelection = true,
                title = stringResource(Res.string.quest_surface_tracktype_conflict_correct_tracktype),
                otherAnswers = {
                    listOf(
                        AnswerItem(stringResource(Res.string.action_back)) {
                            step = ConflictStep.Overview
                        },
                    )
                },
            )
        }
    }
}