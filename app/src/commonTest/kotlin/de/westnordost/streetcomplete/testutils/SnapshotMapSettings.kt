package de.westnordost.streetcomplete.testutils

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.data.preferences.SettingsWithSnapshot

class SnapshotMapSettings(private val values: MutableMap<String, Any> = mutableMapOf()) :
    SettingsWithSnapshot, ObservableSettings by MapSettings(values) {
    override val all: Map<String, *> get() = values.toMap()
}
