package de.westnordost.streetcomplete.quests.is_sidepath

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*

class AddIsSidepathRoadMetadata : OsmElementQuestType<SidepathRoadMetadataAnswer> {

    private val filter by lazy { SIDEPATH_ROAD_METADATA_COMPLETION_FILTER.toElementFilterExpression() }

    override val changesetComment = "Specify road association for sidepaths"
    override val wikiLink = "Key:is_sidepath"
    override val icon = Res.drawable.quest_sidepath_road
    override val hint = Res.string.quest_is_sidepath_road_metadata_hint
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val title = Res.string.quest_is_sidepath_road_metadata_title

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.ways.filter { path ->
            if (!filter.matches(path)) return@filter false
            val geometry = mapData.getWayGeometry(path.id) ?: return@filter false
            isSidepathRoadMetadataCompletionEligible(path, geometry, mapData)
        }

    override fun isApplicableTo(element: Element): Boolean = filter.matches(element)

    @Composable
    override fun Form(
        on: (QuestAction<SidepathRoadMetadataAnswer>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo
    ) {
        SidepathChooseRoadForm(
            on = on,
            pathGeometry = geometry,
            mapAnswer = { ofName, ofHighway ->
                SidepathRoadMetadataAnswer.RoadAssociation(ofName, ofHighway)
            },
            bottomOptionLabelRes = Res.string.quest_is_sidepath_road_metadata_not_sidepath,
            onBottomOptionSelected = { on(Answer(SidepathRoadMetadataAnswer.NotSidepath)) },
        )
    }

    override fun applyAnswerTo(
        answer: SidepathRoadMetadataAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long
    ) {
        when (answer) {
            is SidepathRoadMetadataAnswer.RoadAssociation ->
                applySidepathRoadMetadata(tags, answer.ofName, answer.ofHighway)
            SidepathRoadMetadataAnswer.NotSidepath ->
                applySidepathNotSidepath(tags)
        }
    }
}
