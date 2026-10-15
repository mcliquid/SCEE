package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.distanceToArcs
import de.westnordost.streetcomplete.util.math.enlargedBy
import de.westnordost.streetcomplete.util.math.initialBearingTo
import de.westnordost.streetcomplete.util.math.arcIntersection
import de.westnordost.streetcomplete.util.math.toNormalOnSphere
import de.westnordost.streetcomplete.util.math.toLatLon
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
    val contextSection: List<LatLon> = emptyList(),
) {
    val elementKeys get() = (listOf(road.key, waterway.key) + nearbyWays.map { it.way.key } +
        (road.nodeIds + waterway.nodeIds + nearbyWays.flatMap { context ->
            nearbyEdges(context.way, context.positions, position, contextSection).flatMap { listOf(it.first, it.second) } +
                context.way.nodeIds.filter { it < 0 }
        }).distinct()
            .map { ElementKey(ElementType.NODE, it) }).distinct()

    fun idsUpdatesApplied(ids: Map<ElementKey, Long>) = copy(
        road = road.remap(ids), waterway = waterway.remap(ids),
        nearbyWays = nearbyWays.map { it.copy(way = it.way.remap(ids)) },
    )

    fun validate(repository: MapDataRepository, allowSharedFord: Boolean = false): Pair<Way, Way> {
        val (currentRoad, rp) = validateWay(road, roadPositions, repository)
        val (currentWater, wp) = validateWay(waterway, waterwayPositions, repository)
        checkCrossing(currentRoad, currentWater, rp, wp, position, allowSharedFord)
        for (context in nearbyWays) validateContext(context, position, contextSection, repository)
        return currentRoad to currentWater
    }
}

/** Downloaded surrounding transport geometry is also checked on replay/upload. A third way
 * at the edited crossing is deliberately outside this phase's supported geometry. */
@Serializable
data class CrossingContextWay(val way: Way, val positions: List<LatLon>)

internal const val MIN_CROSSING_EDGE_LENGTH = 1.0 // metres, independent of map zoom
// A margin for conflict snapshots, not a limit on surveyed structure length.
internal const val CROSSING_CONTEXT_MARGIN = 100.0
private val structureKeys = setOf("bridge", "tunnel", "covered", "ford", "layer", "level", "location")
private val roadTypes = setOf("motorway", "trunk", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
    "residential", "unclassified", "tertiary", "secondary", "primary",
    "service", "living_street", "track", "path", "footway", "cycleway", "bridleway", "pedestrian")
private val waterTypes = setOf("stream", "river", "canal", "ditch", "drain")
private val detectionFeatureKeys = setOf("highway", "waterway", "railway", "area")

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

private fun nearbyEdges(way: Way, positions: List<LatLon>, center: LatLon, section: List<LatLon>): List<NearbyEdge> =
    positions.zipWithNext().mapIndexedNotNull { i, (a, b) ->
        if (center.distanceToArc(a, b) <= CROSSING_CONTEXT_MARGIN || section.any { it.distanceToArc(a, b) <= CROSSING_CONTEXT_MARGIN }
            || section.size >= 2 && (a.distanceToArcs(section) <= CROSSING_CONTEXT_MARGIN || b.distanceToArcs(section) <= CROSSING_CONTEXT_MARGIN
                || crossingPoints(listOf(a, b), section).isNotEmpty()))
            NearbyEdge(way.nodeIds[i], way.nodeIds[i + 1], a, b) else null
    }

