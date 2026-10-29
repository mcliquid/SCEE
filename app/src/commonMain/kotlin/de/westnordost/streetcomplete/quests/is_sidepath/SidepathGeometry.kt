package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.osm.ALL_ROADS
import de.westnordost.streetcomplete.osm.PEDESTRIAN_ONLY_ROADS
import de.westnordost.streetcomplete.util.ktx.asSequenceOfPairs
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.enlargedBy
import de.westnordost.streetcomplete.util.math.initialBearingTo
import de.westnordost.streetcomplete.util.math.intersect
import de.westnordost.streetcomplete.util.math.normalizeDegrees
import kotlin.math.abs
import kotlin.math.min
import kotlinx.serialization.Serializable

/** Motor-traffic roads considered for sidepath geometry / candidate list. */
val SIDEPATH_ROADS: Set<String> = ALL_ROADS - PEDESTRIAN_ONLY_ROADS - setOf("service", "track")

const val MAX_SIDEPATH_DISTANCE_METERS = 20.0
const val MAX_SIDEPATH_ALIGNMENT_DEGREES = 30.0

/** Extra padding for map-data queries so long roads with sparse nodes are still returned (#3797). */
const val SIDEPATH_MAP_DATA_QUERY_PADDING_METERS = 100.0

/** OSM `name` of a road for `is_sidepath:of:name`; never synthesized from ref or other keys. */
internal fun sidepathAssociatedRoadName(tags: Map<String, String>): String? =
    tags["name"]?.takeIf { it.isNotEmpty() }

/** OSM `ref` of a road for `is_sidepath:of:ref`; never synthesized from name or other keys. */
internal fun sidepathAssociatedRoadRef(tags: Map<String, String>): String? =
    tags["ref"]?.takeIf { it.isNotEmpty() }

@Serializable
data class CandidateRoad(
    val element: Element,
    val geometry: ElementGeometry,
    val distance: Double,
    val alignmentDiff: Double,
) {
    val name: String? get() = sidepathAssociatedRoadName(element.tags)
    val ref: String? get() = sidepathAssociatedRoadRef(element.tags)
    val highway: String? get() = element.tags["highway"]
}

/** Absolute bearing difference folded so opposite OSM way directions count as parallel. */
fun alignmentDiffDegrees(bearingA: Double, bearingB: Double): Double {
    val bearingDiff = abs(normalizeDegrees(bearingA - bearingB, -180.0))
    return if (bearingDiff > 90.0) 180.0 - bearingDiff else bearingDiff
}

fun ElementGeometry.eligiblePolylines(): List<List<LatLon>> =
    (this as? ElementPolylinesGeometry)?.polylines?.filter { it.size >= 2 } ?: emptyList()

fun ElementGeometry.asOpenPolylineOrNull(): List<LatLon>? =
    eligiblePolylines().singleOrNull()

/**
 * Returns eligible nearby roads sorted by distance, then alignment, then element id.
 * Does not deduplicate same-name ways (keeps highlighting unambiguous).
 */
fun findCandidateRoads(
    pathGeometry: ElementGeometry,
    mapData: MapDataWithGeometry,
    maxDistance: Double = MAX_SIDEPATH_DISTANCE_METERS,
    maxAlignment: Double = MAX_SIDEPATH_ALIGNMENT_DEGREES,
): List<CandidateRoad> {
    val pathPolylines = pathGeometry.eligiblePolylines()
    if (pathPolylines.isEmpty()) return emptyList()
    val pathBounds = pathGeometry.bounds.enlargedBy(maxDistance)

    return mapData.ways.mapNotNull { road ->
        val highway = road.tags["highway"] ?: return@mapNotNull null
        if (highway !in SIDEPATH_ROADS) return@mapNotNull null
        val roadGeometry = mapData.getWayGeometry(road.id) ?: return@mapNotNull null
        val roadPolylines = roadGeometry.eligiblePolylines()
        if (roadPolylines.isEmpty()) return@mapNotNull null
        if (!pathBounds.intersect(roadGeometry.bounds)) return@mapNotNull null

        val match = bestNearAndAlignedPairAcross(pathPolylines, roadPolylines, maxDistance, maxAlignment)
            ?: return@mapNotNull null
        CandidateRoad(
            element = road,
            geometry = roadGeometry,
            distance = match.first,
            alignmentDiff = match.second,
        )
    }.sortedWith(
        compareBy<CandidateRoad> { it.distance }
            .thenBy { it.name == null }
            .thenBy { it.alignmentDiff }
            .thenBy { it.element.id }
    )
}

