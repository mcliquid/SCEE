package de.westnordost.streetcomplete.quests.smoothness

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.osm.FILTER_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.quests.FullElementSelectionDialog
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import de.westnordost.streetcomplete.quests.getLabelSources
import de.westnordost.streetcomplete.util.ktx.systemTimeNow
import de.westnordost.streetcomplete.util.ktx.toLocalDate

/** Survey explicit cycleway sides or the parts of a segregated combined foot/cycle way. */
class AddCyclewayPartSmoothness : OsmElementQuestType<SmoothnessAnswer> {

    val elementFilter = """
        ways with
          (cycleway:left ~ track|lane or cycleway:right ~ track|lane or cycleway:both ~ track|lane
            or (highway ~ path|footway|cycleway|bridleway and segregated = yes))
          and ($FILTER_BICYCLE_ACCESSIBLE)
          and area != yes
          and (!conveying or conveying = no)
          and (!indoor or indoor = no)
    """
    override val changesetComment = "Specify cycleway and footway part smoothness"
    override val wikiLink = "Key:smoothness"
    override val icon = Res.drawable.quest_way_surface_detail
    override val title = Res.string.quest_partSmoothness_title
    override val achievements = listOf(BICYCLIST)
    override val defaultDisabledMessage = Res.string.default_disabled_msg_difficult_and_time_consuming
    override val hint = Res.string.quest_smoothness_hint
    override val previousAnswerKeys = listOf("cycleway:smoothness", "footway:smoothness",
        "cycleway:left:smoothness", "cycleway:right:smoothness", BOTH_SMOOTHNESS_KEY)

    private val filter get() = prefs.getString(getPrefixedFullElementSelectionPref(prefs), elementFilter)
        .toElementFilterExpression()

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val selection = filter
        val today = systemTimeNow().toLocalDate()
        return mapData.ways.filter { selection.matches(it) &&
            resolveSmoothnessParts(it.tags, it.timestampEdited, today).any { part -> part.needsSurvey } }
    }

    override fun isApplicableTo(element: Element): Boolean = element is Way && filter.matches(element) &&
        resolveSmoothnessParts(element.tags, element.timestampEdited).any { it.needsSurvey }

    override val hasQuestSettings = true
    override val dotLabelSources by lazy { getLabelSources(super.dotLabelSources.joinToString(", "), this, prefs) }

    @Composable override fun QuestSettings(onDismissRequest: () -> Unit) {
        FullElementSelectionDialog(prefs, getPrefixedFullElementSelectionPref(prefs),
            Res.string.quest_settings_element_selection, elementFilter, onDismissRequest)
    }

    @Composable
    override fun Form(on: (QuestAction<SmoothnessAnswer>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        AddPartSmoothnessForm(on, element, geometry, countryInfo)
    }

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry): Sequence<Element> {
        val nodes = (element as Way).nodeIds
        return mapData.nodes.asSequence().filter { it.id in nodes && barrierFilter.matches(it) }
    }

    private val barrierFilter by lazy {
        "nodes with barrier or traffic_calming or (kerb and kerb !~ no|flush)".toElementFilterExpression()
    }

    override fun applyAnswerTo(answer: SmoothnessAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        when (answer) {
            is PartSmoothnessAnswer -> answer.applyToParts(tags, timestampEdited)
            // Preserve the previous answer contract. Persisted edits themselves contain tag diffs.
            is SmoothnessValueAnswer, WrongSurfaceAnswer -> answer.applyTo(tags, prefix = CYCLEWAY_PART_PREFIX)
            IsActuallyStepsAnswer -> throw IllegalStateException()
        }
    }
}

const val CYCLEWAY_PART_PREFIX = "cycleway"
const val CYCLEWAY_PART_SURFACE_KEY = "cycleway:surface"
