package de.westnordost.streetcomplete.quests.oneway

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.BACKWARD
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.FORWARD
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.NO_ONEWAY

class AddOnewayBicycle :
    OsmElementQuestType<OnewayAnswer>,
    AndroidQuest {

    /** all bicycle-relevant ways, used to calculate connectivity */
    private val allBikeWaysFilter by lazy { """
        ways with
          (
            highway = cycleway
            or (highway = path and bicycle ~ yes|designated)
            or (highway = footway and bicycle ~ yes|designated)
          )
          and area != yes
    """.toElementFilterExpression() }

    /** only those bike ways eligible for asking for oneway */
    private val elementFilter by lazy { """
        ways with
          (
            highway = cycleway
            or (highway = path and bicycle ~ yes|designated)
            or (highway = footway and bicycle ~ yes|designated)
          )
          and !oneway
          and area != yes
          and junction != roundabout
          and (access !~ private|no or (foot and foot !~ private|no))
    """.toElementFilterExpression() }

    override val changesetComment = "Specify whether bicycle ways are one-ways"
    override val wikiLink = "Key:oneway"
    override val icon = R.drawable.quest_oneway
    override val hasMarkersAtEnds = true
    override val achievements = listOf(EditTypeAchievement.BICYCLIST)

    override val hint = R.string.quest_arrow_tutorial

    override fun getTitle(tags: Map<String, String>) =
        R.string.quest_oneway2_title

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val bikeWays = mapData.ways.filter {
            allBikeWaysFilter.matches(it) && it.nodeIds.size >= 2
        }

        val connectionCountByNodeIds = mutableMapOf<Long, Int>()
        val onewayCandidates = mutableListOf<Way>()

        for (way in bikeWays) {
            for (nodeId in way.nodeIds) {
                connectionCountByNodeIds[nodeId] =
                    (connectionCountByNodeIds[nodeId] ?: 0) + 1
            }
            if (elementFilter.matches(way)) {
                onewayCandidates.add(way)
            }
        }

        // For bicycle infrastructure, one connected end is sufficient
        return onewayCandidates.filter { way ->
            val firstConnected =
                (connectionCountByNodeIds[way.nodeIds.first()] ?: 0) > 1
            val lastConnected =
                (connectionCountByNodeIds[way.nodeIds.last()] ?: 0) > 1
            firstConnected || lastConnected
        }
    }

    override fun isApplicableTo(element: Element): Boolean? {
        if (!elementFilter.matches(element)) return false
        // Need surrounding geometry to decide connectivity
        return null
    }

    override fun createForm() =
        AddOnewayForm()

    override fun applyAnswerTo(
        answer: OnewayAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long
    ) {
        tags["oneway"] = when (answer) {
            FORWARD -> "yes"
            BACKWARD -> "-1"
            NO_ONEWAY -> "no"
        }
    }
}
