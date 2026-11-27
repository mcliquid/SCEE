package de.westnordost.streetcomplete.overlays.sidewalk

import androidx.compose.runtime.Composable
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.overlays.Overlay
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.PEDESTRIAN
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.overlays.cycleway.SeparateCyclewayForm
import de.westnordost.streetcomplete.osm.any
import de.westnordost.streetcomplete.osm.footway.FootwayKind
import de.westnordost.streetcomplete.osm.footway.parseFootwayKind
import de.westnordost.streetcomplete.osm.isPrivateOnFoot
import de.westnordost.streetcomplete.osm.maxspeed.MAX_SPEED_TYPE_KEYS
import de.westnordost.streetcomplete.osm.sidewalk.Sidewalk
import de.westnordost.streetcomplete.osm.sidewalk.parseSidewalkSides
import de.westnordost.streetcomplete.osm.surface.UNPAVED_SURFACES
import de.westnordost.streetcomplete.quests.sidewalk.AddSidewalk
import de.westnordost.streetcomplete.resources.*

class SidewalkOverlay : Overlay {

    override val title = Res.string.overlay_sidewalk
    override val icon = Res.drawable.quest_sidewalk
    override val changesetComment = "Specify sidewalks and kinds of footways"
    override val wikiLink: String = "Key:sidewalk"
    override val achievements = listOf(PEDESTRIAN)
    override val hidesQuestTypes = setOf(AddSidewalk::class.simpleName!!)

    override fun getStyledElements(mapData: MapDataWithGeometry) =
        // roads
        mapData.filter("""
            ways with
              highway ~ ${ALL_ROADS.joinToString("|")}
              and area != yes
        """).map { it to getSidewalkStyle(it) } +
        // footways etc, just to highlight e.g. separately mapped sidewalks. However, it is also
        // possible to add sidewalks to them. At least in NL, cycleways with sidewalks actually exist
        mapData.filter("""
            ways with
              highway ~ footway|steps|path|bridleway|cycleway
              and area != yes
        """).map { it to getFootwayStyle(it) }

    @Composable
    override fun Form(
        on: (OverlayAction) -> Unit,
        element: Element?,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        requireNotNull(element)
        when (element.tags["highway"]) {
            in ALL_ROADS -> SidewalkOverlayForm(on, element, geometry, countryInfo)
            "footway" -> FootwayKindForm(on, element)
            "path", "cycleway" -> SeparateCyclewayForm(on, element, countryInfo)
            // steps, bridleway: not selectable, see getFootwayStyle. The separate cycleway form
            // would retag highway=* for these
            else -> {}
        }
    }
}

/** highways that are only displayed for orientation, but have no form in this overlay */
private val NOT_SELECTABLE_PATHS = setOf("steps", "bridleway")

private fun getFootwayStyle(element: Element): OverlayStyle.Polyline {
    val foot = element.tags["foot"] ?: when (element.tags["highway"]) {
        "footway", "steps" -> "designated"
        "path" -> "yes"
        else -> null
    }
    val isAccessibleOnFoot = foot in listOf("yes", "designated")
    val footwayKind = parseFootwayKind(element.tags)

    val style = when {
        // highway=footway is displayed by its kind of footway, because that is what is edited here
        footwayKind != null ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(
                if (isAccessibleOnFoot) footwayKind.color else OverlayColor.Invisible
            ))
        parseSidewalkSides(element.tags)?.any { it == Sidewalk.YES } == true ->
            getSidewalkStyle(element)
        isAccessibleOnFoot ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Sky))
        else ->
            OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Invisible))
    }
    return if (element.tags["highway"] in NOT_SELECTABLE_PATHS) style.copy(disabled = true) else style
}

/** Not specified is neutral (same as other foot paths), an unsupported value is not an error */
private val FootwayKind.color get() = when (this) {
    FootwayKind.UNSPECIFIED ->    OverlayColor.Sky
    FootwayKind.SIDEWALK ->       OverlayColor.Blue
    FootwayKind.CROSSING ->       OverlayColor.Orange
    FootwayKind.TRAFFIC_ISLAND -> OverlayColor.Gold
    FootwayKind.ACCESS_AISLE ->   OverlayColor.Lime
    FootwayKind.UNSUPPORTED ->    OverlayColor.Purple
}

private fun getSidewalkStyle(element: Element): OverlayStyle.Polyline {
    val sidewalks = parseSidewalkSides(element.tags)
    val isNoSidewalkExpected = lazy { sidewalkTaggingNotExpected(element) || isPrivateOnFoot(element) }

    return OverlayStyle.Polyline(
        stroke = getStreetStrokeStyle(element.tags),
        strokeLeft = sidewalks?.left.getStyle(isNoSidewalkExpected),
        strokeRight = sidewalks?.right.getStyle(isNoSidewalkExpected)
    )
}

private fun getStreetStrokeStyle(tags: Map<String, String>): OverlayStyle.Stroke? =
    when {
        tags["highway"] == "pedestrian" ->
            OverlayStyle.Stroke(OverlayColor.Sky)
        tags["highway"] == "living_street" || tags["living_street"] == "yes" ->
            OverlayStyle.Stroke(OverlayColor.Sky, dashed = true)
        else -> null
    }

private val sidewalkTaggingNotExpectedFilter by lazy { """
    ways with
      highway ~ track|living_street|pedestrian|service|motorway_link|motorway|busway
      or motorroad = yes
      or expressway = yes
      or maxspeed <= 10
      or maxspeed = walk
      or surface ~ ${UNPAVED_SURFACES.joinToString("|")}
      or ~"${MAX_SPEED_TYPE_KEYS.joinToString("|")}" ~ ".*:(zone)?:?([1-9]|10)"
""".toElementFilterExpression() }

private fun sidewalkTaggingNotExpected(element: Element) =
    sidewalkTaggingNotExpectedFilter.matches(element)

private fun Sidewalk?.getStyle(isNoSidewalkExpected: Lazy<Boolean>) = OverlayStyle.Stroke(when (this) {
    Sidewalk.YES ->      OverlayColor.Sky
    Sidewalk.NO ->       OverlayColor.Black
    Sidewalk.SEPARATE -> OverlayColor.Invisible
    Sidewalk.INVALID  -> OverlayColor.Red
    null ->              if (isNoSidewalkExpected.value) OverlayColor.Invisible else OverlayColor.Red
})
