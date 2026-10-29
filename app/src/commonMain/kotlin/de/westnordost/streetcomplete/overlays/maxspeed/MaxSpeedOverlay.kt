package de.westnordost.streetcomplete.overlays.maxspeed

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.overlays.Overlay
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.maxspeed.interpretMaxSpeed
import de.westnordost.streetcomplete.quests.max_speed.AddMaxSpeed
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.maxspeed_overlay_title
import de.westnordost.streetcomplete.resources.quest_max_speed

/** Read-only overlay that visualizes how speed limits are tagged. It never infers legal defaults
 *  from the road classification and never changes tags by itself. */
class MaxSpeedOverlay(
    private val getCountryInfoByLocation: (LatLon) -> CountryInfo,
) : Overlay {

    override val title = Res.string.maxspeed_overlay_title
    override val icon = Res.drawable.quest_max_speed
    override val changesetComment = "Specify speed limits"
    override val wikiLink: String = "Key:maxspeed"
    override val achievements = listOf(CAR)
    override val hidesQuestTypes = setOf(AddMaxSpeed::class.simpleName!!)

    override fun getStyledElements(mapData: MapDataWithGeometry): Sequence<Pair<Element, OverlayStyle>> =
        mapData.filter(MAX_SPEED_OVERLAY_FILTER).map { way ->
            val interpretation = interpretMaxSpeed(way.tags)
            // country is only needed to put directional values on the correct side
            val isLeftHandTraffic = interpretation.isDirectional &&
                mapData.getWayGeometry(way.id)?.center?.let { getCountryInfoByLocation(it).isLeftHandTraffic } == true
            way to maxSpeedWayStyle(interpretation, isLeftHandTraffic)
        }

    @Composable
    override fun Form(
        on: (OverlayAction) -> Unit,
        element: Element?,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        requireNotNull(element)
        MaxSpeedOverlayForm(on, element, countryInfo)
    }
}

/** Roads on which speed limits are commonly signed. Tracks and pedestrian streets are only shown
 *  if they carry any speed limit tag; paths, construction and proposed roads are never shown. */
internal const val MAX_SPEED_OVERLAY_FILTER = """
    ways with
      (
        highway ~ motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|busway|road
        or (highway ~ track|pedestrian and ~"(source:|zone:)?maxspeed.*")
      )
      and area != yes
"""
