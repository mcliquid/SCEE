package de.westnordost.streetcomplete.quests.is_sidepath

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddIsSidepathForm(
    on: (QuestAction<IsSidepathAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
) {
    val completingExistingSidepath = element.tags["is_sidepath"] == "yes"
    var step by rememberSaveable {
        mutableStateOf(
            if (completingExistingSidepath) {
                IsSidepathFormStep.ChooseRoad
            } else {
                IsSidepathFormStep.AskIfSidepath
            }
        )
    }

    when (step) {
        IsSidepathFormStep.AskIfSidepath -> {
            val isFootway = element.tags["highway"] == "footway"
            QuestForm(
                on = on,
                answers = listOfNotNull(
                    if (isFootway) {
                        AnswerItem(stringResource(Res.string.quest_is_sidepath_answer_sidewalk)) {
                            on(Answer(IsSidepathAnswer.IsSidewalk))
                        }
                    } else {
                        null
                    },
                    AnswerItem(stringResource(Res.string.quest_is_sidepath_answer_parallel_path)) {
                        step = IsSidepathFormStep.ChooseRoad
                    },
                    AnswerItem(stringResource(Res.string.quest_generic_hasFeature_no)) {
                        on(Answer(IsSidepathAnswer.No))
                    },
                ),
                otherAnswers = {
                    listOf(
                        AnswerItem(
                            stringResource(Res.string.quest_is_sidepath_answer_is_crossing)
                        ) {
                            on(Answer(IsSidepathAnswer.IsCrossing))
                        },
                    )
                }
            )
        }

        IsSidepathFormStep.ChooseRoad -> {
            SidepathChooseRoadForm(
                on = on,
                pathGeometry = geometry,
                mapAnswer = { ofName, ofHighway, ofRef ->
                    IsSidepathAnswer.Yes(ofName, ofHighway, ofRef)
                },
                onBack = if (completingExistingSidepath) {
                    null
                } else {
                    { step = IsSidepathFormStep.AskIfSidepath }
                },
                bottomOptionLabelRes = if (completingExistingSidepath) {
                    Res.string.quest_is_sidepath_road_metadata_not_sidepath
                } else {
                    Res.string.quest_is_sidepath_road_cant_say
                },
                onBottomOptionSelected = if (completingExistingSidepath) {
                    { on(Answer(IsSidepathAnswer.No)) }
                } else {
                    null
                },
                roadSelectionProducesChanges = if (completingExistingSidepath) {
                    // A road whose tags already match produces no edit. Enabling OK for that
                    // match closed the quest without a change, or threw on an empty action.
                    { ofName, ofHighway, ofRef ->
                        sidepathRoadMetadataAnswerProducesChanges(
                            element.tags,
                            ofName,
                            ofHighway,
                            ofRef,
                        )
                    }
                } else {
                    { _, _, _ -> true }
                },
            )
        }
    }
}

private enum class IsSidepathFormStep {
    AskIfSidepath,
    ChooseRoad,
}
