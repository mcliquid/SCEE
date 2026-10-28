package de.westnordost.streetcomplete.quests.bike_parking_capacity

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
import de.westnordost.streetcomplete.quests.bike_parking.SURVEYABLE_BICYCLE_PARKING_ACCESS
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.CountInputQuestForm
import org.jetbrains.compose.resources.painterResource

class AddBikeParkingCargoCapacity : OsmFilterQuestType<Int>() {

    override val elementFilter = """
        nodes, ways with
          amenity = bicycle_parking
          and $SURVEYABLE_BICYCLE_PARKING_ACCESS
          and bicycle_parking !~ floor|informal
          and !capacity:cargo_bike
    """

    override val changesetComment = "Specify cargo bicycle parking capacities"
    override val wikiLink = "Key:capacity:cargo_bike"
    override val icon = Res.drawable.quest_bicycle_parking_cargo_capacity
    override val title = Res.string.quest_bikeParkingCargoCapacity_title
    override val hint = Res.string.quest_bikeParkingCargoCapacity_hint
    override val achievements = listOf(BICYCLIST)

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter("nodes, ways with amenity = bicycle_parking")

    @Composable
    override fun Form(on: (QuestAction<Int>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        CountInputQuestForm(
            on = on,
            icon = painterResource(Res.drawable.count_bicycle),
            minimum = 0,
        )
    }

    override fun applyAnswerTo(answer: Int, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["capacity:cargo_bike"] = answer.toString()
    }
}
