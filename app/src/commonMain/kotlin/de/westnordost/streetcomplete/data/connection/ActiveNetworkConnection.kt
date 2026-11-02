package de.westnordost.streetcomplete.data.connection

import kotlinx.coroutines.flow.Flow

/** Provides information about the default active network connection */
interface ActiveNetworkConnection {
    val capabilities: Flow<NetworkCapabilities?>

    /** Synchronous snapshot of the default network, or `null` if none or unknown. */
    fun snapshotCapabilities(): NetworkCapabilities? = null
}

data class NetworkCapabilities(
    val hasInternet: Boolean,
    val isMetered: Boolean
)
