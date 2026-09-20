package de.westnordost.streetcomplete.quests.width

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import org.jetbrains.compose.resources.StringResource

class AddCyclewayWidth(
    private val checkArSupport: ArSupportChecker
) : OsmFilterQuestType<WidthAnswer>() {

    /* Exclusive cycleways, segregated combined foot/cycle ways (cycleway:width), and clearly
     * shared (segregated=no) combined foot/cycle ways (total width). The shared patterns match
     * AddCyclewaySegregation's clearly designated combined ways; missing segregated stays out. */
    override val elementFilter = """
        ways with
          (
            (
              highway = cycleway
              and foot !~ yes|designated
              and (!width or source:width ~ ".*estimat.*")
            ) or (
              segregated = yes
              and (
                highway = cycleway and foot ~ yes|designated
                or highway ~ path|footway and bicycle != no
                or highway = bridleway and bicycle ~ designated|yes
              )
              and (!cycleway:width or source:cycleway:width ~ ".*estimat.*")
            ) or (
              segregated = no
              and (
                (highway = path and bicycle = designated and foot = designated)
                or (highway = footway and bicycle = designated)
                or (highway = cycleway and foot ~ designated|yes)
              )
              and (!width or source:width ~ ".*estimat.*")
            )
          )
          and area != yes
          and access !~ private|no
          and placement != transition
          and ~path|footway|cycleway|bridleway !~ link
    """
    override val changesetComment = "Specify cycleways width"
    override val wikiLink = "Key:width"
    override val icon = Res.drawable.quest_bicycleway_width
    override val title = Res.string.quest_cycleway_width_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage: StringResource?
        get() = if (!checkArSupport()) Res.string.default_disabled_msg_no_ar else null

    @Composable
    override fun Form(on: (QuestAction<WidthAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddWidthForm(on, element, countryInfo)
    }

    override fun applyAnswerTo(answer: WidthAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        // segregated=yes → cycleway portion width; exclusive or shared (segregated=no) → total width
        val key = if (tags["segregated"] == "yes") "cycleway:width" else "width"

        tags[key] = answer.width.toOsmValue()
        if (answer.isARMeasurement) {
            tags["source:$key"] = "ARCore"
        } else {
            tags.remove("source:$key")
        }
    }
}
