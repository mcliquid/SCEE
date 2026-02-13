package de.westnordost.streetcomplete.quests.roof_orientation

import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_roofOrientation_along
import de.westnordost.streetcomplete.resources.quest_roofOrientation_across
import org.jetbrains.compose.resources.stringResource

class AddRoofOrientationForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf("along", "across")

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "along" -> Res.string.quest_roofOrientation_along
            "across" -> Res.string.quest_roofOrientation_across
            else -> null
        }!!))
    }
}
