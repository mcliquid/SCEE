package de.westnordost.streetcomplete.quests.crossing_markings_colour

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*

class AddCrossingMarkingsColour : OsmFilterQuestType<CrossingMarkingsColourAnswer>() {

    override val elementFilter = """
        nodes, ways with
          (
            highway = crossing and bicycle ~ yes|designated|official
            or (
              highway ~ cycleway
              and cycleway = crossing
            )
            or (
              highway ~ path
              and path = crossing
              and bicycle ~ yes|designated|official
            )
          )
          and crossing:markings
          and crossing:markings != no
          and !crossing:markings:colour
    """
    override val changesetComment = "Specify colours of bicycle crossing markings"
    override val wikiLink = "Key:crossing:markings:colour"
    override val icon = Res.drawable.quest_crossing_markings_colour
    override val title = Res.string.quest_crossing_markings_colour_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter("""
            nodes, ways with
              highway = crossing and bicycle ~ yes|designated|official
            or (
              highway ~ cycleway
              and cycleway = crossing
            )
            or (
              highway ~ path
              and path = crossing
              and bicycle ~ yes|designated|official
            )
        """)

    @Composable
    override fun Form(
        on: (QuestAction<CrossingMarkingsColourAnswer>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        AddCrossingMarkingsColourForm(on, element)
    }

    override fun applyAnswerTo(
        answer: CrossingMarkingsColourAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        tags["crossing:markings:colour"] = answer.toTagValue()
    }
}
