package de.westnordost.streetcomplete.quests.bicycle_bypass

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.YesNoQuestForm
import de.westnordost.streetcomplete.util.ktx.toYesNo

class AddBicycleBypass : OsmElementQuestType<Boolean> {

    private val trafficCalmingNodeFilter by lazy { """
        nodes with
          traffic_calming ~ "$BICYCLE_BYPASS_TRAFFIC_CALMING_FILTER"
          and !bicycle_bypass
          and !bicycle_bypass:forward
          and !bicycle_bypass:backward
          and bicycle !~ no|private
          and access !~ private|no
    """.toElementFilterExpression() }

    private val bicycleAccessibleRoadFilter by lazy { """
        ways with
          highway ~ ${ALL_ROADS.joinToString("|")}
          and highway != construction
          and area != yes
          and bicycle !~ no|private
          and access !~ private|no
    """.toElementFilterExpression() }

    override val changesetComment = "Specify whether bicycles can bypass traffic calming"
    override val wikiLink = "Key:bicycle_bypass"
    override val icon = Res.drawable.quest_bicycle_bypass
    override val title = Res.string.quest_bicycleBypass_title
    override val hint = Res.string.quest_bicycleBypass_hint
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val achievements = listOf(BICYCLIST, CAR)

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val roadNodeIds = mapData.ways
            .filter { bicycleAccessibleRoadFilter.matches(it) }
            .flatMapTo(HashSet()) { it.nodeIds }

        return mapData.nodes
            .filter { trafficCalmingNodeFilter.matches(it) && it.id in roadNodeIds }
    }

    override fun isApplicableTo(element: Element): Boolean? =
        if (!trafficCalmingNodeFilter.matches(element)) false else null

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter("""
            nodes with traffic_calming ~ "$BICYCLE_BYPASS_TRAFFIC_CALMING_FILTER"
        """)

    @Composable
    override fun Form(
        on: (QuestAction<Boolean>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        YesNoQuestForm(on)
    }

    override fun applyAnswerTo(
        answer: Boolean,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        tags["bicycle_bypass"] = answer.toYesNo()
    }
}

/**
 * traffic_calming values where bicycle_bypass applies (lane narrowings).
 * Regex matches listed values anywhere in a semicolon-separated traffic_calming tag.
 */
internal const val BICYCLE_BYPASS_TRAFFIC_CALMING_FILTER =
    "^(.*;)?(choker|chicane|choked_table|choked_island)(;.*)?$"