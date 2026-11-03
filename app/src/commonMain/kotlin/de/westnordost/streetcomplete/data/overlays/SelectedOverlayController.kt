package de.westnordost.streetcomplete.data.overlays

import com.russhwolf.settings.SettingsListener
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.Listeners

class SelectedOverlayController(
    private val prefs: Preferences,
    private val overlayRegistry: OverlayRegistry
) : SelectedOverlaySource {

    private val listeners = Listeners<SelectedOverlaySource.Listener>()

    /** While the setter writes prefs, ignore duplicate notifications from [settingsListener]. */
    private var suppressSettingsNotification = false

    // must have local reference because the listeners are only a weak reference
    private val settingsListener: SettingsListener = prefs.onSelectedOverlayNameChanged {
        if (!suppressSettingsNotification) notifySelectedOverlayChanged()
    }

    override var selectedOverlay: Overlay?
        set(value) {
            val previousName = prefs.selectedOverlayName
            val previousCustomIndex = prefs.getInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 0)
            suppressSettingsNotification = true
            try {
                when {
                    value == null -> prefs.selectedOverlayName = null
                    value.isCustomOverlayPickerEntry() -> {
                        value.wikiLink?.toIntOrNull()?.let { prefs.putInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, it) }
                        prefs.selectedOverlayName = /*CustomOverlay::class.simpleName*/ "CustomOverlay" // todo, but later...
                    }
                    overlayRegistry.getByName(value.name) != null -> {
                        // Resolve by stable EditType.name, not instance identity (UI may hold non-registry instances).
                        prefs.selectedOverlayName = value.name
                    }
                    else -> prefs.selectedOverlayName = null
                }
            } finally {
                suppressSettingsNotification = false
            }
            if (prefs.selectedOverlayName != previousName ||
                prefs.getInt(Prefs.CUSTOM_OVERLAY_SELECTED_INDEX, 0) != previousCustomIndex
            ) notifySelectedOverlayChanged()
        }
        get() {
            val stored = prefs.selectedOverlayName ?: return null
            val overlay = overlayRegistry.resolveOverlayByStoredName(stored) ?: return null
            if (overlay.name != stored) {
                prefs.selectedOverlayName = overlay.name
            }
            return overlay
        }

    override fun addListener(listener: SelectedOverlaySource.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: SelectedOverlaySource.Listener) {
        listeners.remove(listener)
    }

    private fun notifySelectedOverlayChanged() {
        listeners.forEach { it.onSelectedOverlayChanged() }
    }
}
