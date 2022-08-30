package de.westnordost.streetcomplete.overlays.custom

import android.content.SharedPreferences
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.mapdata.filter
import de.westnordost.streetcomplete.overlays.Overlay
import de.westnordost.streetcomplete.overlays.PointStyle
import de.westnordost.streetcomplete.overlays.PolygonStyle
import de.westnordost.streetcomplete.overlays.PolylineStyle
import de.westnordost.streetcomplete.overlays.Style
import de.westnordost.streetcomplete.data.elementfilter.ParseException

class CustomOverlay(val prefs: SharedPreferences) : Overlay {

    override val title = R.string.custom_overlay_title
    override val icon = R.drawable.ic_quest_poi_fixme
    override val changesetComment = "Edit user-defined element selection"
    override val wikiLink: String = "Tags"

    override fun getStyledElements(mapData: MapDataWithGeometry): Sequence<Pair<Element, Style>> {
        val filter = try {
            prefs.getString(Prefs.CUSTOM_OVERLAY_FILTER, "")?.toElementFilterExpression() ?: return emptySequence()
        } catch (e: ParseException) { return emptySequence() }
        return mapData
            .filter(filter)
            .map { it to getStyle(it) }
    }

    override fun createForm(element: Element) = CustomOverlayForm()
}

private fun getStyle(element: Element): Style {
    val color = "#ccff00"
    return when {
        element is Node -> PointStyle(element.tags["name"])
        element.tags["area"] == "yes" -> PolygonStyle(color, null)
        else -> PolylineStyle(color)
    }
}