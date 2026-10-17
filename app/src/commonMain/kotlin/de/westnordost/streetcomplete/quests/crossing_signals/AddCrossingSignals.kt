package de.westnordost.streetcomplete.quests.crossing_signals

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.isCrossing
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.ui.common.quest.YesNoQuestForm
import de.westnordost.streetcomplete.util.ktx.toYesNo

class AddCrossingSignals : OsmElementQuestType<Boolean> {

    private val crossingFilter by lazy { """
        nodes with
          highway = crossing
          and foot != no
          and !crossing:signals
          and (!crossing or crossing = island)
    """.toElementFilterExpression() }
    /* only looking for crossings that have no crossing=* at all set because if the crossing had
     * signals, it would have been tagged with crossing=traffic_signals */

    private val excludedWaysFilter by lazy { """
        ways with
          highway and access ~ private|no
          or highway = service
    """.toElementFilterExpression() }

    override val changesetComment = "Specify whether pedestrian crossings have traffic signals"
    override val wikiLink = "Key:crossing:signals"
    override val icon = Res.drawable.quest_traffic_lights
    override val title = Res.string.quest_pedestrian_crossing_signals2
    override val achievements = listOf(PEDESTRIAN)

    override fun getHighlightedElements(element: Element, mapData: MapDataWithGeometry) =
        mapData.filter { it.isCrossing() }.asSequence()

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> {
        val excludedWayNodeIds = mapData.ways
            .filter { excludedWaysFilter.matches(it) }
            .flatMapTo(HashSet()) { it.nodeIds }

        return mapData.nodes
            .filter { crossingFilter.matches(it) && it.id !in excludedWayNodeIds }
    }

    override fun isApplicableTo(element: Element): Boolean? =
        if (!crossingFilter.matches(element)) false else null

    @Composable
    override fun Form(on: (QuestAction<Boolean>) -> Unit, element: Element, geometry: ElementGeometry, countryInfo: CountryInfo) {
        YesNoQuestForm(on)
    }

    override fun applyAnswerTo(answer: Boolean, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        tags["crossing:signals"] = answer.toYesNo()
        if (!answer) return

        /* Additionally write the widely-supported legacy tag crossing=traffic_signals (e.g.
         * BRouter only knows that one), but never overwrite an existing crossing=* value.
         * The obsolete crossing=island is migrated to crossing:island=yes so the info is kept. */
        when (tags["crossing"]) {
            null -> tags["crossing"] = "traffic_signals"
            "island" -> {
                if (tags["crossing:island"] == null) tags["crossing:island"] = "yes"
                tags["crossing"] = "traffic_signals"
            }
        }
    }
}
