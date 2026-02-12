package de.westnordost.streetcomplete.quests.socket_type

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.data.quest.AllCountriesExcept
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement
import de.westnordost.streetcomplete.osm.Tags

class AddChargingStationSockets : OsmElementQuestType<Set<SocketType>>, AndroidQuest {

    private val filter by lazy {
        "nodes, ways with amenity = charging_station"
            .toElementFilterExpression()
    }

    override val enabledInCountries = AllCountriesExcept(
        "AT","BE","BG","HR","CY","CZ","DE","DK","EE","ES","FI","FR","GB",
        "GR","HU","IE","IS","IT","LI","LT","LU","LV","MT","NL","NO",
        "PL","PT","RO","SE","SI","SK"
    )

    override val changesetComment = "Add charging station sockets"
    override val wikiLink = "Key:socket"
    override val icon = R.drawable.ic_quest_charging_station
    override val achievements = listOf(EditTypeAchievement.RARE)

    override fun getTitle(tags: Map<String, String>): Int =
        R.string.quest_charging_station_sockets_title

    override fun createForm() = AddChargingStationSocketsForm()

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        // explicit type to avoid platform-nullability inference warnings
        val candidates: Iterable<Element> = mapData.filter { element: Element ->
            filter.matches(element)
        }

        return candidates
            .filter { element: Element ->
                // show only if no supported socket tag exists
                SocketType.selectableValues.none { socketType ->
                    element.tags.containsKey("socket:${socketType.osmKey}")
                }
            }
            .asIterable()
    }

    override fun isApplicableTo(element: Element): Boolean {
        if (!filter.matches(element)) return false

        // skip if any socket:* tag already exists
        if (element.tags.keys.any { key -> key.startsWith("socket:") }) {
            return false
        }

        return true
    }

    override fun applyAnswerTo(
        answer: Set<SocketType>,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long
    ) {
        answer.forEach { socketType ->
            tags["socket:${socketType.osmKey}"] = "1"
        }
    }
}
