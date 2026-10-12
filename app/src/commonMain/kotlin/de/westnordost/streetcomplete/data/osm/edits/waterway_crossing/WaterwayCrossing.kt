package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.distanceToArcs
import de.westnordost.streetcomplete.util.math.intersectionOf
import kotlinx.serialization.Serializable
import de.westnordost.streetcomplete.util.ktx.equalsInOsm

/** A crossing pair. Positions are kept for strict replay
 * checks: a moved crossing must never acquire the answer to the old crossing. */
@Serializable
data class WaterwayCrossing(
    val road: Way,
    val waterway: Way,
    val roadPositions: List<LatLon>,
    val waterwayPositions: List<LatLon>,
    val position: LatLon,
    val nearbyWays: List<CrossingContextWay> = emptyList(),
) {
    val elementKeys get() = (listOf(road.key, waterway.key) + nearbyWays.map { it.way.key } +
        (road.nodeIds + waterway.nodeIds + nearbyWays.flatMap { context ->
            nearbyEdges(context.way, context.positions, position).flatMap { listOf(it.first, it.second) } +
                context.way.nodeIds.filter { it < 0 }
        }).distinct()
            .map { ElementKey(ElementType.NODE, it) }).distinct()

    fun idsUpdatesApplied(ids: Map<ElementKey, Long>) = copy(
        road = road.remap(ids), waterway = waterway.remap(ids),
        nearbyWays = nearbyWays.map { it.copy(way = it.way.remap(ids)) },
    )

    fun validate(repository: MapDataRepository): Pair<Way, Way> {
        val (currentRoad, rp) = validateWay(road, roadPositions, repository)
        val (currentWater, wp) = validateWay(waterway, waterwayPositions, repository)
        checkCrossing(currentRoad, currentWater, rp, wp, position)
        for (context in nearbyWays) validateContext(context, position, repository)
        return currentRoad to currentWater
    }
}

/** Downloaded surrounding transport geometry is also checked on replay/upload. A third way
 * at the edited crossing is deliberately outside this phase's supported geometry. */
@Serializable
data class CrossingContextWay(val way: Way, val positions: List<LatLon>)

internal const val MIN_CROSSING_EDGE_LENGTH = 1.0 // metres, independent of map zoom
// Keep the first phase's structure and its checked surrounding geometry within a bounded area.
internal const val MAX_CROSSING_STRUCTURE_LENGTH = 100.0
private val structureKeys = setOf("bridge", "tunnel", "covered", "ford", "layer", "level", "location")
private val roadTypes = setOf("motorway", "trunk", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    "residential", "unclassified", "tertiary", "secondary", "primary",
    "service", "living_street", "track", "path", "footway", "cycleway", "bridleway", "pedestrian")
private val waterTypes = setOf("stream", "river", "canal", "ditch", "drain")

internal fun crossingCheck(condition: Boolean, message: String) {
    if (!condition) throw ConflictException(message)
}

private fun validateWay(original: Way, positions: List<LatLon>, repo: MapDataRepository): Pair<Way, List<LatLon>> {
    val complete = repo.getWayComplete(original.id)
        ?: throw ConflictException("Crossing way deleted or unavailable")
    val current = complete.getWay(original.id) ?: throw ConflictException("Crossing way deleted")
    crossingCheck(current.nodeIds == original.nodeIds, "Crossing way nodes changed")
    val currentPositions = current.nodeIds.map {
        complete.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry")
    }
    crossingCheck(currentPositions.size == positions.size
        && currentPositions.zip(positions).all { (current, original) -> current.equalsInOsm(original) }, "Crossing geometry moved")
    crossingCheck(current.tags["highway"] == original.tags["highway"]
        && current.tags["waterway"] == original.tags["waterway"], "Crossing feature type changed")
    return current to currentPositions
}

private data class NearbyEdge(val first: Long, val second: Long, val a: LatLon, val b: LatLon)

private fun nearbyEdges(way: Way, positions: List<LatLon>, center: LatLon): List<NearbyEdge> =
    positions.zipWithNext().mapIndexedNotNull { i, (a, b) ->
        if (center.distanceToArc(a, b) <= MAX_CROSSING_STRUCTURE_LENGTH)
            NearbyEdge(way.nodeIds[i], way.nodeIds[i + 1], a, b) else null
    }

