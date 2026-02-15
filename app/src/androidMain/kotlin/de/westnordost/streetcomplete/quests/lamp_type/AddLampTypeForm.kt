package de.westnordost.streetcomplete.quests.lamp_type

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm

class AddLampTypeForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf(
        "led",
        "high_pressure_sodium",
        "low_pressure_sodium",
        "gaslight",
        "fluorescent",
        "incandescent",
        "metal-halide",
        "mercury",
        "halogen",
    )

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "led" -> R.string.quest_lampType_led
            "high_pressure_sodium" -> R.string.quest_lampType_highPressureSodium
            "low_pressure_sodium" -> R.string.quest_lampType_lowPressureSodium
            "gaslight" -> R.string.quest_lampType_gaslight
            "fluorescent" -> R.string.quest_lampType_fluorescent
            "incandescent" -> R.string.quest_lampType_incandescent
            "metal-halide" -> R.string.quest_lampType_metalHalide
            "mercury" -> R.string.quest_lampType_mercury
            "halogen" -> R.string.quest_lampType_halogen
            else -> 0
        }))
    }
}
