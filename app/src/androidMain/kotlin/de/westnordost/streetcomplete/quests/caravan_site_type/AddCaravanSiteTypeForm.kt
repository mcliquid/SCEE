package de.westnordost.streetcomplete.quests.caravan_site_type

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.quests.ARadioGroupQuestForm
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_beach
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_camp_site
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_farm
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_harbour
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_lake
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_museum
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_parking_lot
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_restaurant
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_river
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_supermarket
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_town
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_village
import de.westnordost.streetcomplete.resources.quest_caravanSiteType_winery
import org.jetbrains.compose.resources.stringResource

class AddCaravanSiteTypeForm : ARadioGroupQuestForm<String, String>() {
    override val items = listOf(
        "village",
        "town",
        "river",
        "lake",
        "parking_lot",
        "harbour",
        "winery",
        "camp_site",
        "museum",
        "restaurant",
        "farm",
        "beach",
        "supermarket",
    )

    @Composable override fun BoxScope.ItemContent(item: String) {
        Text(stringResource(when (item) {
            "village" -> Res.string.quest_caravanSiteType_village
            "town" -> Res.string.quest_caravanSiteType_town
            "river" -> Res.string.quest_caravanSiteType_river
            "lake" -> Res.string.quest_caravanSiteType_lake
            "parking_lot" -> Res.string.quest_caravanSiteType_parking_lot
            "harbour" -> Res.string.quest_caravanSiteType_harbour
            "winery" -> Res.string.quest_caravanSiteType_winery
            "camp_site" -> Res.string.quest_caravanSiteType_camp_site
            "museum" -> Res.string.quest_caravanSiteType_museum
            "restaurant" -> Res.string.quest_caravanSiteType_restaurant
            "farm" -> Res.string.quest_caravanSiteType_farm
            "beach" -> Res.string.quest_caravanSiteType_beach
            "supermarket" -> Res.string.quest_caravanSiteType_supermarket
            else -> null
        }!!))
    }
}

