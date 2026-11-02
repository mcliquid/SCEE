package de.westnordost.streetcomplete.data.connection

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestMonitorDownloadNetworkPolicyTest {
    @Test fun `without network state permission download is not skipped`() {
        assertFalse(shouldSkipQuestMonitorBackgroundDownload(networkStatePermissionGranted = false, capabilities = null))
        assertFalse(
            shouldSkipQuestMonitorBackgroundDownload(
                networkStatePermissionGranted = false,
                capabilities = NetworkCapabilities(hasInternet = false, isMetered = false),
            )
        )
    }

    @Test fun `with permission null network skips download`() {
        assertTrue(shouldSkipQuestMonitorBackgroundDownload(networkStatePermissionGranted = true, capabilities = null))
    }

    @Test fun `with permission disconnected or unvalidated internet skips download`() {
        assertTrue(
            shouldSkipQuestMonitorBackgroundDownload(
                networkStatePermissionGranted = true,
                capabilities = NetworkCapabilities(hasInternet = false, isMetered = false),
            )
        )
    }

    @Test fun `with permission validated internet allows download regardless of metered`() {
        assertFalse(
            shouldSkipQuestMonitorBackgroundDownload(
                networkStatePermissionGranted = true,
                capabilities = NetworkCapabilities(hasInternet = true, isMetered = true),
            )
        )
        assertFalse(
            shouldSkipQuestMonitorBackgroundDownload(
                networkStatePermissionGranted = true,
                capabilities = NetworkCapabilities(hasInternet = true, isMetered = false),
            )
        )
    }
}
