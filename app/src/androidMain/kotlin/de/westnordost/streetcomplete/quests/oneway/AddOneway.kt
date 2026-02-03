package de.westnordost.streetcomplete.quests.oneway

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.estimateUsableRoadwayWidth
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.BACKWARD
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.FORWARD
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer.NO_ONEWAY

class AddOneway : OsmElementQuestType<OnewayAnswer>, AndroidQuest {

    /** find all roads */
    private val allRoadsFilter by lazy { """
    ways with
      (
        highway ~ ${ALL_ROADS.joinToString("|")}
        or highway = cycleway
        or (highway = path and bicycle ~ yes|designated)
        or (highway = footway and bicycle ~ yes|designated)
      )
      and area != yes
""".toElementFilterExpression() }

    /** find only those roads eligible for asking for oneway */
    private val elementFilter by lazy { """
    ways with
      (
        (highway ~ living_street|residential|service|tertiary|unclassified|busway
         and width <= 4 and (!lanes or lanes <= 1))
        or
        (highway = cycleway)
        or
        (highway = footway and bicycle ~ yes|designated)
        or
        (highway = path and bicycle ~ yes|designated)
      )
      and !oneway
      and area != yes
      and junction != roundabout
      and (access !~ private|no or (foot and foot !~ private|no))
""".toElementFilterExpression() }

    override val changesetComment = "Specify whether roads are one-ways"
    override val wikiLink = "Key:oneway"
    override val icon = R.drawable.quest_oneway
    override val hasMarkersAtEnds = true
    override val achievements = listOf(CAR)

    override val hint = R.string.quest_arrow_tutorial

    override fun getTitle(tags: Map<String, String>) = R.string.quest_oneway2_title

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val allRoads = mapData.ways.filter { allRoadsFilter.matches(it) && it.nodeIds.size >= 2 }
        val connectionCountByNodeIds = mutableMapOf<Long, Int>()
        val onewayCandidates = mutableListOf<Way>()

        for (road in allRoads) {
            for (nodeId in road.nodeIds) {
                val prevCount = connectionCountByNodeIds[nodeId] ?: 0
                connectionCountByNodeIds[nodeId] = prevCount + 1
            }
            if (isOnewayRoadCandidate(road)) {
                onewayCandidates.add(road)
            }
        }

        return onewayCandidates.filter { way ->
            val firstConnected = (connectionCountByNodeIds[way.nodeIds.first()] ?: 0) > 1
            val lastConnected  = (connectionCountByNodeIds[way.nodeIds.last()]  ?: 0) > 1
            if (isBikeFacility(way.tags)) {
                // Für cycleway und path+bicycle: mindestens ein Ende verbunden
                firstConnected || lastConnected
            } else {
                // Für die restlichen (engen Kfz-Straßen): beide Enden verbunden
                firstConnected && lastConnected
            }
        }
    }


    override fun isApplicableTo(element: Element): Boolean? {
        if (!isOnewayRoadCandidate(element)) return false
        /* return null because oneway candidate roads must also be connected on both ends with other
           roads for which we'd need to look at surrounding geometry */
        return null
    }

    private fun isYesOrDesignated(v: String?) = v == "yes" || v == "designated"

    private fun isBikeFacility(tags: Map<String, String>): Boolean {
        return when (tags["highway"]) {
            "cycleway" -> true
            "path" -> isYesOrDesignated(tags["bicycle"]) || isYesOrDesignated(tags["footway"])
            "footway" -> isYesOrDesignated(tags["bicycle"]) // für footway + bicycle=yes|designated
            else -> false
        }
    }

    private fun isOnewayRoadCandidate(road: Element): Boolean {
        if (!elementFilter.matches(road)) return false

        if (isBikeFacility(road.tags)) {
            // kein Breitenlimit bei Rad-Infrastruktur
            return true
        }

        val usableWidth = estimateUsableRoadwayWidth(road.tags) ?: return false
        return usableWidth <= 4f
    }


    override fun createForm() = AddOnewayForm()

    override fun applyAnswerTo(answer: OnewayAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["oneway"] = when (answer) {
            FORWARD -> "yes"
            BACKWARD -> "-1"
            NO_ONEWAY -> "no"
        }
    }
}
