package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.preferences.Preferences

class DesktopPreferencesMigration(private val prefs: Preferences) : PreferencesMigration {
    override fun migrate(version: String): Boolean {
        if (prefs.lastDataVersion == version) return false
        prefs.lastDataVersion = version
        return true
    }
}
