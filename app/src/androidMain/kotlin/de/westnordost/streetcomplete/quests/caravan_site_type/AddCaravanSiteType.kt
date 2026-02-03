package de.westnordost.streetcomplete.quests.caravan_site_type

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.OUTDOORS
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.default_disabled_msg_caravanSiteType

class AddCaravanSiteType : OsmFilterQuestType<String>(), AndroidQuest {

    override val elementFilter = """
        nodes, ways, relations with
            tourism = caravan_site and (
            !caravan_site:type
        )
    """
    override val changesetComment = "Add caravan site type info"
    override val defaultDisabledMessage = Res.string.default_disabled_msg_caravanSiteType
    override val wikiLink = "Key:caravan_site:type"
    override val icon = R.drawable.ic_quest_caravan_site
    override val achievements = listOf(OUTDOORS)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_caravanSiteType_title

    override fun getHighlightedElements(element: Element, getMapData: () -> MapDataWithGeometry) =
        getMapData().filter("nodes, ways, relations with tourism ~ caravan_site|camp_site")

    override fun createForm() = AddCaravanSiteTypeForm()

    override fun applyAnswerTo(answer: String, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["caravan_site:type"] = answer
    }
}
