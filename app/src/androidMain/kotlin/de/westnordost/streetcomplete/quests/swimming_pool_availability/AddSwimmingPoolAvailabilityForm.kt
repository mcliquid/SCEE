package de.westnordost.streetcomplete.quests.swimming_pool_availability

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm

class AddSwimmingPoolAvailabilityForm : ARadioGroupQuestForm<SwimmingPoolAvailability, SwimmingPoolAvailability>() {
    override val items = SwimmingPoolAvailability.entries

    @Composable override fun BoxScope.ItemContent(item: SwimmingPoolAvailability) {
        Text(stringResource(when (item) {
            SwimmingPoolAvailability.INDOOR_AND_OUTDOOR -> R.string.quest_swimming_pool_indoor_and_outdoor
            SwimmingPoolAvailability.ONLY_INDOOR -> R.string.quest_swimming_pool_indoor_only
            SwimmingPoolAvailability.ONLY_OUTDOOR -> R.string.quest_swimming_pool_outdoor_only
            SwimmingPoolAvailability.NO -> R.string.quest_swimming_pool_no
        }))
    }
}
