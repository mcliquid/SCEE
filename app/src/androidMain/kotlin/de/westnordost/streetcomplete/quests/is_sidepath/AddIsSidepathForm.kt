package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AbstractOsmQuestForm
import de.westnordost.streetcomplete.quests.AnswerItem

class AddIsSidepathForm : AbstractOsmQuestForm<IsSidepathAnswer>() {

    override val buttonPanelAnswers = listOf(
        AnswerItem(R.string.quest_generic_hasFeature_yes) {
            applyAnswer(IsSidepathAnswer.Yes)
        },
        AnswerItem(R.string.quest_generic_hasFeature_no) {
            applyAnswer(IsSidepathAnswer.No)
        }
    )

    override val otherAnswers get() = listOfNotNull(
        if (element.tags["highway"] == "footway") {
            AnswerItem(R.string.quest_is_sidepath_answer_is_sidewalk) {
                applyAnswer(IsSidepathAnswer.IsSidewalk, true)
            }
        } else null,

        AnswerItem(R.string.quest_is_sidepath_answer_is_crossing) {
            applyAnswer(IsSidepathAnswer.IsCrossing, true)
        }
    )
}

