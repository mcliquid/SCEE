package de.westnordost.streetcomplete.quests.charging_station_socket

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.ui.common.quest.QuestForm

@Composable
fun AddChargingStationSocketForm(
    on: (QuestAction<Map<SocketType, Int>>) -> Unit
) {
    var counts by remember {
        mutableStateOf<Map<SocketType, Int>>(emptyMap())
    }

    QuestForm(
        on = on,
        isComplete = counts.isNotEmpty(),
        hasChanges = counts.isNotEmpty(),
        onClickOk = {
            on(Answer(counts))
        }
    ) {
        SocketTypeAndCountForm(
            counts = counts,
            onCountsChanged = {
                counts = it
            }
        )
    }
}
