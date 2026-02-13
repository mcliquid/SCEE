package de.westnordost.streetcomplete.quests.seating

import de.westnordost.streetcomplete.R
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm

class AddOutdoorSeatingTypeForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf(
        "parklet",
        "pedestrian_zone",
        "street",
        "sidewalk",
        "patio",
        "terrace",
        "balcony",
        "veranda",
        "roof",
        "garden",
        "beach",
    )

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "parklet" -> R.string.quest_seating_parklet
            "pedestrian_zone" -> R.string.quest_seating_pedestrian_zone
            "street" -> R.string.quest_seating_street
            "sidewalk" -> R.string.quest_seating_sidewalk
            "patio" -> R.string.quest_seating_patio
            "terrace" -> R.string.quest_seating_terrace
            "balcony" -> R.string.quest_seating_balcony
            "veranda" -> R.string.quest_seating_veranda
            "roof" -> R.string.quest_seating_roof
            "garden" -> R.string.quest_seating_garden
            "beach" -> R.string.quest_seating_beach
            else -> 0
        }))
    }
}