private fun validateContext(context: CrossingContextWay, center: LatLon, repository: MapDataRepository) {
    val data = repository.getWayComplete(context.way.id) ?: throw ConflictException("Crossing context unavailable")
    val current = data.getWay(context.way.id) ?: throw ConflictException("Crossing context unavailable")
    val positions = current.nodeIds.map { data.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry") }
    val before = nearbyEdges(context.way, context.positions, center)
    val after = nearbyEdges(current, positions, center)
    crossingCheck(before.size == after.size && before.zip(after).all { (a, b) ->
        a.first == b.first && a.second == b.second && a.a.equalsInOsm(b.a) && a.b.equalsInOsm(b.b)
    }, "Crossing context geometry moved")
}

internal fun checkCrossing(road: Way, water: Way, rp: List<LatLon>, wp: List<LatLon>, pos: LatLon) {
    crossingCheck(road.id != water.id && road.tags["highway"] in roadTypes
        && water.tags["waterway"] in waterTypes, "Select one road and one linear waterway")
    for ((way, positions) in listOf(road to rp, water to wp)) {
        crossingCheck(way.tags.keys.none { it in structureKeys }
            && way.tags["area"] != "yes" && "railway" !in way.tags,
            "Existing structure, layer or area tags require manual editing")
        checkSimpleCrossingWay(way, positions)
    }
    crossingCheck(road.nodeIds.none { it in water.nodeIds }, "Ways already connected")
    crossingCheck(!hasOverlappingCrossingEdges(rp, wp), "Overlapping crossing ways")
    val crossings = crossingPoints(rp, wp)
    crossingCheck(crossings.size == 1 && crossings.single().equalsInOsm(pos),
        "Crossing is no longer unique")
    crossingCheck((rp + wp).all { it.distanceTo(pos) >= MIN_CROSSING_EDGE_LENGTH },
        "Crossing too close to an existing node")
}

/** Reject shared stretches even when another pair of edges has a proper intersection. */
internal fun hasOverlappingCrossingEdges(first: List<LatLon>, second: List<LatLon>): Boolean =
    first.zipWithNext().any { (a, b) -> second.zipWithNext().any { (c, d) ->
        val shared = listOf(a, b).filter { it.distanceToArc(c, d) < 0.01 } +
            listOf(c, d).filter { it.distanceToArc(a, b) < 0.01 }
        shared.any { p -> shared.any { q -> p.distanceTo(q) > 0.01 } }
    } }

/** Shared by detection and replay; neither may treat ambiguous lines as a simple crossing. */
internal fun checkSimpleCrossingWay(way: Way, positions: List<LatLon>) {
    crossingCheck(!way.isClosed && way.nodeIds.size >= 2
        && way.nodeIds.size == positions.size
        && way.nodeIds.distinct().size == way.nodeIds.size
        && positions.distinct().size == positions.size, "Closed or ambiguous way")
    crossingCheck(positions.zipWithNext().all { (a, b) -> a.distanceTo(b) > 0.01 }, "Degenerate segment")
    val segments = positions.zipWithNext()
    for (i in segments.indices) for (j in i + 2 until segments.size) {
        crossingCheck(intersectionOf(segments[i].first, segments[i].second,
            segments[j].first, segments[j].second) == null, "Self-intersecting way")
    }
    // Adjacent edges may touch only at their shared vertex; reject backtracking/overlap.
    for (i in 1 until positions.lastIndex) {
        crossingCheck(positions[i - 1].distanceToArc(positions[i], positions[i + 1]) > 0.01
            && positions[i + 1].distanceToArc(positions[i - 1], positions[i]) > 0.01,
            "Overlapping way segments")
    }
}

fun crossingPoints(first: List<LatLon>, second: List<LatLon>): List<LatLon> =
    first.zipWithNext().flatMap { (a, b) ->
        second.zipWithNext().mapNotNull { (c, d) -> intersectionOf(a, b, c, d) }
    }

/** Unique geometric intersections near the selected point. Editing applies stricter checks. */
fun findWaterwayCrossings(data: MapDataWithGeometry, near: LatLon, radius: Double): List<WaterwayCrossing> {
    fun positions(way: Way): List<LatLon>? {
        val nodes = way.nodeIds.map { data.getNode(it)?.position ?: return null }
        return nodes
    }
    val roads = data.ways.filter { it.tags["highway"] in roadTypes }
    val waters = data.ways.filter { it.tags["waterway"] in waterTypes }
    return roads.flatMap { road ->
        val rp = positions(road) ?: return@flatMap emptyList()
        waters.mapNotNull { water ->
            val wp = positions(water) ?: return@mapNotNull null
            val points = uniqueCrossingPoints(rp, wp)
            val pos = points.singleOrNull()?.takeIf { it.distanceTo(near) <= radius }
                ?: return@mapNotNull null
            try { checkCrossing(road, water, rp, wp, pos) } catch (_: ConflictException) { return@mapNotNull null }
            val context = crossingContext(data, road, water, pos) ?: return@mapNotNull null
            WaterwayCrossing(road, water, rp, wp, pos, context)
        }
    }.sortedBy { it.position.distanceTo(near) }
}

internal fun crossingContext(data: MapDataWithGeometry, road: Way, water: Way, pos: LatLon): List<CrossingContextWay>? {
    val context = mutableListOf<CrossingContextWay>()
    for (other in data.ways.filter { it.id != road.id && it.id != water.id
        && ("highway" in it.tags || "waterway" in it.tags || "railway" in it.tags) }) {
        val positions = other.nodeIds.map { data.getNode(it)?.position ?: return null }
        if (positions.size < 2) continue
        if (pos.distanceToArcs(positions) < 0.01) return null
        if (pos.distanceToArcs(positions) <= MAX_CROSSING_STRUCTURE_LENGTH)
            context.add(CrossingContextWay(other, positions))
    }
    return context
}

/** Adjacent segments can report the same crossing at an existing vertex more than once. */
fun uniqueCrossingPoints(first: List<LatLon>, second: List<LatLon>): List<LatLon> {
    val result = mutableListOf<LatLon>()
    for (point in crossingPoints(first, second)) {
        if (result.none { it.distanceTo(point) < 0.01 }) result.add(point)
    }
    return result
}

internal fun Way.allowsSimpleWaterwayFord() = tags["highway"] in setOf(
    "residential", "unclassified", "service", "living_street", "track", "path", "footway", "cycleway", "bridleway", "pedestrian")

/** Only geometry and tags which influence crossing eligibility; versions and unrelated tags do not. */
internal fun crossingDetectionElement(element: Element): Element? = when (element) {
    is Way -> if ("highway" in element.tags || "waterway" in element.tags || "railway" in element.tags)
        element.copy(tags = element.tags.filterKeys { it in structureKeys || it in setOf("highway", "waterway", "railway", "area") },
            version = 0, timestampEdited = 0) else null
    is Node -> element.copy(tags = element.tags.filterKeys { it in structureKeys }, version = 0, timestampEdited = 0)
    is Relation -> null
}

internal fun crossingDetectionInput(data: MapDataWithGeometry): List<Element> {
    val ways = data.ways.mapNotNull { crossingDetectionElement(it) as? Way }
    val nodeIds = ways.flatMap { it.nodeIds }.toSet()
    return (ways + data.nodes.filter { it.id in nodeIds }.mapNotNull(::crossingDetectionElement))
        .sortedWith(compareBy({ it.type.ordinal }, { it.id }))
}

internal fun Way.isCrossingRoad() = tags["highway"] in roadTypes
internal fun Way.isCrossingWaterway() = tags["waterway"] in waterTypes
internal fun Way.hasCrossingStructure() = tags.keys.any { it in structureKeys }

private fun Way.remap(ids: Map<ElementKey, Long>) = copy(
    id = ids[key] ?: id,
    nodeIds = nodeIds.map { ids[ElementKey(ElementType.NODE, it)] ?: it },
)
