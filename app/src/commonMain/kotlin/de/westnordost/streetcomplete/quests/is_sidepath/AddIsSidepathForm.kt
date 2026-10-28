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
    var step by rememberSaveable { mutableStateOf(IsSidepathFormStep.AskIfSidepath) }

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
                mapAnswer = { ofName, ofHighway ->
                    IsSidepathAnswer.Yes(ofName, ofHighway)
                },
                onBack = { step = IsSidepathFormStep.AskIfSidepath },
            )
        }
    }
}

private enum class IsSidepathFormStep {
    AskIfSidepath,
    ChooseRoad,
}
