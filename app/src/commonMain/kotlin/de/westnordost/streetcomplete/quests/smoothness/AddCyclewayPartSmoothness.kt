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
import de.westnordost.streetcomplete.osm.FILTER_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*

/** Survey `cycleway:smoothness` on the bicycle strip of a segregated combined foot/cycle way. */
class AddCyclewayPartSmoothness : OsmFilterQuestType<SmoothnessAnswer>() {

    override val elementFilter = """
        ways with
          (
            (
              highway = cycleway
              or (highway ~ path|footway and bicycle and bicycle != no)
              or (highway = bridleway and bicycle ~ designated|yes)
            )
            and (
              highway = footway
              or highway = path and foot !~ no|private
              or (highway ~ cycleway|bridleway and foot and foot !~ no|private)
            )
          )
          and segregated = yes
          and ($FILTER_BICYCLE_ACCESSIBLE)
          and cycleway:surface ~ ${SURFACES_FOR_SMOOTHNESS.joinToString("|")}
          and !(sidewalk or sidewalk:left or sidewalk:right or sidewalk:both)
          and (!conveying or conveying = no)
          and (!indoor or indoor = no)
          and (
            !cycleway:smoothness
            or cycleway:smoothness older today -4 years
            or cycleway:smoothness:date < today -4 years
          )
          and ~path|footway|cycleway|bridleway !~ link
    """
    override val changesetComment = "Specify cycleway path smoothness"
    override val wikiLink = "Key:smoothness"
    override val icon = Res.drawable.quest_way_surface_detail
    override val title = Res.string.quest_cyclewayPartSmoothness_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_difficult_and_time_consuming
    override val hint = Res.string.quest_smoothness_hint

    @Composable
    override fun Form(on: (QuestAction<SmoothnessAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddSmoothnessForm(
            on,
            element,
            surfaceKey = CYCLEWAY_PART_SURFACE_KEY,
            allowIsActuallySteps = false,
        )
    }

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry): Sequence<Element> {
        val nodes = (element as Way).nodeIds
        return mapData.nodes.asSequence().filter { it.id in nodes && barrierFilter.matches(it) }
    }

    private val barrierFilter by lazy {
        "nodes with barrier or traffic_calming or (kerb and kerb !~ no|flush)".toElementFilterExpression()
    }

    override fun applyAnswerTo(answer: SmoothnessAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        if (answer is IsActuallyStepsAnswer) throw IllegalStateException()
        answer.applyTo(tags, prefix = CYCLEWAY_PART_PREFIX)
    }
}

const val CYCLEWAY_PART_PREFIX = "cycleway"
const val CYCLEWAY_PART_SURFACE_KEY = "cycleway:surface"