private fun validateContext(context: CrossingContextWay, center: LatLon, section: List<LatLon>, repository: MapDataRepository) {
    val data = repository.getWayComplete(context.way.id) ?: throw ConflictException("Crossing context unavailable")
    val current = data.getWay(context.way.id) ?: throw ConflictException("Crossing context unavailable")
    val positions = current.nodeIds.map { data.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry") }
    val before = nearbyEdges(context.way, context.positions, center, section)
    val after = nearbyEdges(current, positions, center, section)
    crossingCheck(before.size == after.size && before.zip(after).all { (a, b) ->
        a.first == b.first && a.second == b.second && a.a.equalsInOsm(b.a) && a.b.equalsInOsm(b.b)
    }, "Crossing context geometry moved")
}

/** Only reused within one detection run over the same data; replay/upload never shares this cache. */
internal class CrossingGeometryChecks {
    private val simpleWays = mutableMapOf<Long, Result<Unit>>()
    private val crossings = mutableMapOf<Pair<Long, Long>, List<LatLon>>()
    private val overlaps = mutableMapOf<Pair<Long, Long>, Boolean>()

    fun checkSimple(way: Way, positions: List<LatLon>) {
        simpleWays.getOrPut(way.id) {
            try { checkSimpleCrossingWay(way, positions); Result.success(Unit) }
            catch (e: ConflictException) { Result.failure(e) }
        }.getOrThrow()
    }

    fun intersections(road: Way, water: Way, rp: List<LatLon>, wp: List<LatLon>): List<LatLon> {
        val key = road.id to water.id
        return crossings[key] ?: uniqueCrossingPoints(rp, wp).also {
            // Empty pairs never reach validation. Avoid retaining every spatial near miss.
            if (it.isNotEmpty()) crossings[key] = it
        }
    }

    fun overlaps(road: Way, water: Way, rp: List<LatLon>, wp: List<LatLon>) =
        overlaps.getOrPut(road.id to water.id) { hasOverlappingCrossingEdges(rp, wp) }
}

internal fun checkCrossing(road: Way, water: Way, rp: List<LatLon>, wp: List<LatLon>, pos: LatLon,
    allowSharedFord: Boolean = false, geometryChecks: CrossingGeometryChecks? = null) {
    crossingCheck(road.id != water.id && road.tags["highway"] in roadTypes
        && water.tags["waterway"] in waterTypes, "Select one road and one linear waterway")
    for ((way, positions) in listOf(road to rp, water to wp)) {
        crossingCheck(way.tags.keys.none { it in structureKeys }
            && way.tags["area"] != "yes" && "railway" !in way.tags,
            "Existing structure, layer or area tags require manual editing")
        if (geometryChecks == null) checkSimpleCrossingWay(way, positions)
        else geometryChecks.checkSimple(way, positions)
    }
    val shared = road.nodeIds.filterIndexed { i, id -> id in water.nodeIds && rp[i].distanceTo(pos) < 0.01 }
    crossingCheck(shared.isEmpty() || allowSharedFord && shared.size == 1, "Ways already connected")
    crossingCheck(!(geometryChecks?.overlaps(road, water, rp, wp) ?: hasOverlappingCrossingEdges(rp, wp)), "Overlapping crossing ways")
    val crossings = geometryChecks?.intersections(road, water, rp, wp) ?: uniqueCrossingPoints(rp, wp)
    crossingCheck(crossings.count { it.equalsInOsm(pos) } == 1,
        "Crossing is no longer unique")
    if (shared.isNotEmpty()) {
        crossingCheck(road.allowsSimpleWaterwayFord(), "Ford on this road requires manual editing")
        val ri = road.nodeIds.indexOf(shared.single())
        val wi = water.nodeIds.indexOf(shared.single())
        crossingCheck(ri in 1 until rp.lastIndex && wi in 1 until wp.lastIndex,
            "Ford ways must continue through the crossing")
        crossingCheck(rp[ri].equalsInOsm(pos) && wp[wi].equalsInOsm(pos), "Shared crossing moved")
        // Four alternating rays distinguish a crossing from two lines merely touching.
        val rays = listOf(rp[ri - 1] to 0, rp[ri + 1] to 0, wp[wi - 1] to 1, wp[wi + 1] to 1)
            .map { (p, kind) -> pos.initialBearingTo(p) to kind }
            .sortedBy { it.first }
        crossingCheck(rays.indices.all { rays[it].second != rays[(it + 1) % 4].second
            && (rays[(it + 1) % 4].first - rays[it].first + 360.0) % 360.0 > 0.001 },
            "Ways only touch at shared node")
    }
    val vertices = rp.filterIndexed { i, _ -> road.nodeIds[i] !in shared } +
        wp.filterIndexed { i, _ -> water.nodeIds[i] !in shared }
    crossingCheck(vertices.all { it.distanceTo(pos) >= MIN_CROSSING_EDGE_LENGTH },
        "Crossing too close to an existing node")
}

/** Reject shared stretches even when another pair of edges has a proper intersection. */
internal fun hasOverlappingCrossingEdges(first: List<LatLon>, second: List<LatLon>): Boolean {
    val shared = ArrayList<LatLon>(4)
    for (i in 0 until first.lastIndex) for (j in 0 until second.lastIndex) {
        val a = first[i]
        val b = first[i + 1]
        val c = second[j]
        val d = second[j + 1]
        shared.clear()
        if (a.distanceToArc(c, d) < 0.01) shared.add(a)
        if (b.distanceToArc(c, d) < 0.01) shared.add(b)
        if (c.distanceToArc(a, b) < 0.01) shared.add(c)
        if (d.distanceToArc(a, b) < 0.01) shared.add(d)
        for (p in shared) for (q in shared) if (p.distanceTo(q) > 0.01) return true
    }
    return false
}

/** Shared by detection and replay; neither may treat ambiguous lines as a simple crossing. */
internal fun checkSimpleCrossingWay(way: Way, positions: List<LatLon>) {
    crossingCheck(!way.isClosed && way.nodeIds.size >= 2
        && way.nodeIds.size == positions.size
        && way.nodeIds.distinct().size == way.nodeIds.size
        && positions.distinct().size == positions.size, "Closed or ambiguous way")
    for (i in 0 until positions.lastIndex) {
        crossingCheck(positions[i].distanceTo(positions[i + 1]) > 0.01, "Degenerate segment")
    }
    val normals = positions.map { it.toNormalOnSphere() }
    for (i in 0 until positions.lastIndex) for (j in i + 2 until positions.lastIndex) {
        // Planar bbox reject is safe for short OSM segments; skip spherical work for far pairs.
        if (!crossingSegmentBoundsOverlap(positions[i], positions[i + 1], positions[j], positions[j + 1])) continue
        crossingCheck(arcIntersection(normals[i], normals[i + 1], normals[j], normals[j + 1]) == null,
            "Self-intersecting way")
    }
    // Adjacent edges may touch only at their shared vertex; reject backtracking/overlap.
    for (i in 1 until positions.lastIndex) {
        crossingCheck(positions[i - 1].distanceToArc(positions[i], positions[i + 1]) > 0.01
            && positions[i + 1].distanceToArc(positions[i - 1], positions[i]) > 0.01,
            "Overlapping way segments")
    }
}

fun crossingPoints(first: List<LatLon>, second: List<LatLon>): List<LatLon> {
    if (first.size < 2 || second.size < 2) return emptyList()
    val firstNormals = first.map { it.toNormalOnSphere() }
    val secondNormals = second.map { it.toNormalOnSphere() }
    val result = mutableListOf<LatLon>()
    for (i in 0 until first.lastIndex) {
        val a = first[i]
        val b = first[i + 1]
        for (j in 0 until second.lastIndex) {
            val c = second[j]
            val d = second[j + 1]
            // Exact shared endpoints are already known intersections. Spherical rounding
            // can put a recomputed endpoint just outside both adjacent arcs.
            val point = when {
                a == c || a == d -> a
                b == c || b == d -> b
                else -> arcIntersection(firstNormals[i], firstNormals[i + 1], secondNormals[j], secondNormals[j + 1])?.toLatLon()
            }
            if (point != null) result.add(point)
        }
    }
    return result
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
        waters.flatMap { water ->
            val wp = positions(water) ?: return@flatMap emptyList()
            uniqueCrossingPoints(rp, wp).filter { it.distanceTo(near) <= radius }.mapNotNull { pos ->
                try { checkEditableCrossing(data, road, water, rp, wp, pos) } catch (_: ConflictException) { return@mapNotNull null }
                if (!crossingSupportsBridgeOrCulvert(road, water, rp, pos)) return@mapNotNull null
                val context = crossingContext(data, road, water, pos) ?: return@mapNotNull null
                WaterwayCrossing(road, water, rp, wp, pos, context)
            }
        }
    }.sortedBy { it.position.distanceTo(near) }
}

