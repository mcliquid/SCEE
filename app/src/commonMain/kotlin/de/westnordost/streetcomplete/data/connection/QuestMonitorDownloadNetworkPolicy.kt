package de.westnordost.streetcomplete.data.connection

/**
 * Whether the nearby quest monitor should skip a background map download after checking network state.
 *
 * @param networkStatePermissionGranted `ACCESS_NETWORK_STATE` granted; if false, network is not checked
 *   (same as legacy behavior).
 * @param capabilities Snapshot from [ActiveNetworkConnection.snapshotCapabilities]; only read when
 *   [networkStatePermissionGranted] is true.
 */
fun shouldSkipQuestMonitorBackgroundDownload(
    networkStatePermissionGranted: Boolean,
    capabilities: NetworkCapabilities?,
): Boolean {
    if (!networkStatePermissionGranted) return false
    if (capabilities == null) return true
    if (!capabilities.hasInternet) return true
    return false
}
