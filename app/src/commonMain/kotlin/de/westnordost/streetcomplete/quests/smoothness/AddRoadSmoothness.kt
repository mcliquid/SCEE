package de.westnordost.streetcomplete.quests.smoothness

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*

class AddRoadSmoothness : OsmFilterQuestType<SmoothnessAnswer>() {

    override val elementFilter = """
        ways with
          (
            highway ~ ${ROADS_TO_ASK_SMOOTHNESS_FOR.joinToString("|")}
            or highway = service and service !~ driveway|slipway
          )
          and surface ~ ${SURFACES_FOR_SMOOTHNESS.joinToString("|")}
          and (access !~ private|no or (foot and foot !~ private|no))
          and (
            !smoothness
            or smoothness older today -4 years
            or smoothness:date < today -4 years
          )
    """

    override val changesetComment = "Specify road smoothness"
    override val wikiLink = "Key:smoothness"
    override val icon = Res.drawable.quest_street_surface_detail
    override val title = Res.string.quest_smoothness_title
    override val achievements = listOf(CAR, BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_difficult_and_time_consuming
    override val hint = Res.string.quest_smoothness_hint

    @Composable
    override fun Form(on: (QuestAction<SmoothnessAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddSmoothnessForm(on, element)
    }

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry): Sequence<Element> {
        val nodes = (element as Way).nodeIds
        return mapData.nodes.asSequence().filter { it.id in nodes && barrierFilter.matches(it) }
    }

    private val barrierFilter by lazy {
        "nodes with barrier or traffic_calming".toElementFilterExpression()
    }

    override fun applyAnswerTo(answer: SmoothnessAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        if (answer is IsActuallyStepsAnswer) throw IllegalStateException()
        answer.applyTo(tags)
    }
}

// Surfaces for which smoothness is surveyed; must be supported in AddSmoothnessForm (via photos or aliases).
// Values should exist in the Surface enum (concrete:plates is a documented alias of concrete).
val SURFACES_FOR_SMOOTHNESS = listOf(
    "asphalt", "chipseal", "concrete", "concrete:plates", "concrete:lanes",
    "sett", "unhewn_cobblestone", "paving_stones", "grass_paver",
    "compacted", "gravel", "fine_gravel", "pebblestone",
)

private val ROADS_TO_ASK_SMOOTHNESS_FOR = arrayOf(
    // "trunk","trunk_link","motorway","motorway_link", // too much, motorways are almost by definition smooth asphalt (or concrete)
    "primary", "primary_link", "secondary", "secondary_link", "tertiary", "tertiary_link",
    "unclassified", "residential", "living_street", "pedestrian", "track", "busway",
    // "service", // this is too much (e.g. includes driveways), and the information value is very low
)
