package de.westnordost.streetcomplete.util.platform

import io.github.vinceglb.filekit.PlatformFile
import java.io.File
import java.net.URI

actual fun platformFileFromUri(uri: String): PlatformFile {
    val scheme = runCatching { URI(uri).scheme }.getOrNull()
    // a one-letter scheme is a Windows drive letter, not a URI scheme
    return PlatformFile(if (scheme == "file") File(URI(uri)) else File(uri))
}
