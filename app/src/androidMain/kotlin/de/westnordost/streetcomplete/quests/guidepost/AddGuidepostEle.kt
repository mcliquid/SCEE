package de.westnordost.streetcomplete.quests.guidepost

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.OUTDOORS
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_guidepost_disabled_msg
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression

class AddGuidepostEle : OsmElementQuestType<GuidepostEleAnswer>, AndroidQuest {

    private val filter by lazy {
        """
        nodes with
        (information = guidepost or guidepost) and guidepost != simple
        and !ele and !~"ele:.*"
        and (hiking = yes or bicycle=yes)
    """.trimIndent().toElementFilterExpression()
    }

    override val changesetComment = "Specify guidepost elevation"
    override val wikiLink = "Tag:information=guidepost"
    override val icon = R.drawable.ic_quest_guidepost_ele
    override val isDeleteElementEnabled = true
    override val achievements = listOf(OUTDOORS)

    override fun getTitle(tags: Map<String, String>) = R.string.quest_guidepostEle_title

    override fun getHighlightedElements(
        element: Element,
        getMapData: () -> MapDataWithGeometry
    ) = getMapData().filter(filter)

    override val highlightedElementsRadius: Double get() = 200.0
    override val defaultDisabledMessage = Res.string.quest_guidepost_disabled_msg

    override fun createForm() = AddGuidepostEleForm()

    override fun getApplicableElements(
        mapData: MapDataWithGeometry
    ): Iterable<Element> =
        mapData.filter(filter).asIterable()

    override fun isApplicableTo(element: Element): Boolean =
        filter.matches(element)

    override fun applyAnswerTo(answer: GuidepostEleAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        when (answer) {
            is NoVisibleGuidepostEle -> tags["ele:signed"] = "no"
            is GuidepostEle ->          tags["ele"] = answer.ele
        }
    }
}
