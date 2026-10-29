package de.westnordost.streetcomplete.quests.surface

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.FILTER_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.cycleway_track_surface.BOTH_CYCLEWAY_SURFACE_KEY
import de.westnordost.streetcomplete.osm.cycleway_track_surface.CyclewayTrackSurfaceAnswer
import de.westnordost.streetcomplete.osm.cycleway_track_surface.applyCyclewayTrackSurfaceAnswer
import de.westnordost.streetcomplete.osm.cycleway_track_surface.isCyclewayTrackSurfaceEligible
import de.westnordost.streetcomplete.resources.*

/** Surveys surface on road-attached cycleway tracks (`cycleway:left/right/both:surface`). Lanes are excluded. */
class AddRoadAttachedCyclewaySurface : OsmElementQuestType<CyclewayTrackSurfaceAnswer> {

    val elementFilter = """
        ways with
          (cycleway:left = track or cycleway:right = track or cycleway:both = track)
          and highway ~ primary|primary_link|secondary|secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|service|pedestrian|track|busway
          and ($FILTER_BICYCLE_ACCESSIBLE)
          and area != yes
          and (!conveying or conveying = no)
          and (!indoor or indoor = no)
    """

    private val filter by lazy { elementFilter.toElementFilterExpression() }

    override val changesetComment = "Specify road-attached cycleway track surfaces"
    override val wikiLink = "Key:cycleway"
    override val icon = Res.drawable.quest_bicycleway_surface
    override val title = Res.string.quest_roadAttachedCyclewaySurface_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val hint = Res.string.quest_street_side_puzzle_tutorial
    override val previousAnswerKeys = listOf(
        BOTH_CYCLEWAY_SURFACE_KEY,
        "cycleway:left:surface",
        "cycleway:right:surface",
    )

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.ways.filter { filter.matches(it) && isCyclewayTrackSurfaceEligible(it.tags) }

    override fun isApplicableTo(element: Element): Boolean =
        element is Way && filter.matches(element) && isCyclewayTrackSurfaceEligible(element.tags)

    @Composable
    override fun Form(
        on: (QuestAction<CyclewayTrackSurfaceAnswer>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        AddRoadAttachedCyclewaySurfaceForm(on, element, geometry, countryInfo)
    }

    override fun applyAnswerTo(
        answer: CyclewayTrackSurfaceAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        applyCyclewayTrackSurfaceAnswer(tags, answer)
    }
}
