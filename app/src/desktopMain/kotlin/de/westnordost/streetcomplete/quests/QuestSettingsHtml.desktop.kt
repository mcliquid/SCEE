package de.westnordost.streetcomplete.quests

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.ui.common.HtmlText

@Composable
internal actual fun QuestSettingsHtml(html: String) {
    HtmlText(html, style = MaterialTheme.typography.body1)
}
