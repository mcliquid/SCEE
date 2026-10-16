package de.westnordost.streetcomplete.data.imagery

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.imagery.eli.EliIndex
import de.westnordost.streetcomplete.data.imagery.eli.EliIndexClient
import de.westnordost.streetcomplete.data.imagery.eli.EliLayer
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.util.Mockable

data class ResolvedAerialSource(
    val tiles: List<String>,
    val scheme: RasterTileScheme,
    val minZoom: Int,
    val maxZoom: Int,
    val hideLabels: Boolean,
    /** Selection after applying fallback (may differ from requested). */
    val effectiveSelection: AerialSourceSelection,
    val displayName: String,
)

@Mockable
class AerialImageryResolver(
    private val prefs: Preferences,
    private val manualStore: ManualRasterSourcesStore,
    private val eliIndexClient: EliIndexClient,
) {
    fun currentSelection(): AerialSourceSelection =
        AerialSourceSelection.decode(prefs.getString(Prefs.AERIAL_SOURCE, AerialSourceSelection.DEFAULT_ENCODED))

    fun setSelection(selection: AerialSourceSelection) {
        prefs.putString(Prefs.AERIAL_SOURCE, selection.encode())
    }

    suspend fun resolve(mapCenter: LatLon?): ResolvedAerialSource {
        return try {
            val index = runCatching { eliIndexClient.getIndex() }.getOrNull()
            ensureSelectionValid(index)
            val requested = currentSelection()
            resolveSelection(requested, mapCenter, index) ?: esriResolved().also {
                if (requested !is AerialSourceSelection.Esri && requested !is AerialSourceSelection.Auto) {
                    setSelection(AerialSourceSelection.Esri)
                }
            }
        } catch (_: Throwable) {
            // ELI/manual URL helpers must never crash the map; fall back to Esri.
            esriResolved()
        }
    }

    fun resolveSelection(
        requested: AerialSourceSelection,
        mapCenter: LatLon?,
        index: EliIndex?,
    ): ResolvedAerialSource? {
        return when (requested) {
            AerialSourceSelection.Esri -> esriResolved()
            AerialSourceSelection.Auto -> resolveAuto(mapCenter, index)
            is AerialSourceSelection.Eli -> {
                val layer = index?.findById(requested.id)
                if (layer != null) fromEli(layer, requested)
                else null
            }
            is AerialSourceSelection.Manual -> {
                val manual = manualStore.get(requested.id)
                if (manual != null) fromManual(manual)
                else null
            }
        }
    }

    /**
     * Auto priority: preferred manual covering position → ELI best → Esri.
     * Manuals without coverage never win Auto worldwide.
     */
    fun resolveAuto(mapCenter: LatLon?, index: EliIndex?): ResolvedAerialSource {
        val preferred = mapCenter?.let { preferredManualAt(it) }
        if (preferred != null) {
            return fromManual(preferred).copy(effectiveSelection = AerialSourceSelection.Auto)
        }
        val best = mapCenter?.let { index?.bestAt(it) }
        return if (best != null) fromEli(best, AerialSourceSelection.Auto)
        else esriResolved()
    }

    fun preferredManualAt(position: LatLon): ManualRasterSource? {
        return manualStore.getAll()
            .mapNotNull { source ->
                if (!source.preferInAuto) return@mapNotNull null
                val parsed = ImageryCoverageParser.parse(source.coverage) ?: return@mapNotNull null
                if (!parsed.contains(position)) return@mapNotNull null
                source to parsed
            }
            .sortedWith(
                compareBy<Pair<ManualRasterSource, ParsedCoverage>> { it.second.areaEstimate() }
                    .thenBy { it.first.name }
                    .thenBy { it.first.id }
            )
            .firstOrNull()
            ?.first
    }

    /** If selection points at a deleted/unavailable source, reset pref to Esri. */
    fun ensureSelectionValid(index: EliIndex?) {
        when (val sel = currentSelection()) {
            is AerialSourceSelection.Manual -> {
                if (manualStore.get(sel.id) == null) setSelection(AerialSourceSelection.Esri)
            }
            is AerialSourceSelection.Eli -> {
                if (index != null && index.findById(sel.id) == null) {
                    setSelection(AerialSourceSelection.Esri)
                }
            }
            else -> Unit
        }
    }

    fun coveringEliLayers(mapCenter: LatLon?, index: EliIndex?): List<EliLayer> {
        if (mapCenter == null || index == null) return emptyList()
        return index.covering(mapCenter)
    }

    fun displayNameForSelection(
        selection: AerialSourceSelection,
        mapCenter: LatLon?,
        index: EliIndex?,
    ): String {
        return when (selection) {
            AerialSourceSelection.Esri -> "Esri"
            AerialSourceSelection.Auto -> {
                val resolved = resolveAuto(mapCenter, index)
                resolved.displayName
            }
            is AerialSourceSelection.Eli ->
                index?.findById(selection.id)?.name ?: selection.id
            is AerialSourceSelection.Manual ->
                manualStore.get(selection.id)?.name ?: selection.id
        }
    }

    private fun fromManual(source: ManualRasterSource): ResolvedAerialSource {
        val normalized = RasterTileUrlNormalizer.normalize(source.url)
        return ResolvedAerialSource(
            tiles = normalized.tiles,
            scheme = normalized.scheme,
            minZoom = 0,
            maxZoom = source.maxZoom,
            hideLabels = source.hideLabels,
            effectiveSelection = AerialSourceSelection.Manual(source.id),
            displayName = source.name,
        )
    }

    private fun fromEli(layer: EliLayer, selection: AerialSourceSelection): ResolvedAerialSource {
        val normalized = RasterTileUrlNormalizer.normalize(layer.url)
        return ResolvedAerialSource(
            tiles = normalized.tiles,
            scheme = normalized.scheme,
            minZoom = layer.minZoom ?: 0,
            maxZoom = layer.maxZoom ?: ApplicationConstants.RASTER_DEFAULT_MAXZOOM,
            hideLabels = false,
            effectiveSelection = selection,
            displayName = layer.name,
        )
    }

    private fun esriResolved(): ResolvedAerialSource {
        val normalized = RasterTileUrlNormalizer.normalize(ApplicationConstants.RASTER_DEFAULT_URL)
        return ResolvedAerialSource(
            tiles = normalized.tiles,
            scheme = normalized.scheme,
            minZoom = 0,
            maxZoom = ApplicationConstants.RASTER_DEFAULT_MAXZOOM,
            hideLabels = false,
            effectiveSelection = AerialSourceSelection.Esri,
            displayName = "Esri",
        )
    }
}
