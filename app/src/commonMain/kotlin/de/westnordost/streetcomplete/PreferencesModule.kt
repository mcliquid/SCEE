package de.westnordost.streetcomplete

import de.westnordost.streetcomplete.data.preferences.Preferences
import org.koin.dsl.module

/** Make the legacy quest accessor available before any eagerly created quest consumers. */
internal val preferencesModule = module {
    single(createdAtStart = true) { Preferences(get()).also { Prefs.preferences = it } }
}
