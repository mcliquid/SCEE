package de.westnordost.streetcomplete.overlays.smoothness

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.data.overlays.Overlay
import de.westnordost.streetcomplete.data.overlays.OverlayAction
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.BICYCLIST
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.CAR
import de.westnordost.streetcomplete.data.user.achievements.EditTypeAchievement.WHEELCHAIR
import de.westnordost.streetcomplete.osm.ALL_PATHS
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.overlays.surface.surfaceTaggingNotExpected
import de.westnordost.streetcomplete.quests.smoothness.AddPathSmoothness
import de.westnordost.streetcomplete.quests.smoothness.AddRoadSmoothness
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import de.westnordost.streetcomplete.resources.*

class SmoothnessOverlay : Overlay {

    override val title = Res.string.overlay_smoothness
    override val icon = Res.drawable.quest_street_surface_detail
    override val changesetComment = "Specify smoothness"
    override val wikiLink: String = "Key:smoothness"
    override val achievements = listOf(CAR, BICYCLIST, WHEELCHAIR)
    // AddCyclewayPartSmoothness is not hidden: it also asks for road-side cycleway smoothness,
    // which this overlay does not edit
    override val hidesQuestTypes = setOf(
        AddRoadSmoothness::class.simpleName!!,
        AddPathSmoothness::class.simpleName!!,
    )

    override fun getStyledElements(mapData: MapDataWithGeometry) =
        mapData.filter("""
            ways, relations with
              highway ~ ${SMOOTHNESS_OVERLAY_HIGHWAYS.joinToString("|")}
        """).map { it to getSmoothnessStyle(it) }

    @Composable
    override fun Form(
        on: (OverlayAction) -> Unit,
        element: Element?,
        geometry: ElementGeometry,
        countryInfo: CountryInfo,
    ) {
        requireNotNull(element)
        SmoothnessOverlayForm(on, element, countryInfo)
    }
}

private val SMOOTHNESS_OVERLAY_HIGHWAYS = ALL_ROADS + ALL_PATHS - "steps"

/* Design considerations:
 * - sequential palette from blue (excellent) via yellow to orange, as proposed in #5486. Few ways
 *   are worse than very bad, so horrible and very horrible share a colour
 * - impassable is black, the colour reserved for "no" / "does not exist"
 * - red marks missing and invalid values, like in the surface overlay. Invalid values are
 *   additionally labelled with the raw value. Like there, missing values are not highlighted
 *   where tagging is not expected (indoor, private, links); those ways can still be tapped
 */
internal val Smoothness.color: Color get() = when (this) {
    EXCELLENT ->     OverlayColor.Blue
    GOOD ->          OverlayColor.Sky
    INTERMEDIATE ->  OverlayColor.Cyan
    BAD ->           OverlayColor.Lime
    VERY_BAD ->      OverlayColor.Gold
    HORRIBLE ->      OverlayColor.Orange
    VERY_HORRIBLE -> OverlayColor.Orange
    IMPASSABLE ->    OverlayColor.Black
}

/** Severity for showing the worse of two values: invalid > impassable > … > excellent > missing */
private val ParsedSmoothness?.severity: Int get() = when (this) {
    null -> 0
    is ParsedSmoothness.Valid -> 1 + value.ordinal
    is ParsedSmoothness.Invalid -> 1 + Smoothness.entries.size
}

internal fun getSmoothnessStyle(element: Element): OverlayStyle {
    val state = parseSmoothnessOverlayState(element.tags)
    val smoothness = if (state.isSegregated) {
        val parts = listOf(state.footway.smoothness, state.cycleway.smoothness)
        // as long as no part is known, the common smoothness of the whole way is shown
        if (parts.all { it == null }) state.whole.smoothness else parts.maxBy { it.severity }
    } else {
        state.whole.smoothness
    }
    val color = when (smoothness) {
        // same as in the surface overlay
        null -> if (surfaceTaggingNotExpected(element)) OverlayColor.Invisible else OverlayColor.Red
        is ParsedSmoothness.Valid -> smoothness.value.color
        is ParsedSmoothness.Invalid -> OverlayColor.Red
    }
    val label = (smoothness as? ParsedSmoothness.Invalid)?.raw
    return if (element.tags["area"] == "yes") {
        OverlayStyle.Polygon(color, label = label)
    } else {
        OverlayStyle.Polyline(stroke = OverlayStyle.Stroke(color), label = label)
    }
}
