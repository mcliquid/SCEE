package de.westnordost.streetcomplete.screens.settings

import kotlin.test.*

class QuestMonitorPermissionsTest {
    @Test fun `enable requires all permissions and a supported platform`() {
        var granted = false
        var requests = 0
        val permissions = object : QuestMonitorPermissions {
            override fun ensureGranted(): Boolean { requests++; return granted }
        }
        assertFalse(permissions.enableIfGranted(true))
        granted = true
        assertTrue(permissions.enableIfGranted(true))
        assertEquals(2, requests)
        val unsupported: QuestMonitorPermissions? = null
        assertFalse(unsupported.enableIfGranted(true))
    }

    @Test fun `disabling does not request revoked permissions`() {
        val permissions = object : QuestMonitorPermissions {
            override fun ensureGranted(): Boolean = error("Disabling must not request permissions")
        }
        assertFalse(permissions.enableIfGranted(false))
    }
}
