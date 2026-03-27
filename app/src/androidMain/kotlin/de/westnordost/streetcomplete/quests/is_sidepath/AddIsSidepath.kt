package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.AndroidQuest
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.quests.FullElementSelectionDialog
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.*

class AddIsSidepath : OsmElementQuestType<IsSidepathAnswer>, AndroidQuest {

    private val elementFilter = """
        ways with
          (
            (
              highway = cycleway
              and cycleway !~ sidewalk|link|sidepath|crossing
            )
            or
            (
              highway = footway
              and footway !~ sidewalk|link|crossing
            )
            or
            (
              highway = path
              and (bicycle or foot)
              and path !~ sidewalk|link|crossing|sidepath
            )
          )
          and !is_sidepath
          and area != yes
          and access !~ no|private
    """.trimIndent()

    private val filter by lazy {
        prefs
            .getString(getPrefixedFullElementSelectionPref(prefs), elementFilter)
            .toElementFilterExpression()
    }

    override val changesetComment = "Specify whether a path is a sidepath of a road"
    override val wikiLink = "Key:is_sidepath"
    override val icon = R.drawable.ic_quest_poi_bicycle
    override val hint = Res.string.quest_is_sidepath_hint
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val title = Res.string.quest_is_sidepath_title
    override val hasQuestSettings = true

    @Composable
    override fun QuestSettings(onDismissRequest: () -> Unit) {
        FullElementSelectionDialog(
            prefs,
            getPrefixedFullElementSelectionPref(prefs),
            R.string.quest_settings_element_selection,
            elementFilter,
            onDismissRequest
        )
    }

    override fun getApplicableElements(
        mapData: MapDataWithGeometry
    ): Iterable<Element> =
        mapData.ways.filter { filter.matches(it) }

    override fun isApplicableTo(element: Element): Boolean? =
        if (filter.matches(element)) null else false

    override fun createForm() = AddIsSidepathForm()

    override fun applyAnswerTo(
        answer: IsSidepathAnswer,
        tags: Tags,
        geometry: ElementGeometry,
        timestampEdited: Long
    ) {
        when (answer) {
            IsSidepathAnswer.Yes ->
                tags["is_sidepath"] = "yes"

            IsSidepathAnswer.No ->
                tags["is_sidepath"] = "no"

            IsSidepathAnswer.IsSidewalk ->
                tags["footway"] = "sidewalk"

            IsSidepathAnswer.IsCrossing -> {
                when (tags["highway"]) {
                    "cycleway" -> tags["cycleway"] = "crossing"
                    "footway" -> tags["footway"] = "crossing"
                    "path" -> tags["path"] = "crossing"
                }
            }
        }
    }
}
