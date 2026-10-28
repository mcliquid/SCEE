package de.westnordost.streetcomplete.quests.cycleway_separation

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.cycleway_separate.CYCLEWAY_SEPARATION_BASE_FILTER
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationAnswer
import de.westnordost.streetcomplete.osm.cycleway_separate.applyCyclewaySeparationAnswer
import de.westnordost.streetcomplete.osm.cycleway_separate.isCyclewaySeparationEligible
import de.westnordost.streetcomplete.resources.*

/** Surveys physical separation on separately mapped cycleways (`separation:left/right`). */
class AddCyclewaySeparation : OsmElementQuestType<CyclewaySeparationAnswer> {

    private val filter by lazy { CYCLEWAY_SEPARATION_BASE_FILTER.toElementFilterExpression() }

    override val changesetComment = "Specify physical separation of cycleways"
    override val wikiLink = "Proposed features/cycleway:separation"
    override val icon = Res.drawable.quest_cycleway_separation
    override val title = Res.string.quest_cycleway_separation_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val hint = Res.string.quest_cycleway_separation_hint

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.ways.filter { isApplicableTo(it) }

    override fun isApplicableTo(element: Element): Boolean =
        filter.matches(element) && isCyclewaySeparationEligible(element.tags)

    @Composable
    override fun Form(
        on: (QuestAction<CyclewaySeparationAnswer>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        AddCyclewaySeparationForm(on, element, geometry, countryInfo)
    }

    override fun applyAnswerTo(
        answer: CyclewaySeparationAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long,
    ) {
        applyCyclewaySeparationAnswer(tags, answer)
    }
}
