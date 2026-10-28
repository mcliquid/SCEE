package de.westnordost.streetcomplete.quests.width

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.FILTER_FOOT_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker

class AddFootwayWidth(
    private val checkArSupport: ArSupportChecker
) : OsmFilterQuestType<WidthAnswer>() {

    /* Exclusive footways, or ways that are cycleway + footway (or bridleway) but segregated.
     *  Also a footway with bicycle=yes that is not segregated: one shared surface, so width. */
    override val elementFilter = """
        ways with (
          (
            highway = footway
            and footway !~ link|crossing
            and bicycle !~ yes|designated
            and (!width or source:width ~ ".*estimat.*")
          ) or (
            segregated = yes
            and (
              (
                highway = footway
                and bicycle ~ yes|designated
                and (!footway:width or source:footway:width ~ ".*estimat.*")
              ) or (
                highway = footway
                and bicycle !~ yes|designated
                and (!width or source:width ~ ".*estimat.*")
              ) or (
                highway = cycleway and foot ~ yes|designated
                and (!footway:width or source:footway:width ~ ".*estimat.*")
              ) or (
                highway = path
                and bicycle != no
                and (!footway:width or source:footway:width ~ ".*estimat.*")
              ) or (
                highway = bridleway
                and bicycle ~ designated|yes
                and (!footway:width or source:footway:width ~ ".*estimat.*")
              )
            )
          ) or (
            highway = footway
            and footway !~ link|crossing
            and bicycle = yes
            and segregated != yes
            and (!width or source:width ~ ".*estimat.*")
          )
        )
        and area != yes
        and ($FILTER_FOOT_ACCESSIBLE)
        and placement != transition
        and ~path|footway|cycleway|bridleway !~ link
    """
    override val changesetComment = "Specify footway width"
    override val wikiLink = "Key:width"
    override val icon = Res.drawable.ic_quest_footway_width
    override val title = Res.string.quest_footway_width_title
    override val achievements = listOf(PEDESTRIAN)
    override val defaultDisabledMessage
        get() = if (!checkArSupport()) Res.string.default_disabled_msg_no_ar else Res.string.default_disabled_msg_ee

    @Composable
    override fun Form(on: (QuestAction<WidthAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddWidthForm(on, element, countryInfo)
    }

    override fun applyAnswerTo(answer: WidthAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        val key = footwayWidthTargetKey(tags)

        tags[key] = answer.width.toOsmValue()
        if (answer.isARMeasurement) {
            tags["source:$key"] = "ARCore"
        } else {
            tags.remove("source:$key")
        }
    }
}
