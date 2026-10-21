package de.westnordost.streetcomplete.ui.util

import androidx.compose.ui.graphics.Color

/** The existing building/roof palettes and generated overlay colors contain only these hex forms. */
internal fun parseHexColor(value: String): Color {
    require(value.matches(Regex("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?"))) { "Invalid ARGB hex color: $value" }
    val bits = value.substring(1).toLong(16)
    return Color((if (value.length == 7) bits or 0xff000000L else bits).toInt())
}

/** Preserve ColorUtils.blendARGB's sRGB channel arithmetic and truncation (including alpha). */
internal fun nearbyQuestTint(argb: Int): Color {
    fun channel(shift: Int) = ((1f - 0.8f) * ((argb ushr shift) and 255) + 0.8f * 255).toInt()
    return Color((channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0))
}
