package de.westnordost.streetcomplete.quests.socket_type

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.ACheckboxGroupQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel

class AddChargingStationSocketsForm :
    ACheckboxGroupQuestForm<SocketType, Set<SocketType>>() {

    override val items = SocketType.selectableValues

    @Composable
    override fun BoxScope.ItemContent(item: SocketType) {
        ImageWithLabel(
            painter = painterResource(item.iconResId),
            label = stringResource(item.titleResId)
        )
    }

    override fun onClickOk(items: Set<SocketType>) {
        applyAnswer(items)
    }
}
