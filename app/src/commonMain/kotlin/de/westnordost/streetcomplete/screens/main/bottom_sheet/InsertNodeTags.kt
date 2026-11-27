package de.westnordost.streetcomplete.screens.main.bottom_sheet

/** A tag the selected preset would set to a different value than the one an existing vertex
 *  already has. */
data class PresetTagConflict(val key: String, val existingValue: String, val presetValue: String)

/** Returns all preset tags whose key already exists on the vertex with a different value. */
fun getPresetTagConflicts(
    existingTags: Map<String, String>,
    presetTags: Map<String, String>,
): List<PresetTagConflict> =
    presetTags.mapNotNull { (key, presetValue) ->
        val existingValue = existingTags[key]
        if (existingValue == null || existingValue == presetValue) null
        else PresetTagConflict(key, existingValue, presetValue)
    }.sortedBy { it.key }

/** Tags initially shown in the tag editor when a preset is applied to an existing vertex.
 *
 *  All existing tags are kept. Preset tags whose key is not used yet are added. A preset value
 *  that differs from an existing value only replaces it if [replaceConflicting] is set, i.e. after
 *  an explicit user decision. Values are never merged with ';'. */
fun getInitialTagsForExistingVertex(
    existingTags: Map<String, String>,
    presetTags: Map<String, String>,
    replaceConflicting: Boolean,
): Map<String, String> {
    val result = existingTags.toMutableMap()
    for ((key, value) in presetTags) {
        if (key !in result || replaceConflicting) result[key] = value
    }
    return result
}
