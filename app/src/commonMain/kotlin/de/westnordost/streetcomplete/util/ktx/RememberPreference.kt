package de.westnordost.streetcomplete.util.ktx

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import de.westnordost.streetcomplete.data.preferences.Preferences

@Composable
fun rememberPreferenceString(prefs: Preferences, key: String, default: String): String {
    var value by remember(key) { mutableStateOf(prefs.getString(key, default)) }
    DisposableEffect(key) {
        val listener = prefs.addStringListener(key, default) { value = it }
        onDispose { listener.deactivate() }
    }
    return value
}
