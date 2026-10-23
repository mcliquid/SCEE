package de.westnordost.streetcomplete.data

import java.nio.channels.UnresolvedAddressException

actual fun Throwable.isUnresolvedAddressException(): Boolean = this is UnresolvedAddressException
