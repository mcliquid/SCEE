package de.westnordost.streetcomplete.data.imagery.eli

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

data class EliLayer(
    val id: String,
    val name: String,
    val type: String,
    val category: String?,
    val url: String,
    val best: Boolean,
    val overlay: Boolean,
    val minZoom: Int?,
    val maxZoom: Int?,
    val endDate: String?,
    /** null geometry = worldwide */
    val boundingBox: BoundingBox?,
    /**
     * Rings for point-in-polygon. Outer shells counterclockwise, holes clockwise
     * (same convention as [de.westnordost.streetcomplete.util.math.isInMultipolygon]).
     * Empty when worldwide.
     */
    val rings: List<List<LatLon>>,
)
