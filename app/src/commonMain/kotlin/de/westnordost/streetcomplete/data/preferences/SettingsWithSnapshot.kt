package de.westnordost.streetcomplete.data.preferences

import com.russhwolf.settings.ObservableSettings

/** Native store values, without guessing their types through typed Settings getters. */
interface SettingsWithSnapshot : ObservableSettings {
    val all: Map<String, *>
}
