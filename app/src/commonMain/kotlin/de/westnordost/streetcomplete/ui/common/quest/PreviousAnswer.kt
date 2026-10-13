package de.westnordost.streetcomplete.ui.common.quest

/**
 * OSM keys whose current tag values should be shown under the quest title during resurvey
 * (e.g. "Bisher: grade2").
 *
 * Default: derived from a simple wiki link of the form `Key:tracktype`.
 * Quests with other answer keys should override [de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType.previousAnswerKeys].
 */
fun previousAnswerKeysFromWikiLink(wikiLink: String?): List<String> {
    if (wikiLink == null || !wikiLink.startsWith("Key:")) return emptyList()
    val key = wikiLink.removePrefix("Key:")
    if (key.isEmpty() || key.endsWith(":") || key.contains("=") || key.contains(" ")) return emptyList()
    return listOf(key)
}

/** Formats existing tag values for the quest header. Single key → value only; several → `key=value`. */
fun formatPreviousAnswerValues(tags: Map<String, String>, keys: List<String>): String? {
    if (keys.isEmpty()) return null
    val parts = keys.mapNotNull { key ->
        val value = tags[key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (keys.size == 1) value else "$key=$value"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ")
}
