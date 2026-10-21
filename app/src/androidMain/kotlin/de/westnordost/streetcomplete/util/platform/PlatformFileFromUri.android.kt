package de.westnordost.streetcomplete.util.platform

import android.net.Uri
import io.github.vinceglb.filekit.PlatformFile

actual fun platformFileFromUri(uri: String): PlatformFile = PlatformFile(Uri.parse(uri))
