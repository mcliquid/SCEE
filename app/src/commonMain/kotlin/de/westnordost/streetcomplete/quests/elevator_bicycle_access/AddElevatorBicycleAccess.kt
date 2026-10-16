package de.westnordost.streetcomplete.quests.elevator_bicycle_access

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.quests.elevator_bicycle_access.ElevatorBicycleAccessAnswer.*
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import org.jetbrains.compose.resources.stringResource

class AddElevatorBicycleAccess : OsmFilterQuestType<ElevatorBicycleAccessAnswer>() {

    override val elementFilter = """
        nodes, ways with
          highway = elevator
          and access !~ private|no
          and !bicycle
    """

    override val changesetComment = "Survey whether bicycles can be taken in elevator"
    override val wikiLink = "Key:bicycle"
    override val icon = Res.drawable.quest_bicycle_elevator
    override val title = Res.string.quest_elevatorBicycleAccess_title
    override val achievements = listOf(BICYCLIST, PEDESTRIAN)

    @Composable
    override fun Form(on: (QuestAction<ElevatorBicycleAccessAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        QuestForm(
            on = on,
            answers = listOf(
                AnswerItem(stringResource(Res.string.quest_generic_hasFeature_no)) { on(Answer(NO)) },
                AnswerItem(stringResource(Res.string.quest_generic_hasFeature_yes)) { on(Answer(YES)) },
            ),
            otherAnswers = { listOf(
                AnswerItem(stringResource(Res.string.quest_elevatorBicycleAccess_answer_private)) { on(Answer(PRIVATE)) },
            ) },
        )
    }

    override fun applyAnswerTo(answer: ElevatorBicycleAccessAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        when (answer) {
            YES -> tags["bicycle"] = "yes"
            NO -> tags["bicycle"] = "no"
            PRIVATE -> tags["access"] = "private"
        }
    }
}
