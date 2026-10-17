package de.westnordost.streetcomplete.quests.sidepath_usage

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.cycleway.CyclewayAndDirection
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath
import de.westnordost.streetcomplete.osm.cycleway.SIDEPATH_USAGE_COUNTRIES
import de.westnordost.streetcomplete.osm.cycleway.applyRoadSidepath
import de.westnordost.streetcomplete.osm.cycleway.applyTo
import de.westnordost.streetcomplete.osm.cycleway.getSidepathAnswers
import de.westnordost.streetcomplete.osm.cycleway.hasSidepathTagging
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept

/** Answer to [AddSidepathUsage] */
sealed interface SidepathUsageAnswer {
    /** It is compulsory to use the cycleway(s) in the given direction(s) */
    data class Compulsory(val where: RoadSidepath) : SidepathUsageAnswer
    /** There is no separate cycleway (anymore): the cycleway tagging has been corrected with the
     *  same form as in AddCycleway */
    data class CorrectedCycleway(val sides: Sides<CyclewayAndDirection>) : SidepathUsageAnswer
}

/** Asks whether it is compulsory to use the separately mapped cycleway (cycleway:*=separate) of
 *  the road, i.e. whether the road is to be tagged with bicycle(:forward|backward)=use_sidepath.
 *
 *  The question is about the road as a whole and the direction of the OSM way, not about
 *  individual cycleways and their sides or directions, see Sidepath.kt.
 *
 *  "No / I can't tell" does not change any tags, the quest is hidden with the standard hide
 *  function instead, which is persisted in the database of hidden quests. */
class AddSidepathUsage : OsmElementQuestType<SidepathUsageAnswer> {

    override val changesetComment = "Specify whether separately mapped cycleways are compulsory"
    override val wikiLink = "Tag:bicycle=use_sidepath"
    override val icon = Res.drawable.quest_bicycleway
    override val title = Res.string.quest_sidepath_usage_title
    override val achievements = listOf(BICYCLIST)
    override val enabledInCountries = NoCountriesExcept(*SIDEPATH_USAGE_COUNTRIES.toTypedArray())
    /* The answer depends on signs at the cycleways, which may have to be searched for, and the
       choice needs some knowledge of the local traffic laws */
    override val defaultDisabledMessage = Res.string.default_disabled_msg_difficult_and_time_consuming
    override val previousAnswerKeys: List<String> = emptyList()
    override val hint = Res.string.cycleway_sidepath_hint

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.ways.filter { isSidepathUsageApplicable(it) }

    override fun isApplicableTo(element: Element): Boolean =
        isSidepathUsageApplicable(element)

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter("""
            ways with
              (
                highway ~ cycleway|path
                or highway ~ footway|bridleway and bicycle ~ yes|designated
              )
              and bicycle !~ no|private
              and access !~ no|private
        """)

    @Composable
    override fun Form(on: (QuestAction<SidepathUsageAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddSidepathUsageForm(on, element, geometry, countryInfo)
    }

    override fun applyAnswerTo(answer: SidepathUsageAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        // the country is restricted by enabledInCountries: DE and NL drive on the right
        when (answer) {
            is SidepathUsageAnswer.Compulsory -> tags.applyRoadSidepath(answer.where, false)
            // same creator as AddCycleway, which also takes care of use_sidepath
            is SidepathUsageAnswer.CorrectedCycleway -> answer.sides.applyTo(tags, false)
        }
    }
}

private val EXPLICIT_SEPARATE_KEYS = listOf("cycleway", "cycleway:left", "cycleway:right", "cycleway:both")

/** A way with a separate cycleway explicitly tagged (so, not just implied by bicycle=use_sidepath),
 *  without use_sidepath or optional_sidepath, for which an answer may be written. */
internal fun isSidepathUsageApplicable(element: Element): Boolean =
    element.type == ElementType.WAY &&
    EXPLICIT_SEPARATE_KEYS.any { element.tags[it] == "separate" } &&
    !hasSidepathTagging(element.tags) &&
    getSidepathAnswers(element.tags, isLeftHandTraffic = false).isNotEmpty()
