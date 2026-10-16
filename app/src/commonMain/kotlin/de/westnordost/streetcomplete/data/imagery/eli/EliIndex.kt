package de.westnordost.streetcomplete.data.imagery.eli

import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.isInMultipolygon

/** In-memory ELI index with bbox prefilter + point-in-polygon. */
class EliIndex(layers: List<EliLayer>) {

    private val supported: List<EliLayer> = layers.filter { EliIndexParser.isSupported(it) }
    private val worldwide: List<EliLayer> = supported.filter { it.rings.isEmpty() }
    private val regional: List<EliLayer> = supported.filter { it.rings.isNotEmpty() }

    val allSupported: List<EliLayer> get() = supported

    fun covering(position: LatLon): List<EliLayer> {
        val regionalHits = regional.filter { layer ->
            val bbox = layer.boundingBox
            if (bbox != null && !bbox.crosses180thMeridian && !bbox.contains(position)) {
                return@filter false
            }
            position.isInMultipolygon(layer.rings)
        }
        return (regionalHits + worldwide).sortedWith(layerComparator)
    }

    fun bestAt(position: LatLon): EliLayer? =
        covering(position).firstOrNull { it.best }

    fun findById(id: String): EliLayer? = supported.find { it.id == id }

    companion object {
        val layerComparator: Comparator<EliLayer> = compareByDescending<EliLayer> { it.best }
            .thenByDescending { it.endDate.orEmpty() }
            .thenBy { areaEstimate(it) }
            .thenBy { it.name }

        private fun areaEstimate(layer: EliLayer): Double {
            val bbox = layer.boundingBox ?: return Double.MAX_VALUE
            val lat = bbox.max.latitude - bbox.min.latitude
            val lon = bbox.max.longitude - bbox.min.longitude
            return lat * lon
        }
    }
}
