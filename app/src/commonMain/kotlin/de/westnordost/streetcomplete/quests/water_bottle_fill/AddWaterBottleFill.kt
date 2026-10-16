package de.westnordost.streetcomplete.quests.water_bottle_fill

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CITIZEN
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.OUTDOORS
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.YesNoQuestForm
import de.westnordost.streetcomplete.util.ktx.toYesNo

class AddWaterBottleFill : OsmFilterQuestType<Boolean>() {

    override val elementFilter = """
        nodes, ways with
          (
            amenity ~ drinking_water|water_point
            or man_made ~ water_tap|drinking_fountain
          )
          and access !~ no|private
          and !bottle
          and fountain != bottle_refill
    """
    override val changesetComment = "Survey whether water bottles can be filled here"
    override val wikiLink = "Key:bottle"
    override val icon = Res.drawable.quest_water_bottle_fill
    override val title = Res.string.quest_waterBottleFill_title
    override val achievements = listOf(CITIZEN, OUTDOORS)

    @Composable
    override fun Form(on: (QuestAction<Boolean>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        YesNoQuestForm(on)
    }

    override fun applyAnswerTo(answer: Boolean, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["bottle"] = answer.toYesNo()
    }
}
