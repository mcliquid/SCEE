package de.westnordost.streetcomplete.quests.lamp_mount

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm

class AddLampMountForm : ARadioGroupQuestForm<LampMountAnswer, LampMountAnswer>() {
    override val items: List<LampMountAnswer> = LampMount.entries + Support.entries

    @Composable override fun BoxScope.ItemContent(item: LampMountAnswer) {
        Text(stringResource(item.title))
    }
}
