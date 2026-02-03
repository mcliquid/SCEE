package de.westnordost.streetcomplete.quests.map

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm

class AddMapSizeForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf(
        "site",
        "city",
        "landscape",
        "region",
    )

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "site" -> R.string.quest_mapSize_site
            "city" -> R.string.quest_mapSize_city
            "landscape" -> R.string.quest_mapSize_landscape
            "region" -> R.string.quest_mapSize_region
            else -> 0
        }))
    }
}

