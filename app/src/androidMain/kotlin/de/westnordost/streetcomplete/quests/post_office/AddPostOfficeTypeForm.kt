package de.westnordost.streetcomplete.quests.post_office

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm
import de.westnordost.streetcomplete.R

class AddPostOfficeTypeForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf(
        "bureau",
        "post_annex",
        "post_partner"
    )

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "bureau" -> R.string.quest_postOffice_bureau
            "post_annex" -> R.string.quest_postOffice_postAnnex
            "post_partner" -> R.string.quest_postOffice_postPartner
            else -> 0
        }))
    }
}

