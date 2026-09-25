package de.westnordost.streetcomplete.screens.main.map.layers

import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.preset_maki_circle
import de.westnordost.streetcomplete.screens.main.map.toGeometry
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.ui.ktx.id
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Geometry
import kotlin.collections.set

fun Marker.toGeoJsonFeature(): List<Feature<Geometry, JsonObject>> {
    val features = ArrayList<Feature<Geometry, JsonObject>>(3)
    // point marker or any marker with title or icon
    val color = this.color?.toRgbaString() ?: "#D140D0"
    if (icon != null || title != null || geometry is ElementPointGeometry) {
        val p = HashMap<String, JsonElement>(4)

        p["icon"] = JsonPrimitive((icon ?: Res.drawable.preset_maki_circle).id)
        if (title != null) {
            p["label"] = JsonPrimitive(title)
        }
        p["color"] = JsonPrimitive(color)
        p["rotation"] = JsonPrimitive(rotation ?: 0.0)
        features.add(Feature(geometry.center.toGeometry(), JsonObject(p)))
    }

    if (direction != null && geometry is ElementPointGeometry) {
        val p = HashMap<String, JsonElement>(3)
        p["direction"] = JsonPrimitive(true)
        p["color"] = JsonPrimitive(this.color?.toRgbaString() ?: "rgba(209,64,208,0.5)")
        p["rotation"] = JsonPrimitive(direction)
        features.add(Feature(geometry.center.toGeometry(), JsonObject(p)))
    }

    // polygon / polylines marker(s)
    if (geometry is ElementPolygonsGeometry || geometry is ElementPolylinesGeometry) {
        features.add(Feature(
            geometry.toGeometry(),
            JsonObject(mapOf("color" to JsonPrimitive(color))),
        ))
    }
    return features
}

private fun Int.toRgbaString(): String {
    val a = (this ushr 24) and 0xFF
    val r = (this shr 16) and 0xFF
    val g = (this shr 8) and 0xFF
    val b = this and 0xFF
    return "rgba($r, $g, $b, ${a / 255f})"
}
