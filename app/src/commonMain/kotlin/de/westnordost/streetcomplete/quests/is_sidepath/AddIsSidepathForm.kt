package de.westnordost.streetcomplete.quests.is_sidepath

import androidx.compose.runtime.Composable
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
) {
    QuestForm(
        on = on,
        answers = listOf(
            AnswerItem(
                stringResource(Res.string.quest_generic_hasFeature_yes)
            ) {
                on(Answer(IsSidepathAnswer.Yes))
            },

            AnswerItem(
                stringResource(Res.string.quest_generic_hasFeature_no)
            ) {
                on(Answer(IsSidepathAnswer.No))
            }
        ),
        otherAnswers = {
            listOfNotNull(
                if (element.tags["highway"] == "footway") {
                    AnswerItem(
                        stringResource(Res.string.quest_is_sidepath_answer_is_sidewalk)
                    ) {
                        on(Answer(IsSidepathAnswer.IsSidewalk))
                    }
                } else {
                    null
                },

                AnswerItem(
                    stringResource(Res.string.quest_is_sidepath_answer_is_crossing)
                ) {
                    on(Answer(IsSidepathAnswer.IsCrossing))
                }
            )
        }
    )
}
