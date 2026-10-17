package de.westnordost.streetcomplete.quests.traffic_sign

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignAccess
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns
import de.westnordost.streetcomplete.osm.traffic_sign.applyConfirmed
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.isWayInGermany
import de.westnordost.streetcomplete.osm.traffic_sign.needsTrafficSignSurvey
import de.westnordost.streetcomplete.quests.FullElementSelectionDialog
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept

/** The answer to [AddTrafficSignType]: the signs and the proposals for other tags that the user
 *  confirmed (id -> fingerprint, see TagProposal). null: only the proposals that add tags */
data class TrafficSignAnswer(
    val signs: TrafficSignWaySigns,
    val confirmed: Map<String, String>? = null,
)

/** Asks which traffic signs apply to a way, separately for both directions of the OSM way.
 *
 *  Only the obviously relevant ways are asked about; the complete survey is for the traffic sign
 *  overlay. Normal roads without access tags get no quest.
 *
 *  Which ways are asked about (cheapest check first):
 *  1. the element filter ([DEFAULT_ELEMENT_FILTER], configurable in the quest settings)
 *  2. at least one direction is not surveyed or only has traffic_sign=yes
 *     (see [needsTrafficSignSurvey]). traffic_sign=none is a valid answer.
 *  3. the way is completely in Germany (only after 1. and 2., needs the country boundaries)
 *
 *  The answer is written to the way only, via the traffic sign tagging engine, which keeps
 *  everything that is not answered. A direction that is not answered stays as it is. */
class AddTrafficSignType(
    /** returns the ISO 3166-1 (or subdivision) code of the country at the given position */
    private val getCountryCode: (LatLon) -> String?,
) : OsmElementQuestType<TrafficSignAnswer> {

    private val filter by lazy {
        prefs
            .getString(getPrefixedFullElementSelectionPref(prefs), DEFAULT_ELEMENT_FILTER)
            .toElementFilterExpression()
    }

    override val changesetComment = "Survey traffic signs"
    override val wikiLink = "Key:traffic_sign"
    override val icon = Res.drawable.quest_traffic_sign
    override val title = Res.string.quest_traffic_sign_title
    override val hint = Res.string.quest_traffic_sign_hint
    override val achievements = emptyList<de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement>()
    /* The catalogue and the rules to derive tags from signs only exist for Germany. It is not
       overridable by the expert setting. */
    override val enabledInCountries = NoCountriesExcept("DE")
    override val defaultDisabledMessage = Res.string.default_disabled_msg_ee
    override val previousAnswerKeys = listOf("traffic_sign", "traffic_sign:forward", "traffic_sign:backward")
    override val hasQuestSettings = true

    @Composable
    override fun QuestSettings(onDismissRequest: () -> Unit) {
        FullElementSelectionDialog(
            prefs,
            getPrefixedFullElementSelectionPref(prefs),
            Res.string.quest_settings_element_selection,
            DEFAULT_ELEMENT_FILTER,
            onDismissRequest
        )
    }

    private fun matchesTags(element: Element): Boolean =
        element.type == ElementType.WAY &&
        filter.matches(element) &&
        needsTrafficSignSurvey(element.tags)

    override fun getApplicableElements(mapData: MapDataWithGeometry): Iterable<Element> =
        mapData.ways.filter {
            matchesTags(it) && isWayInGermany(mapData.getWayGeometry(it.id), getCountryCode)
        }

    /** false if the tags do not qualify (the common case, e.g. after the quest was answered). For
     *  the remaining candidates, null: the complete check including the position of the whole way
     *  is done by [getApplicableElements], so that ways that cross the border never get a quest. */
    override fun isApplicableTo(element: Element): Boolean? =
        if (matchesTags(element)) null else false

    @Composable
    override fun Form(
        on: (QuestAction<TrafficSignAnswer>) -> Unit,
        element: Element,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        AddTrafficSignTypeForm(on, element, geometry)
    }

    override fun applyAnswerTo(answer: TrafficSignAnswer, tags: Tags, geometry: ElementGeometry, timestampEdited: Long) {
        // The tags may have changed since the form was shown. Everything is derived from the current
        // tags; if nothing is left to change, the tags are left alone.
        // The proposals are derived again from the tags as they are now. A proposal is only written if
        // the user confirmed exactly this one: if tags or values changed since, it is left out.
        answer.signs.deriveWayTags(tags).applyConfirmed(tags, answer.confirmed)
    }

    companion object {
        /** Roads that are always asked about (if not excluded below) */
        private const val ALWAYS = "track|path|cycleway|footway|living_street|pedestrian"
        /** Roads that are only asked about if they have an access tag that hints at a sign, see
         *  [TrafficSignAccess]. Not: motorway, trunk, bridleway, steps and any *_link */
        private const val WITH_ACCESS = "residential|service|unclassified|tertiary|secondary|primary"

        /** The default element filter. The whole text can be replaced in the quest settings; then
         *  none of it applies any more, only what is fixed in code: ways, at least one direction
         *  that needs a survey ([needsTrafficSignSurvey]), Germany.
         *
         *  Ways are asked about if
         *  - they are not an area, and
         *  - they are a track, path, cycleway, footway, living_street or pedestrian way, or a road of [WITH_ACCESS] that has an access
         *    tag that hints at a sign, and
         *  - they are not minor service ways (driveways, parking aisles, ...), and not sidewalks,
         *    crossings, links or traffic islands. These exclusions apply to all groups.
         *  access=private is not excluded as such: it counts as an access tag that hints at a sign. */
        val DEFAULT_ELEMENT_FILTER = """
            ways with
              area != yes
              and (
                highway ~ $ALWAYS
                or (highway ~ $WITH_ACCESS and (${TrafficSignAccess.ELEMENT_FILTER_FRAGMENT}))
              )
              and (highway != service or service !~ driveway|parking_aisle|drive-through|slipway|emergency_access|alley|bus|siding|yard|spur)
              and (highway != footway or footway !~ sidewalk|crossing|link|traffic_island)
              and (highway != cycleway or cycleway !~ crossing|link)
              and (highway != path or path !~ sidewalk|crossing|link)
        """.trimIndent()
    }
}
