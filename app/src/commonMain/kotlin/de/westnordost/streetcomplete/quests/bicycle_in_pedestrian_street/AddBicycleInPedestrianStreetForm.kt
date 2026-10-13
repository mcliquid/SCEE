package de.westnordost.streetcomplete.quests.bicycle_in_pedestrian_street

import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.AddConditionalDialog
import de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street.BicycleInPedestrianStreet.*
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.RadioGroupQuestForm
import org.jetbrains.compose.resources.stringResource

@Composable
fun AddBicycleInPedestrianStreetForm(
    on: (QuestAction<BicycleInPedestrianStreetAnswer>) -> Unit,
    countryInfo: CountryInfo,
) {
    var showConditionalDialog by remember { mutableStateOf(false) }
    RadioGroupQuestForm(
        on = { on(it) },
        items = listOf(ALLOWED, NOT_ALLOWED, NOT_SIGNED).map { BicycleInPedestrianStreetAnswer.Sign(it) },
        itemContent = {
            val value = it.value
            Text(stringResource(when (value) {
                ALLOWED -> Res.string.quest_generic_hasFeature_yes
                NOT_ALLOWED -> Res.string.quest_generic_hasFeature_no
                else -> Res.string.quest_bicycleInPedestrianStreet_noSign
            }))
        },
        otherAnswers = {
            listOf(AnswerItem(stringResource(Res.string.quest_bicycleInPedestrianStreet_conditional)) {
                showConditionalDialog = true
            })
        },
    )
    if (showConditionalDialog) {
        AddConditionalDialog(
            onDismissRequest = { showConditionalDialog = false },
            keys = listOf("bicycle"),
            values = listOf("yes", "no", "dismount", "permissive", "destination", "customers", "private"),
            numberOnly = false,
            countryInfo = countryInfo,
        ) { _, value ->
            on(Answer(BicycleInPedestrianStreetAnswer.Conditional(value)))
        }
    }
}