internal fun crossingContext(data: MapData, road: Way, water: Way, pos: LatLon): List<CrossingContextWay>? {
    val context = mutableListOf<CrossingContextWay>()
    for (other in data.ways) {
        if (other.id == road.id || other.id == water.id) continue
        if ("highway" !in other.tags && "waterway" !in other.tags && "railway" !in other.tags) continue
        if (other.nodeIds.size < 2) continue
        // Resolve nodes first so incomplete nearby transport geometry still rejects the candidate.
        val positions = ArrayList<LatLon>(other.nodeIds.size)
        var minLat = Double.POSITIVE_INFINITY
        var maxLat = Double.NEGATIVE_INFINITY
        var minLon = Double.POSITIVE_INFINITY
        var maxLon = Double.NEGATIVE_INFINITY
        for (id in other.nodeIds) {
            val p = data.getNode(id)?.position ?: return null
            positions.add(p)
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        // Far ways cannot contribute context; skip the arc-distance work.
        if (pos !in BoundingBox(minLat, minLon, maxLat, maxLon).enlargedBy(CROSSING_CONTEXT_MARGIN)) continue
        val distance = pos.distanceToArcs(positions)
        if (distance < 0.01) return null
        if (distance <= CROSSING_CONTEXT_MARGIN)
            context.add(CrossingContextWay(other, positions))
    }
    return context
}

/** Axis-aligned segment bounds with a tiny epsilon; used only to skip impossible self-intersections. */
private fun crossingSegmentBoundsOverlap(a: LatLon, b: LatLon, c: LatLon, d: LatLon): Boolean {
    val eps = 1e-9
    return maxOf(a.longitude, b.longitude) + eps >= minOf(c.longitude, d.longitude)
        && minOf(a.longitude, b.longitude) - eps <= maxOf(c.longitude, d.longitude)
        && maxOf(a.latitude, b.latitude) + eps >= minOf(c.latitude, d.latitude)
        && minOf(a.latitude, b.latitude) - eps <= maxOf(c.latitude, d.latitude)
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

/** Bridge and culvert need an interior crossing; a shared vertex at the crossing supports only ford. */
fun crossingSupportsBridgeOrCulvert(road: Way, water: Way, roadPositions: List<LatLon>, position: LatLon): Boolean =
    road.nodeIds.withIndex().none { (i, id) ->
        id in water.nodeIds && roadPositions[i].distanceTo(position) < 0.01
    }

/** Only geometry and tags which influence crossing eligibility; versions and unrelated tags do not. */
internal fun crossingDetectionElement(element: Element): Element? = when (element) {
    is Way -> if ("highway" in element.tags || "waterway" in element.tags || "railway" in element.tags)
        element.copy(tags = element.tags.filterKeys { it in structureKeys || it in detectionFeatureKeys },
            version = 0, timestampEdited = 0) else null
    is Node -> element.copy(tags = element.tags.filterKeys { it in structureKeys }, version = 0, timestampEdited = 0)
    is Relation -> null
}

internal fun crossingDetectionInput(data: MapData): List<Element> {
    val ways = data.ways.mapNotNull { crossingDetectionElement(it) as? Way }
    val nodeIds = ways.flatMap { it.nodeIds }.toSet()
    val sharedIds = ways.filter { it.isCrossingRoad() }.flatMap { it.nodeIds }.toSet()
        .intersect(ways.filter { it.isCrossingWaterway() }.flatMap { it.nodeIds }.toSet())
    val wayIds = ways.map { it.id }.toSet()
    val otherReferences = data.ways.filter { way -> way.id !in wayIds && way.nodeIds.any { it in sharedIds } }
        .map { it.copy(tags = emptyMap(), version = 0, timestampEdited = 0) }
    return (ways + otherReferences + data.relations.filter { r -> r.members.any { it.type == ElementType.NODE && it.ref in sharedIds } } + data.nodes.filter { it.id in nodeIds }.mapNotNull {
        if (it.id in sharedIds) it.copy(version = 0, timestampEdited = 0) else crossingDetectionElement(it)
    })
        .sortedWith(compareBy({ it.type.ordinal }, { it.id }))
}

internal fun Way.isCrossingRoad() = tags["highway"] in roadTypes
internal fun Way.isCrossingWaterway() = tags["waterway"] in waterTypes
internal fun Way.hasCrossingStructure() = tags.keys.any { it in structureKeys }

private fun Way.remap(ids: Map<ElementKey, Long>) = copy(
    id = ids[key] ?: id,
    nodeIds = nodeIds.map { ids[ElementKey(ElementType.NODE, it)] ?: it },
)

/** Shared vertices support only a conservative tag-only ford; unconnected ways keep all answers. */
internal fun checkEditableCrossing(data: MapData, road: Way, water: Way, rp: List<LatLon>, wp: List<LatLon>, pos: LatLon,
    geometryChecks: CrossingGeometryChecks? = null) {
    checkCrossing(road, water, rp, wp, pos, allowSharedFord = true, geometryChecks = geometryChecks)
    val shared = road.nodeIds.singleOrNull { it in water.nodeIds && data.getNode(it)?.position?.distanceTo(pos)?.let { d -> d < 0.01 } == true } ?: return
    val node = data.getNode(shared) ?: throw ConflictException("Incomplete crossing geometry")
    crossingCheck(node.tags.isEmpty(), "Existing crossing node tags require manual editing")
    crossingCheck(data.ways.filter { shared in it.nodeIds }.map { it.id }.toSet() == setOf(road.id, water.id),
        "Additional way at ford; use manual editing")
    crossingCheck(data.relations.none { r -> r.members.any { it.type == ElementType.NODE && it.ref == shared } },
        "Crossing node in relation requires manual editing")
}
