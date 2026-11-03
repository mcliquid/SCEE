package de.westnordost.streetcomplete.data.overlays

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.user.achievements.editTypeAliases
import de.westnordost.streetcomplete.overlays.custom.CustomOverlay
import de.westnordost.streetcomplete.util.fakeStringResource

/** Picker entry for a user-defined custom overlay slot (not the registry [CustomOverlay] instance). */
fun Overlay.isCustomOverlayPickerEntry(): Boolean = title == fakeStringResource

/** Whether [candidate] is the active overlay in UI lists (registry instances may differ by reference). */
fun isSameSelectedOverlay(
    candidate: Overlay,
    selectedOverlay: Overlay?,
    prefs: Preferences,
): Boolean {
    if (selectedOverlay == null) return false
    return when {
        candidate.isCustomOverlayPickerEntry() && selectedOverlay is CustomOverlay ->
            candidate.wikiLink?.toIntOrNull() == prefs.getInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 0)
        candidate.isCustomOverlayPickerEntry() -> false
        selectedOverlay is CustomOverlay -> false
        else -> candidate.name == selectedOverlay.name
    }
}

/** Resolve persisted overlay type names, including legacy renames (e.g. ShopsOverlay → PlacesOverlay). */
fun OverlayRegistry.resolveOverlayByStoredName(storedName: String): Overlay? =
    getByName(storedName)
        ?: editTypeAliases[storedName]?.let { getByName(it) }
