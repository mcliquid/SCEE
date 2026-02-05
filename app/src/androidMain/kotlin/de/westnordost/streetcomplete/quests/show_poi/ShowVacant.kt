package de.westnordost.streetcomplete.quests.show_poi

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.applyReplacePlaceTo
import de.westnordost.streetcomplete.osm.isPlace
import de.westnordost.streetcomplete.osm.updateCheckDate
import de.westnordost.streetcomplete.quests.getLabelSources
import de.westnordost.streetcomplete.quests.shop_type.IsShopVacant
import de.westnordost.streetcomplete.quests.shop_type.ShopType
import de.westnordost.streetcomplete.quests.shop_type.ShopTypeAnswer
import de.westnordost.streetcomplete.quests.shop_type.ShopTypeForm
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.quests.LabelOrElementSelectionDialog
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.default_disabled_msg_poi_vacant

class ShowVacant : OsmFilterQuestType<ShopTypeAnswer>(), AndroidQuest {
    override val elementFilter = """
        nodes, ways, relations with
        shop = vacant
        or disused:shop
        or disused:amenity
        or disused:office
    """
    override val changesetComment = "Adjust vacant places"
    override val wikiLink = "Key:disused:"
    override val icon = R.drawable.ic_quest_poi_vacant
    override val dotColor = "grey"
    override val defaultDisabledMessage = Res.string.default_disabled_msg_poi_vacant
    override val dotLabelSources = getLabelSources("label", this, prefs)

    override fun getTitle(tags: Map<String, String>) =
        R.string.quest_poi_vacant_title

    override fun createForm() = ShopTypeForm()

    override fun getHighlightedElements(element: Element, getMapData: () -> MapDataWithGeometry) =
        getMapData().asSequence().filter { it.isPlace() }

    override fun applyAnswerTo(answer: ShopTypeAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        when (answer) {
            is IsShopVacant -> tags.updateCheckDate()
            is ShopType -> answer.feature.applyReplacePlaceTo(tags)
        }
    }

    @Composable
    override fun QuestSettings(onDismissRequest: () -> Unit) {
        LabelOrElementSelectionDialog(this, prefs, onDismissRequest)
    }
}
