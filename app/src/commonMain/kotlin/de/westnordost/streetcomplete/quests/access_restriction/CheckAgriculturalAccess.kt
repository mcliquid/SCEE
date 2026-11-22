package de.westnordost.streetcomplete.quests.access_restriction

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.Action
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.AnswerItem
import de.westnordost.streetcomplete.ui.common.quest.ItemSelectQuestForm
import org.jetbrains.compose.resources.stringResource
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept

class CheckAgriculturalAccess : OsmFilterQuestType<AgriculturalAccessSign>() {

    /*
        Germany-only. The filter is based on semantic conflicts with the supported answers
        (DE:250 / DE:260 + agricultural/forestry exception), not on highway class as a
        safety mechanism.

        Eligible: ordinary roads where access=agricultural|forestry can be a mistaken
        stand-in for a vehicle- or motor-vehicle restriction. Compatible overrides such as
        foot=yes / bicycle=yes are allowed and preserved (e.g. Alter Kirchweg).

        Excluded highway types are those where the supported prohibition signs are the wrong
        signs, or where agricultural/forestry access can be legitimate:
        - path, footway, cycleway, bridleway, steps, pedestrian: typically signed as
          pedestrian/cycle infrastructure with an agricultural exception, not DE:250/260
        - motorway / motorway_link: different signing regime
        - construction/proposed and other non-road highway values

        Existing traffic_sign=* does not hide the quest: the access correction can still be
        useful. applyTo never overwrites traffic_sign.

        path with access=agricultural may use DE:240 + exception; footway/cycleway remain excluded.

        DE:240 completion (unlike DE:250/260) keeps access=*; hide only when modes, access and a
        matching DE:240+1026 plate are already tagged. Modes without traffic_sign stay eligible.
     */
    override val elementFilter = """
        ways with
          highway
          and highway !~ $agriculturalAccessExcludedHighways
          and access ~ agricultural|forestry|agricultural;forestry|forestry;agricultural
          and area != yes
          and motorroad != yes
          and bicycle_road != yes
          and cyclestreet != yes
          and !barrier
          and !vehicle and !motor_vehicle
          and !~"(access|vehicle|motor_vehicle|bicycle):.*"
          and !(access = agricultural and foot ~ designated and bicycle ~ designated and segregated = no and traffic_sign ~ "DE:240,1026-36|DE:240,DE:1026-36")
          and !(access = forestry and foot ~ designated and bicycle ~ designated and segregated = no and traffic_sign ~ "DE:240,1026-37|DE:240,DE:1026-37")
          and !(access ~ agricultural;forestry|forestry;agricultural and foot ~ designated and bicycle ~ designated and segregated = no and traffic_sign ~ "DE:240,1026-38|DE:240,DE:1026-38")
    """
    override val changesetComment = "Specify more precise agricultural and forestry access restrictions"
    override val wikiLink = "Key:access"
    override val icon = Res.drawable.quest_access_restriction
    override val title = Res.string.quest_accessRestriction_title
    override val hint = Res.string.quest_accessRestriction_hint
    override val achievements = listOf(CAR, PEDESTRIAN)
    override val hasMarkersAtEnds = true
    override val enabledInCountries = NoCountriesExcept("DE")

    @Composable
    override fun Form(
        on: (QuestAction<AgriculturalAccessSign>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        val accessValue = element.tags["access"].orEmpty()
        ItemSelectQuestForm(
            on = on,
            items = AgriculturalAccessSign.entries.filter { it.canApplyTo(element.tags) },
            itemsPerRow = 2,
            otherAnswers = { listOf(
                AnswerItem(stringResource(Res.string.quest_accessRestriction_other)) { on(Action.CantSay) }
            ) },
            itemContent = { AgriculturalAccessSignGraphic(it, accessValue) },
        )
    }

    override fun applyAnswerTo(
        answer: AgriculturalAccessSign,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        answer.applyTo(tags)
    }
}
