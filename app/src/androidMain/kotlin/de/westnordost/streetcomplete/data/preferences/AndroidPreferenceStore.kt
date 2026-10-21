package de.westnordost.streetcomplete.data.preferences

import android.content.SharedPreferences
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings

class AndroidPreferenceStore(private val store: SharedPreferences) :
    SettingsWithSnapshot, ObservableSettings by SharedPreferencesSettings(store) {
    override val all: Map<String, *> get() = store.all
}
