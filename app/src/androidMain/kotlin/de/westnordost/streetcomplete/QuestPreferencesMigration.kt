package de.westnordost.streetcomplete

import android.content.SharedPreferences
import de.westnordost.streetcomplete.data.preferences.Preferences.Companion.LAST_VERSION_DATA
import de.westnordost.streetcomplete.screens.settings.renamedQuests
import de.westnordost.streetcomplete.util.logs.Log

/** Returns true only after a changed version/key plan has been synchronously committed. */
internal fun migrateQuestPreferences(prefs: SharedPreferences, version: String): Boolean {
    val snapshot = prefs.all
    val oldVersion = snapshot[LAST_VERSION_DATA]
    require(oldVersion == null || oldVersion is String) { "Invalid preferences data version" }
    val writes = linkedMapOf<String, Any>()
    val removals = linkedSetOf<String>()
    val sources = snapshot.keys.mapNotNull { key -> renamedPreferenceKey(key)?.let { it to key } }
        .groupBy({ it.first }, { it.second })

    // Finish the entire plan before opening an editor, including conflicts in later groups.
    for ((target, keys) in sources.toSortedMap()) {
        val values = keys.sorted().associateWith { copyNativeValue(snapshot[it]) }
        if (snapshot.containsKey(target)) {
            // Keep differing sources as lossless backups; the existing target always wins.
            removals += values.filterValues { it == snapshot[target] }.keys
        } else {
            val value = values.values.first()
            if (values.values.any { it != value }) {
                Log.w("QuestPreferencesMigration", "Conflicting legacy quest settings; migration deferred")
                return false
            }
            writes[target] = value
            removals += keys
        }
    }
    // Also repair legacy keys left behind by the old marker-before-rename implementation.
    if (oldVersion == version && writes.isEmpty() && removals.isEmpty()) return false

    val editor = prefs.edit()
    removals.forEach { editor.remove(it) }
    writes.forEach { (key, value) -> editor.putNativeValue(key, value) }
    editor.putString(LAST_VERSION_DATA, version)
    try {
        check(editor.commit()) { "Could not commit quest preferences migration" }
    } catch (failure: Exception) {
        // Android can publish to memory even when commit fails: keep this process retryable too.
        try {
            val restore = prefs.edit()
            if (oldVersion == null) restore.remove(LAST_VERSION_DATA)
            else restore.putString(LAST_VERSION_DATA, oldVersion)
            check(restore.commit()) { "Could not restore preferences migration marker" }
        } catch (restoreFailure: Exception) {
            failure.addSuppressed(restoreFailure)
        }
        throw failure
    }
    return true
}

private val questSettingsKey = Regex("^((?:\\d+_)?qs_)([^_]+)(.*)$")

private fun renamedPreferenceKey(key: String): String? {
    // These key segments identify quests; their filter/label/answer payloads are user data.
    val match = questSettingsKey.matchEntire(key)
    if (match != null) {
        val renamed = renamedQuests[match.groupValues[2]] ?: return null
        return match.groupValues[1] + renamed + match.groupValues[3]
    }
    val prefix = "imageListLastPicked."
    if (!key.startsWith(prefix)) return null
    return renamedQuests[key.removePrefix(prefix)]?.let { prefix + it }
}

private fun copyNativeValue(value: Any?): Any = when (value) {
    is Boolean, is Int, is Long, is Float, is String -> value
    is Set<*> -> value.map { requireNotNull(it as? String) { "Invalid preferences string set" } }.toSet()
    else -> error("Unsupported quest preference value")
}

private fun SharedPreferences.Editor.putNativeValue(key: String, value: Any) {
    when (value) {
        is Boolean -> putBoolean(key, value)
        is Int -> putInt(key, value)
        is Long -> putLong(key, value)
        is Float -> putFloat(key, value)
        is String -> putString(key, value)
        is Set<*> -> putStringSet(key, value.map { it as String }.toSet())
        else -> error("Unsupported quest preference value")
    }
}
