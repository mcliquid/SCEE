package de.westnordost.streetcomplete.screens.main.map.components

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon

data class Pin(
    val position: LatLon,
    val icon: Int,
    val properties: Collection<Pair<String, String>> = emptyList(),
    val order: Int = 0,
    val geometry: ElementGeometry? = null,
    val color: String? = null,
)
