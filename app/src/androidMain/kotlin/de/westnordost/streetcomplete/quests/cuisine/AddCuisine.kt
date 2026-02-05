package de.westnordost.streetcomplete.quests.cuisine

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.isPlace
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.default_disabled_msg_go_inside

class AddCuisine : OsmFilterQuestType<String>(), AndroidQuest {

    override val elementFilter = """
        nodes, ways with
        (
          amenity ~ restaurant|fast_food
          or (amenity = pub and food = yes)
        )
        and !cuisine
    """
    override val changesetComment = "Add cuisine"
    override val wikiLink = "Key:cuisine"
    override val icon = R.drawable.quest_restaurant
    override val isReplacePlaceEnabled = true
    override val defaultDisabledMessage = Res.string.default_disabled_msg_go_inside

    override fun getTitle(tags: Map<String, String>) = R.string.quest_cuisine_title

    override fun getHighlightedElements(element: Element, getMapData: () -> MapDataWithGeometry) =
        getMapData().asSequence().filter { it.isPlace() }

    override fun createForm() = AddCuisineForm()

    override fun applyAnswerTo(answer: String, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["cuisine"] = answer
    }
}
