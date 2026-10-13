package de.westnordost.streetcomplete.quests.bicycle_in_pedestrian_street

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Relation
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street.BicycleInPedestrianStreet.*
import de.westnordost.streetcomplete.resources.*

class AddBicycleInPedestrianStreet : OsmElementQuestType<BicycleInPedestrianStreetAnswer> {
    private val filter = """
        ways, relations with
          highway = pedestrian
          and !bicycle and !bicycle:conditional and !bicycle:signed
    """.toElementFilterExpression()

    override val changesetComment = "Specify signed bicycle access in pedestrian streets"
    override val wikiLink = "Key:bicycle"
    override val icon = Res.drawable.quest_bicycle_in_pedestrian_street
    override val title = Res.string.quest_bicycleInPedestrianStreet_title
    override val hint = Res.string.quest_bicycleInPedestrianStreet_hint
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val achievements = listOf(EditTypeAchievement.BICYCLIST)

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        // A tagged multipolygon represents the area; do not ask again on its boundary ways,
        // including when the bicycle rule has already been recorded on the relation.
        val boundaryWays = mapData.relations.asSequence()
            .filter { it.tags["type"] == "multipolygon" && it.tags["highway"] == "pedestrian" }
            .flatMap { it.members.asSequence() }
            .filter { it.type == ElementType.WAY }
            .map { it.ref }.toSet()
        return mapData.filter(filter)
            .filter { it !is Relation || it.tags["type"] == "multipolygon" }
            .filterNot { it is Way && it.id in boundaryWays }.asIterable()
    }

    override fun isApplicableTo(element: Element): Boolean? = when {
        !filter.matches(element) -> false
        element is Relation && element.tags["type"] != "multipolygon" -> false
        // Relation membership requires the surrounding map data on updates too.
        element is Way -> null
        else -> true
    }

    @Composable
    override fun Form(on: (QuestAction<BicycleInPedestrianStreetAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddBicycleInPedestrianStreetForm(on, countryInfo)
    }

    override fun applyAnswerTo(answer: BicycleInPedestrianStreetAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        // Preserve bicycle information added since the quest was opened.
        if (tags["bicycle"] != null || tags["bicycle:conditional"] != null || tags["bicycle:signed"] != null) return
        when (answer) {
            is BicycleInPedestrianStreetAnswer.Sign -> when (answer.value) {
                ALLOWED -> tags["bicycle"] = "yes"
                NOT_ALLOWED -> tags["bicycle"] = "no"
                NOT_SIGNED -> tags["bicycle:signed"] = "no"
                DESIGNATED -> error("Not an answer offered by this quest")
            }
            is BicycleInPedestrianStreetAnswer.Conditional -> tags["bicycle:conditional"] = answer.value
        }
    }
}
