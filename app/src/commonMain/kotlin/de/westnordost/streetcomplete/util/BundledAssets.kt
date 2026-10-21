package de.westnordost.streetcomplete.util

import de.westnordost.streetcomplete.resources.Res

internal suspend fun readBundledAsset(path: String): ByteArray {
    require(path.isNotEmpty() && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }
        && '\\' !in path && ':' !in path) { "Expected a relative bundled asset path" }
    return Res.readBytes("files/$path")
}

internal suspend fun readBundledText(path: String): String = readBundledAsset(path).decodeToString()

internal suspend fun readBundledLines(path: String): List<String> = assetLines(readBundledText(path))

// Match BufferedReader.readLines(), including empty files and a final line separator.
internal fun assetLines(text: String): List<String> = when {
    text.isEmpty() -> emptyList()
    text.endsWith('\n') || text.endsWith('\r') -> text.lines().dropLast(1)
    else -> text.lines()
}
