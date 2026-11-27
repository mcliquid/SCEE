package de.westnordost.streetcomplete.util.math

import de.westnordost.streetcomplete.data.osm.edits.create.InsertIntoWayAt
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.Way

/** Possible targets for "insert node into way" near a position.
 *
 *  Every candidate describes exactly the topology the resulting edit will have:
 *  - [VertexOfWay]: an existing node, with all loaded ways that contain it
 *  - [PositionOnWaySegment]: a new node in exactly one way
 *  - [PositionOnWaysSegment]: a new node in a segment that several ways already share (same nodes),
 *    so inserting into all of them keeps the existing topology
 *  - [PositionOnCrossingWaySegments]: a new node that connects two so far unconnected ways. Only
 *    offered for explicit selection, never [preselected].
 */
data class InsertNodeTargets(
    val candidates: List<PositionOnWay>,
    /** candidate that is unambiguous enough to be selected without user interaction, or null */
    val preselected: PositionOnWay?,
)

/** Returns the possible targets for "insert node into way" at this position.
 *
 *  @param ways all loaded ways with complete geometry. Used to find out which ways share a node or
 *              segment, so that existing topology is preserved.
 *  @param isCandidate whether a way may be offered as target (e.g. level filter)
 *  @param maxDistance max distance from this position to a way
 *  @param snapToVertexDistance existing vertices within this distance are preferred over new nodes
 *  @param ambiguityDistance if the second-best candidate is not at least this much further away
 *                           than the best one, nothing is preselected
 *  @param allowPreselection false to always require an explicit choice (e.g. if geometry of some
 *                           nearby ways is incomplete)
 */
fun LatLon.getInsertNodeTargets(
    ways: Collection<Pair<Way, List<LatLon>>>,
    isCandidate: (Way) -> Boolean = { true },
    maxDistance: Double,
    snapToVertexDistance: Double,
    ambiguityDistance: Double,
    allowPreselection: Boolean = true,
): InsertNodeTargets {
    // existing vertices nearby, one candidate per node
    val vertexDistance = minOf(maxDistance, snapToVertexDistance)
    val vertices = LinkedHashMap<Long, Pair<VertexOfWay, Double>>()
    for ((way, positions) in ways) {
        if (!isCandidate(way)) continue
        for ((i, nodeId) in way.nodeIds.withIndex()) {
            if (nodeId in vertices) continue
            val distance = distanceTo(positions[i])
            if (distance > vertexDistance) continue
            val wayIds = ways.filter { nodeId in it.first.nodeIds }.map { it.first.id }.toSet()
            vertices[nodeId] = VertexOfWay(wayIds, positions[i], nodeId) to distance
        }
    }
    val waysWithVertexCandidate = vertices.values.flatMap { it.first.wayIds }.toSet()

    // nearest segment of every other way
    val nearestSegments = ArrayList<NearestSegment>()
    for ((way, positions) in ways) {
        if (!isCandidate(way) || way.id in waysWithVertexCandidate) continue
        var best: NearestSegment? = null
        for (i in 0 until positions.size - 1) {
            val distance = distanceToArc(positions[i], positions[i + 1])
            if (distance > maxDistance) continue
            if (best == null || distance < best.distance) {
                best = NearestSegment(way, i, positions[i], positions[i + 1], distance)
            }
        }
        if (best != null) nearestSegments.add(best)
    }

    // group by actually shared segments (same two nodes), never by mere geometric overlap
    val segmentGroups = nearestSegments
        .groupBy { it.nodeIdPair }
        .values
        .map { group ->
            val first = group.first()
            val position = nearestPointOnArc(first.pos1, first.pos2)
            val sharingWays = ways.filter { (way, _) -> way.containsSegment(first.nodeIdPair) }
            val target: PositionOnWay = if (sharingWays.size <= 1) {
                PositionOnWaySegment(first.way.id, position, first.pos1 to first.pos2)
            } else {
                PositionOnWaysSegment(position, sharingWays.map { (way, positions) ->
                    val i = way.indexOfSegment(first.nodeIdPair)
                    InsertIntoWayAt(way.id, positions[i], positions[i + 1])
                })
            }
            target to first.distance
        }
        .sortedBy { it.second }

    // new connections between two so far unconnected single ways: offered, never preselected
    val singleSegments = nearestSegments.filter { s -> segmentGroups.any { (t, _) -> t is PositionOnWaySegment && t.wayId == s.way.id } }
    val crossings = ArrayList<PositionOnCrossingWaySegments>()
    for (i in singleSegments.indices) {
        for (j in i + 1 until singleSegments.size) {
            val a = singleSegments[i]
            val b = singleSegments[j]
            if (a.nodeIdPair.first in b.nodeIdPair.toList() || a.nodeIdPair.second in b.nodeIdPair.toList()) continue
            if (!mayOfferNewConnection(a.way, b.way)) continue
            val intersection = intersectionOf(a.pos1, a.pos2, b.pos1, b.pos2) ?: continue
            if (distanceTo(intersection) > maxDistance) continue
            crossings.add(PositionOnCrossingWaySegments(intersection, listOf(
                InsertIntoWayAt(a.way.id, a.pos1, a.pos2),
                InsertIntoWayAt(b.way.id, b.pos1, b.pos2),
            )))
        }
    }

    val sortedVertices = vertices.values.sortedBy { it.second }
    val preselected = if (!allowPreselection) {
        null
    } else if (sortedVertices.isNotEmpty()) {
        val (vertex, distance) = sortedVertices.first()
        val otherVertexTooNear = sortedVertices.getOrNull(1)?.let { it.second - distance <= ambiguityDistance } ?: false
        val segmentNearer = segmentGroups.any { it.second < distance }
        if (otherVertexTooNear || segmentNearer) null else vertex
    } else if (segmentGroups.isNotEmpty()) {
        val (segment, distance) = segmentGroups.first()
        val otherTooNear = segmentGroups.getOrNull(1)?.let { it.second - distance <= ambiguityDistance } ?: false
        if (otherTooNear) null else segment
    } else {
        null
    }

    return InsertNodeTargets(
        candidates = sortedVertices.map { it.first } + segmentGroups.map { it.first } + crossings,
        preselected = preselected,
    )
}

