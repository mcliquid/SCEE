package de.westnordost.streetcomplete.quests

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

@Composable
internal actual fun QuestSettingsHtml(html: String) {
    Text(AnnotatedString.fromHtml(html), style = MaterialTheme.typography.body1)
}