fun hasNearbyAlignedRoad(
    pathGeometry: ElementGeometry,
    mapData: MapDataWithGeometry,
    maxDistance: Double = MAX_SIDEPATH_DISTANCE_METERS,
    maxAlignment: Double = MAX_SIDEPATH_ALIGNMENT_DEGREES,
): Boolean {
    val pathPolylines = pathGeometry.eligiblePolylines()
    if (pathPolylines.isEmpty()) return false
    val pathBounds = pathGeometry.bounds.enlargedBy(maxDistance)

    for (road in mapData.ways) {
        val highway = road.tags["highway"] ?: continue
        if (highway !in SIDEPATH_ROADS) continue
        val roadGeometry = mapData.getWayGeometry(road.id) ?: continue
        val roadPolylines = roadGeometry.eligiblePolylines()
        if (roadPolylines.isEmpty()) continue
        if (!pathBounds.intersect(roadGeometry.bounds)) continue
        if (bestNearAndAlignedPairAcross(pathPolylines, roadPolylines, maxDistance, maxAlignment) != null) {
            return true
        }
    }
    return false
}

internal fun bestNearAndAlignedPairAcross(
    paths: List<List<LatLon>>,
    roads: List<List<LatLon>>,
    maxDistance: Double,
    maxAlignment: Double,
): Pair<Double, Double>? {
    var bestDistance = Double.POSITIVE_INFINITY
    var bestAlignment = Double.POSITIVE_INFINITY

    for (path in paths) {
        for (road in roads) {
            val match = bestNearAndAlignedPair(path, road, maxDistance, maxAlignment) ?: continue
            if (
                match.first < bestDistance ||
                (match.first == bestDistance && match.second < bestAlignment)
            ) {
                bestDistance = match.first
                bestAlignment = match.second
            }
        }
    }

    return if (bestDistance.isFinite()) bestDistance to bestAlignment else null
}

/** Best matching segment pair as (distance, alignmentDiff), or null if none within thresholds. */
internal fun bestNearAndAlignedPair(
    path: List<LatLon>,
    road: List<LatLon>,
    maxDistance: Double,
    maxAlignment: Double,
): Pair<Double, Double>? {
    var bestDistance = Double.POSITIVE_INFINITY
    var bestAlignment = Double.POSITIVE_INFINITY

    for ((p0, p1) in path.asSequenceOfPairs()) {
        val pathBearing = p0.initialBearingTo(p1)
        for ((r0, r1) in road.asSequenceOfPairs()) {
            val distance = segmentCentrelineDistance(p0, p1, r0, r1)
            if (distance > maxDistance) continue
            val alignment = alignmentDiffDegrees(pathBearing, r0.initialBearingTo(r1))
            if (alignment > maxAlignment) continue
            if (
                distance < bestDistance ||
                (distance == bestDistance && alignment < bestAlignment)
            ) {
                bestDistance = distance
                bestAlignment = alignment
            }
        }
    }

    return if (bestDistance.isFinite()) bestDistance to bestAlignment else null
}

/** Bidirectional vertex-to-arc distance between two segments. */
internal fun segmentCentrelineDistance(
    a0: LatLon,
    a1: LatLon,
    b0: LatLon,
    b1: LatLon,
): Double = min(
    min(a0.distanceToArc(b0, b1), a1.distanceToArc(b0, b1)),
    min(b0.distanceToArc(a0, a1), b1.distanceToArc(a0, a1)),
)
