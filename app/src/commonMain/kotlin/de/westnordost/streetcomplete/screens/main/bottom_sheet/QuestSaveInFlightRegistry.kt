package de.westnordost.streetcomplete.screens.main.bottom_sheet

import de.westnordost.streetcomplete.data.quest.QuestKey

/** Tracks quest keys whose local edit save has started but not finished (success or failure). */
internal class QuestSaveInFlightRegistry {
    private val keys = hashSetOf<QuestKey>()
    private val lock = Any()

    fun tryClaim(key: QuestKey?): Boolean {
        if (key == null) return true
        synchronized(lock) {
            if (key in keys) return false
            keys.add(key)
            return true
        }
    }

    fun release(key: QuestKey?) {
        if (key == null) return
        synchronized(lock) { keys.remove(key) }
    }

    fun isInFlight(key: QuestKey): Boolean = synchronized(lock) { key in keys }
}