/** All ids of ways whose topology an edit at this target touches */
val PositionOnWay.affectedWayIds: List<Long> get() = when (this) {
    is VertexOfWay -> wayIds.toList()
    is PositionOnWaySegment -> listOf(wayId)
    is PositionOnWaysSegment -> insertIntoWaysAt.map { it.wayId }
    is PositionOnCrossingWaySegments -> insertIntoWaysAt.map { it.wayId }
}

/** Whether a new connection of the two ways may be offered at all (still needs explicit selection):
 *  not if any of them is a bridge, tunnel or covered, or if they are on different layers/levels. */
internal fun mayOfferNewConnection(a: Way, b: Way): Boolean =
    a.isAtGround() && b.isAtGround()
    && (a.tags["layer"] ?: "0") == (b.tags["layer"] ?: "0")
    && a.tags["level"] == b.tags["level"]

private fun Way.isAtGround(): Boolean =
    listOf("bridge", "tunnel", "covered").none { key -> tags[key].let { it != null && it != "no" } }

private class NearestSegment(
    val way: Way,
    val index: Int,
    val pos1: LatLon,
    val pos2: LatLon,
    val distance: Double,
) {
    /** unordered pair of node ids of this segment */
    val nodeIdPair: Pair<Long, Long> = way.nodeIds[index].let { a ->
        val b = way.nodeIds[index + 1]
        if (a <= b) a to b else b to a
    }
}

private fun Way.indexOfSegment(nodeIdPair: Pair<Long, Long>): Int =
    (0 until nodeIds.size - 1).first { i ->
        val a = nodeIds[i]
        val b = nodeIds[i + 1]
        (if (a <= b) a to b else b to a) == nodeIdPair
    }

private fun Way.containsSegment(nodeIdPair: Pair<Long, Long>): Boolean =
    (0 until nodeIds.size - 1).any { i ->
        val a = nodeIds[i]
        val b = nodeIds[i + 1]
        (if (a <= b) a to b else b to a) == nodeIdPair
    }
