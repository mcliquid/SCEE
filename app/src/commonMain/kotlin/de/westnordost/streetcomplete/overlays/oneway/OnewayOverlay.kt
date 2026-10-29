package de.westnordost.streetcomplete.overlays.oneway

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.overlays.Overlay
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.oneway.interpretOneway
import de.westnordost.streetcomplete.quests.oneway.AddOneway
import de.westnordost.streetcomplete.quests.oneway.AddOnewayBicycle
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.oneway_overlay_title
import de.westnordost.streetcomplete.resources.quest_bicycleway_oneway
import de.westnordost.streetcomplete.screens.main.map.MapLabel

/** Shows and edits oneway restrictions, with focus on bicycles. All roads and bicycle ways are
 *  selectable, but only those with a directional restriction are highlighted. */
class OnewayOverlay : Overlay {

    override val title = Res.string.oneway_overlay_title
    override val icon = Res.drawable.quest_bicycleway_oneway
    override val changesetComment = "Specify oneway restrictions"
    override val wikiLink: String = "Key:oneway"
    override val achievements = listOf(BICYCLIST)
    override val hidesQuestTypes = setOf(AddOneway::class.simpleName!!, AddOnewayBicycle::class.simpleName!!)
    // the arrows of the background map are not bicycle-aware and do not reflect local edits
    override val hiddenLabels = setOf(MapLabel.ONEWAY_ARROWS)

    override fun getStyledElements(mapData: MapDataWithGeometry): Sequence<Pair<Element, OverlayStyle>> =
        mapData.filter(ONEWAY_OVERLAY_FILTER).map { it to onewayWayStyle(interpretOneway(it.tags)) }

    @Composable
    override fun Form(
        on: (OverlayAction) -> Unit,
        element: Element?,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        requireNotNull(element)
        OnewayOverlayForm(on, element)
    }
}

/** roads and ways on which cycling is (likely) allowed */
internal val ONEWAY_OVERLAY_FILTER = """
    ways with
      (
        highway ~ ${ALL_ROADS.joinToString("|")}
        or highway = cycleway
        or (highway ~ path|footway|bridleway and bicycle ~ yes|designated|permissive)
      )
      and area != yes
"""
