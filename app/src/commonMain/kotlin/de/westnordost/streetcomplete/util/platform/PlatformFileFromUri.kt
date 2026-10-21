package de.westnordost.streetcomplete.util.platform

import io.github.vinceglb.filekit.PlatformFile

/** The file a text/view intent points to: a content/file URI on Android, a file URI or plain path on desktop. */
expect fun platformFileFromUri(uri: String): PlatformFile
