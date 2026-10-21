package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.preferences.Preferences

private const val SEPARATOR = "§"
private const val MAX_RECENT_FEATURES = 35

/**
 * SCEE: feature ids last used for creating nodes, most recent last. [fallback] if there are none.
 * Pass the result reversed to `FeatureSearchDialog.codesOfDefaultFeatures`.
 */
fun recentFeatureIds(
    prefs: Preferences,
    key: String,
    fallback: List<String>,
    exclude: Set<String> = emptySet(),
): List<String> =
    prefs.getString(key, "").split(SEPARATOR).filter { it.isNotBlank() && it !in exclude }.ifEmpty { fallback }

/** Moves [id] to the end of the stored recent feature ids and keeps only the last 35 */
fun addRecentFeatureId(prefs: Preferences, key: String, id: String) {
    val ids = prefs.getString(key, "").split(SEPARATOR).filter { it.isNotBlank() }.toMutableList()
    if (ids.lastOrNull() == id) return
    ids.remove(id)
    ids.add(id)
    prefs.putString(key, ids.takeLast(MAX_RECENT_FEATURES).joinToString(SEPARATOR))
}
