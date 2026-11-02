package de.westnordost.streetcomplete

import android.content.ComponentCallbacks2
import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationMemoryTrimPolicyTest {
    @Test fun `Android 14 and above trims on UI hidden and background without clearing`() {
        val sdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, sdk),
        )
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, sdk),
        )
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, sdk),
        )
    }

    @Test fun `Android 13 and below clears on most severe legacy levels`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.CLEAR_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, sdk),
        )
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.CLEAR_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL, sdk),
        )
    }

    @Test fun `Android 13 and below trims on moderate legacy levels`() {
        val sdk = Build.VERSION_CODES.TIRAMISU
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND, sdk),
        )
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN, sdk),
        )
        @Suppress("DEPRECATION")
        assertEquals(
            MemoryTrimAction.TRIM_CACHES,
            memoryTrimActionFor(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW, sdk),
        )
    }

    @Test fun `unknown trim level does nothing`() {
        assertEquals(MemoryTrimAction.NONE, memoryTrimActionFor(0, Build.VERSION_CODES.UPSIDE_DOWN_CAKE))
        assertEquals(MemoryTrimAction.NONE, memoryTrimActionFor(0, Build.VERSION_CODES.TIRAMISU))
    }
}
