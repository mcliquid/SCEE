package de.westnordost.streetcomplete.overlays.traffic_sign

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.overlays.Overlay
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.ALL_PATHS
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.ic_overlay_traffic_sign
import de.westnordost.streetcomplete.resources.traffic_sign_overlay_title
import de.westnordost.streetcomplete.util.ktx.isArea

/** Overlay to survey the traffic signs of ways per direction. Germany only: ways that are not
 *  (completely) in Germany are not shown and thus can not be selected. */
class TrafficSignOverlay(
    /** returns the ISO 3166-1 code of the country at the given position */
    private val getCountryCodeByLocation: (LatLon) -> String?,
) : Overlay {

    override val title = Res.string.traffic_sign_overlay_title
    override val icon = Res.drawable.ic_overlay_traffic_sign
    override val changesetComment = "Survey traffic signs"
    override val wikiLink: String = "Key:traffic_sign"

    override fun getStyledElements(mapData: MapDataWithGeometry): Sequence<Pair<Element, OverlayStyle>> =
        mapData.filter("ways with highway ~ ${(ALL_ROADS + ALL_PATHS).joinToString("|")}")
            .filter { it is Way && !it.isArea() && it.tags["area"] != "yes" }
            .filter { isInGermany(mapData.getWayGeometry(it.id)) }
            .map { it to trafficSignWayStyle(it.tags) }

    /** The center and both end points must be in Germany. Ways that cross the border are
     *  excluded: the signs of such a way would be partly of another country, whose catalogue is not
     *  available. (Three lookups in the country boundaries grid per way are cheap) */
    private fun isInGermany(geometry: ElementGeometry?): Boolean {
        if (geometry !is ElementPolylinesGeometry) return false
        val line = geometry.polylines.singleOrNull() ?: return false
        if (line.size < 2) return false
        return listOf(geometry.center, line.first(), line.last()).all { getCountryCodeByLocation(it) == "DE" }
    }

    @Composable
    override fun Form(
        on: (OverlayAction) -> Unit,
        element: Element?,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        requireNotNull(element)
        TrafficSignOverlayForm(on, element as Way, geometry, countryInfo)
    }
}
