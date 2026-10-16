package de.westnordost.streetcomplete.data.imagery

/**
 * Normalizes ELI / iD-style and manual raster URL templates for MapLibre.
 * Single pipeline for ELI layers and user-defined sources (WMS bbox support from PR #898).
 */
object RasterTileUrlNormalizer {

    /**
     * Pattern for ELI `{switch:a,b,c}` placeholders.
     * Both braces must be escaped — Android ICU rejects `\{switch:([^}]+)}`
     * (unescaped trailing `}`) while OpenJDK accepts it.
     */
    internal const val SWITCH_PATTERN = """\{switch:([^}]+)\}"""

    private val switchRegex = Regex(SWITCH_PATTERN)

    fun isValidTemplate(url: String): Boolean {
        if (url.isBlank()) return false
        val hasXyz = url.contains("{x}") &&
            (url.contains("{y}") || url.contains("{-y}")) &&
            (url.contains("{z}") || url.contains("{zoom}"))
        val hasBboxEpsg = url.contains("{bbox-epsg-3857}")
        val hasBboxProj = url.contains("{bbox}") && (url.contains("{proj}") || url.contains("{wkid}"))
        val hasBboxOnly = url.contains("{bbox}") // ELI often hardcodes CRS; runtime injects proj/wkid
        return hasXyz || hasBboxEpsg || hasBboxProj || hasBboxOnly
    }

    fun normalize(url: String): NormalizedRasterSource {
        val usesTms = url.contains("{-y}")
        val base = url
            .replace("{-y}", "{y}")
            .replace("{zoom}", "{z}")
            .replace("{bbox}", "{bbox-epsg-3857}")
            .replace("{width}", "256")
            .replace("{height}", "256")
            .replace("{proj}", "EPSG:3857")
            .replace("{wkid}", "3857")

        val tiles = expandSwitch(base)
        return NormalizedRasterSource(
            tiles = tiles,
            scheme = if (usesTms) RasterTileScheme.Tms else RasterTileScheme.Xyz,
            tileSize = 256,
        )
    }

    private fun expandSwitch(url: String): List<String> {
        val match = switchRegex.find(url) ?: return listOf(url)
        val options = match.groupValues[1].split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (options.isEmpty()) return listOf(url)
        return options.map { option -> url.replace(match.value, option) }
    }
}

enum class RasterTileScheme { Xyz, Tms }

data class NormalizedRasterSource(
    val tiles: List<String>,
    val scheme: RasterTileScheme,
    val tileSize: Int = 256,
)
