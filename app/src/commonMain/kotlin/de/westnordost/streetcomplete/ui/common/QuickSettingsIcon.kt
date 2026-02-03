package de.westnordost.streetcomplete.ui.common

import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.action_settings
import de.westnordost.streetcomplete.resources.ic_settings_48
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun QuickSettingsIcon() {
    Icon(painterResource(Res.drawable.ic_settings_48), stringResource(Res.string.action_settings))
}
