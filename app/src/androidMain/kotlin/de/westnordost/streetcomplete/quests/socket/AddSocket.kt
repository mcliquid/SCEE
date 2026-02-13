package de.westnordost.streetcomplete.quests.socket

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

class AddSocket : OsmElementQuestType<Set<SocketType>>, AndroidQuest {

    private val filter by lazy {
        """
        nodes, ways with
          amenity = charging_station
          and bicycle != yes
          and motorcar != no
        """.toElementFilterExpression()
    }

    override val enabledInCountries = AllCountriesExcept(
        "AT","BE","BG","HR","CY","CZ","DE","DK","EE","ES","FI","FR","GB",
        "GR","HU","IE","IS","IT","LI","LT","LU","LV","MT","NL","NO",
        "PL","PT","RO","SE","SI","SK"
    )

    override val changesetComment = "Add charging station sockets"
    override val wikiLink = "Key:socket"
    override val icon = R.drawable.ic_quest_socket
    override val achievements = listOf(EditTypeAchievement.RARE)

    override fun getTitle(tags: Map<String, String>): Int = R.string.quest_socket_title

    override fun createForm() = AddSocketForm()

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.filter { element: Element ->
            isApplicableTo(element)
        }

    override fun isApplicableTo(element: Element): Boolean {
        if (!filter.matches(element)) return false

        val supportedSockets = supportedSocketTypes(element)

        // skip if any supported socket already exists
        if (supportedSockets.any { socketType ->
                element.tags.containsKey("socket:${socketType.osmKey}")
            }) {
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

    /**
     * Returns the list of socket types that should be shown for this element.
     *
     * Currently only car-relevant sockets are supported.
     * Future versions may extend this depending on bicycle=yes / motorcar=yes.
     */
    private fun supportedSocketTypes(element: Element): List<SocketType> {
        return SocketType.selectableValues
    }
}
