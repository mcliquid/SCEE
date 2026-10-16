package de.westnordost.streetcomplete.data.imagery

import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.isInMultipolygon
import de.westnordost.streetcomplete.util.math.isRingDefinedClockwise
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Optional geographic coverage for a manual aerial source.
 * Reuses the same GeoJSON → rings → [isInMultipolygon] path as ELI.
 */
@Serializable
data class ManualRasterCoverage(
    val minLat: Double? = null,
    val minLon: Double? = null,
    val maxLat: Double? = null,
    val maxLon: Double? = null,
    /** Raw GeoJSON geometry or Feature / FeatureCollection text. */
    val geoJson: String? = null,
) {
    fun hasGeometry(): Boolean =
        !geoJson.isNullOrBlank() || (minLat != null && minLon != null && maxLat != null && maxLon != null)
}

data class ParsedCoverage(
    val boundingBox: BoundingBox?,
    val rings: List<List<LatLon>>,
) {
    fun contains(position: LatLon): Boolean {
        if (rings.isEmpty()) {
            val bbox = boundingBox ?: return false
            return !bbox.crosses180thMeridian && bbox.contains(position)
        }
        val bbox = boundingBox
        if (bbox != null && !bbox.crosses180thMeridian && !bbox.contains(position)) {
            return false
        }
        return position.isInMultipolygon(rings)
    }

    fun areaEstimate(): Double {
        val bbox = boundingBox ?: return Double.MAX_VALUE
        return (bbox.max.latitude - bbox.min.latitude) *
            (bbox.max.longitude - bbox.min.longitude)
    }
}

object ImageryCoverageParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(coverage: ManualRasterCoverage?): ParsedCoverage? {
        if (coverage == null || !coverage.hasGeometry()) return null
        val fromGeoJson = coverage.geoJson?.let { parseGeoJson(it) }
        if (fromGeoJson != null) return fromGeoJson
        val minLat = coverage.minLat ?: return null
        val minLon = coverage.minLon ?: return null
        val maxLat = coverage.maxLat ?: return null
        val maxLon = coverage.maxLon ?: return null
        val bbox = BoundingBox(
            min = LatLon(latitude = minLat, longitude = minLon),
            max = LatLon(latitude = maxLat, longitude = maxLon),
        )
        return ParsedCoverage(bbox, emptyList())
    }

    fun parseGeoJson(text: String): ParsedCoverage? {
        return try {
            val root = json.parseToJsonElement(text)
            val geometry = when {
                root is JsonObject && root.string("type") == "FeatureCollection" ->
                    root["features"]?.jsonArray?.firstOrNull()?.jsonObject?.get("geometry")
                root is JsonObject && root.string("type") == "Feature" ->
                    root["geometry"]
                root is JsonObject -> root
                else -> null
            } ?: return null
            parseGeometry(geometry.jsonObject)
        } catch (_: Exception) {
            null
        }
    }

    fun parseGeometry(geometry: JsonObject?): ParsedCoverage? {
        if (geometry == null) return null
        val type = geometry.string("type") ?: return null
        val coordinates = geometry["coordinates"] ?: return null
        val rings = when (type) {
            "Polygon" -> polygonRings(coordinates.jsonArray)
            "MultiPolygon" -> coordinates.jsonArray.flatMap { polygonRings(it.jsonArray) }
            else -> emptyList()
        }
        if (rings.isEmpty()) return null
        val allPoints = rings.flatten()
        val bbox = BoundingBox(
            min = LatLon(
                latitude = allPoints.minOf { it.latitude },
                longitude = allPoints.minOf { it.longitude },
            ),
            max = LatLon(
                latitude = allPoints.maxOf { it.latitude },
                longitude = allPoints.maxOf { it.longitude },
            ),
        )
        return ParsedCoverage(bbox, rings)
    }

    private fun polygonRings(coords: JsonArray): List<List<LatLon>> {
        if (coords.isEmpty()) return emptyList()
        return coords.mapIndexedNotNull { index, ringEl ->
            val ring = ringEl.jsonArray.mapNotNull { pair ->
                val arr = pair.jsonArray
                if (arr.size < 2) return@mapNotNull null
                val lon = arr[0].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                val lat = arr[1].jsonPrimitive.content.toDoubleOrNull() ?: return@mapNotNull null
                LatLon(latitude = lat, longitude = lon)
            }
            if (ring.size < 4) return@mapIndexedNotNull null
            normalizeWinding(ring, hole = index > 0)
        }
    }

    private fun normalizeWinding(ring: List<LatLon>, hole: Boolean): List<LatLon> {
        val clockwise = ring.isRingDefinedClockwise()
        return when {
            hole && !clockwise -> ring.asReversed()
            !hole && clockwise -> ring.asReversed()
            else -> ring
        }
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull
}
