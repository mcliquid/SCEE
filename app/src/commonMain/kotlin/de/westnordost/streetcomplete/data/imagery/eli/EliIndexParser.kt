package de.westnordost.streetcomplete.data.imagery.eli

import de.westnordost.streetcomplete.data.imagery.ImageryCoverageParser
import de.westnordost.streetcomplete.data.imagery.RasterTileUrlNormalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object EliIndexParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<EliLayer> {
        val root = json.parseToJsonElement(text).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { parseFeature(it) }
    }

    fun isSupported(layer: EliLayer): Boolean {
        if (layer.overlay) return false
        if (layer.category != null && layer.category != "photo") return false
        if (layer.type != "tms" && layer.type != "wms") return false
        if (layer.url.contains("{apikey}")) return false
        return try {
            RasterTileUrlNormalizer.isValidTemplate(layer.url)
        } catch (_: Throwable) {
            // Never let URL/regex init failures abort ELI index construction.
            false
        }
    }

    private fun parseFeature(element: JsonElement): EliLayer? {
        val feature = element as? JsonObject ?: return null
        val props = feature["properties"]?.jsonObject ?: return null
        val id = props.string("id") ?: return null
        val name = props.string("name") ?: return null
        val type = props.string("type") ?: return null
        val url = props.string("url") ?: return null
        val geometry = feature["geometry"] as? JsonObject
        val parsed = ImageryCoverageParser.parseGeometry(geometry)
        return EliLayer(
            id = id,
            name = name,
            type = type,
            category = props.string("category"),
            url = url,
            best = props["best"]?.jsonPrimitive?.booleanOrNull == true,
            overlay = props["overlay"]?.jsonPrimitive?.booleanOrNull == true,
            minZoom = props["min_zoom"]?.jsonPrimitive?.intOrNull,
            maxZoom = props["max_zoom"]?.jsonPrimitive?.intOrNull,
            endDate = props.string("end_date"),
            boundingBox = parsed?.boundingBox,
            rings = parsed?.rings.orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
