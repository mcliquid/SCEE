package de.westnordost.streetcomplete

import android.content.ComponentCallbacks2
import android.os.Build

enum class MemoryTrimAction {
    NONE,
    TRIM_CACHES,
    CLEAR_CACHES,
}

/**
 * Maps [ComponentCallbacks2.onTrimMemory] levels to cache actions.
 *
 * From API 34 (Android 14) the system no longer delivers deprecated trim levels; only
 * [ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN] and higher background levels remain. We trim (not clear)
 * map caches on those levels to avoid expensive reloads.
 *
 * Below API 34, legacy level-specific behavior is preserved (including [MemoryTrimAction.CLEAR_CACHES]
 * for the most severe levels).
 */
fun memoryTrimActionFor(level: Int, sdkInt: Int = Build.VERSION.SDK_INT): MemoryTrimAction {
    if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> MemoryTrimAction.TRIM_CACHES
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> MemoryTrimAction.TRIM_CACHES
            else -> MemoryTrimAction.NONE
        }
    }
    @Suppress("DEPRECATION")
    return when (level) {
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
        -> MemoryTrimAction.CLEAR_CACHES
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
        -> MemoryTrimAction.TRIM_CACHES
        else -> MemoryTrimAction.NONE
    }
}
