package de.westnordost.streetcomplete.data

// java.nio does not exist on iOS; the Darwin engine reports its own errors
actual fun Throwable.isUnresolvedAddressException(): Boolean = false
