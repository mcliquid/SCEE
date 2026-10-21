package de.westnordost.streetcomplete

/** Returns true only when a changed data version or key plan has been saved successfully. */
fun interface PreferencesMigration {
    fun migrate(version: String): Boolean
}
