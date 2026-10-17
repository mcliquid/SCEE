package de.westnordost.streetcomplete.quests.sidepath_usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.cycleway.SidepathChoice
import de.westnordost.streetcomplete.osm.cycleway.SidepathChoiceGrid
import de.westnordost.streetcomplete.osm.cycleway.getSidepathAnswers
import de.westnordost.streetcomplete.osm.cycleway.getSidepathChoices
import de.westnordost.streetcomplete.osm.oneway.isOneway
import de.westnordost.streetcomplete.quests.cycleway.AddCyclewayForm
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.QuestForm
import de.westnordost.streetcomplete.ui.util.rememberSerializable
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import org.jetbrains.compose.resources.stringResource

/** Choose where it is compulsory to use a cycleway: in the direction of the arrow, against it or
 *  both. "No / I can't tell" hides the quest, without changing any tags. Other answer: there is
 *  no separate cycleway, which switches to the cycleway form of AddCycleway to correct that. */
@Composable
fun AddSidepathUsageForm(
    on: (QuestAction<SidepathUsageAnswer>) -> Unit,
    element: Element,
    geometry: ElementGeometry,
    countryInfo: CountryInfo,
) {
    var isCorrectingCycleway by rememberSaveable { mutableStateOf(false) }

    if (isCorrectingCycleway) {
        AddCyclewayForm(
            on = { action ->
                when (action) {
                    is Answer -> on(Answer(SidepathUsageAnswer.CorrectedCycleway(action.value)))
                    is Action -> on(action)
                }
            },
            element = element,
            geometry = geometry,
            countryInfo = countryInfo,
            title = stringResource(Res.string.quest_cycleway_title2),
            showQuestHint = false,
            startEditing = true,
        )
        return
    }

    val choices = remember(element) {
        getSidepathChoices(getSidepathAnswers(element.tags, countryInfo.isLeftHandTraffic))
    }
    val geometryRotation = remember(geometry) { geometry.getOrientationOrZero() }
    val isOneway = remember(element) { isOneway(element.tags) }
    var selected by rememberSerializable { mutableStateOf<SidepathChoice?>(null) }

    QuestForm(
        on = on,
        isComplete = selected != null,
        onClickOk = {
            val where = selected?.roadSidepath
            if (where != null) {
                on(Answer(SidepathUsageAnswer.Compulsory(where)))
            } else if (selected == SidepathChoice.NO_OR_UNKNOWN) {
                // nothing to tag. Hidden for good with the standard hide function
                on(Action.HideQuest)
            }
        },
        otherAnswers = { listOf(
            AnswerItem(stringResource(Res.string.quest_sidepath_usage_answer_no_cycleway)) {
                isCorrectingCycleway = true
            },
        ) },
    ) {
        SidepathChoiceGrid(choices, selected, { selected = it }, geometryRotation, isOneway)
    }
}
