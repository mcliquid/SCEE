package de.westnordost.streetcomplete

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.screens.main.HardwareKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Application preferences and incoming requests, independent of the visible destination. */
class AppViewModel(prefs: Preferences, private val savedState: SavedStateHandle) : ViewModel() {
    val locale = MutableStateFlow(prefs.locale)
    val keepScreenOn = MutableStateFlow(prefs.keepScreenOn)

    val pendingUri = savedState.getStateFlow<String?>("uri", null)

    /** SCEE: hardware keys forwarded by the Android activity. Must be the activity's view model: the
     *  main screen's own `MainViewModel` is scoped to its navigation entry and is another instance. */
    val hardwareKeys = MutableSharedFlow<HardwareKey>(extraBufferCapacity = 1)

    /** SCEE: shared text file (custom quests / tree names) awaiting the user's choice on the main
     *  screen. Saved state, so a cold-start intent survives until the main screen shows it. */
    val textIntentUri = savedState.getStateFlow<String?>("textUri", null)

    private val listeners = listOf(
        prefs.onLocaleChanged { locale.value = it },
        prefs.onKeepScreenOnChanged { keepScreenOn.value = it },
    )

    fun openUri(uri: String) { savedState["uri"] = uri }
    fun consumeUri() { savedState["uri"] = null }
    fun openTextIntent(uri: String) { savedState["textUri"] = uri }
    fun consumeTextIntent() { savedState["textUri"] = null }

    override fun onCleared() { listeners.forEach { it.deactivate() } }
}
