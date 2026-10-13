package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.split_way.*
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.util.math.distanceTo
import de.westnordost.streetcomplete.util.math.distanceToArc
import de.westnordost.streetcomplete.util.math.measuredLength
import kotlinx.serialization.Serializable
import de.westnordost.streetcomplete.util.ktx.equalsInOsm

@Serializable
enum class WaterwayStructure { BRIDGE, CULVERT }

/** One persisted edit and one upload: both cuts, relation replacements and structure tags.
 * iD crossing_ways.makeAddBridgeOrTunnelFix is the reference for selecting the bounded piece;
 * boundaries here are surveyed manually rather than estimated from the other way's width. */
@Serializable
data class SetWaterwayStructureAction(
    val crossing: WaterwayCrossing,
    val structure: WaterwayStructure,
    val splits: List<SplitPolylineAtPosition>,
) : ElementEditAction {
    private val splitAction get() = SplitWayAction(
        if (structure == WaterwayStructure.BRIDGE) crossing.road else crossing.waterway, splits)
    override val newElementsCount get() = splitAction.newElementsCount
    override val elementKeys get() = crossing.elementKeys
    override fun idsUpdatesApplied(updatedIds: Map<ElementKey, Long>) =
        copy(crossing = crossing.idsUpdatesApplied(updatedIds))

    /** Same ordering as SplitWayAction, without reserving IDs or changing map data. */
    fun selectedPositions(): List<LatLon> {
        crossingCheck(splits.size == 2, "Choose two boundaries")
        val positions = if (structure == WaterwayStructure.BRIDGE) crossing.roadPositions else crossing.waterwayPositions
        val cuts = splits.map { it.toSplitWayAt(positions) }.sorted()
        val start = when (val cut = cuts.first()) {
            is SplitWayAtIndex -> cut.index + 1
            is SplitWayAtLinePosition -> cut.index2
        }
        val end = when (val cut = cuts.last()) {
            is SplitWayAtIndex -> cut.index
            is SplitWayAtLinePosition -> cut.index2
        }
        crossingCheck(start <= end, "Ambiguous split boundaries")
        return listOf(cuts.first().pos) + positions.subList(start, end) + cuts.last().pos
    }

    override fun createUpdates(mapDataRepository: MapDataRepository, idProvider: ElementIdProvider): MapDataChanges {
        val repository = CrossingMapDataRepository(mapDataRepository)
        crossing.validate(repository)
        crossingCheck(splits.size == 2 && splits[0].pos.distanceTo(splits[1].pos) >= 2 * MIN_CROSSING_EDGE_LENGTH,
            "Choose two distinct boundaries at least two metres apart")
        fun currentPositions(way: Way) = way.nodeIds.map {
            repository.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry")
        }
        val positions = currentPositions(if (structure == WaterwayStructure.BRIDGE) crossing.road else crossing.waterway)
        val actualCrossing = uniqueCrossingPoints(currentPositions(crossing.road), currentPositions(crossing.waterway)).single { it.equalsInOsm(crossing.position) }
        for (split in splits) {
            val resolved = split.toSplitWayAt(positions)
            if (resolved is SplitWayAtLinePosition) {
                crossingCheck(resolved.pos.distanceTo(resolved.pos1) >= MIN_CROSSING_EDGE_LENGTH
                    && resolved.pos.distanceTo(resolved.pos2) >= MIN_CROSSING_EDGE_LENGTH,
                    "Boundary would create a segment shorter than one metre")
            }
        }
        val changes = splitAction.createUpdates(repository, idProvider)
        val ways = (changes.creations + changes.modifications).filterIsInstance<Way>()
        crossingCheck(ways.size == 3, "Expected three unambiguous way pieces")
        val nodes = changes.creations.filterIsInstance<Node>().associateBy { it.id }
        fun position(id: Long) = nodes[id]?.position ?: repository.getNode(id)?.position
            ?: throw ConflictException("Missing boundary node")
        // Match BOTH bounds, independent of which piece keeps the original OSM ID.
        val boundaryIds = splits.map { split ->
            ways.flatMap { listOf(it.nodeIds.first(), it.nodeIds.last()) }.distinct()
                .singleOrNull { position(it).equalsInOsm(split.pos) }
                ?: throw ConflictException("Ambiguous split boundary")
        }
        val middle = ways.singleOrNull {
            setOf(it.nodeIds.first(), it.nodeIds.last()) == boundaryIds.toSet()
        } ?: throw ConflictException("No unique middle piece")
        val middlePositions = middle.nodeIds.map(::position)
        val target = if (structure == WaterwayStructure.BRIDGE) crossing.road else crossing.waterway
        val counterparts = listOf(if (structure == WaterwayStructure.BRIDGE) crossing.waterway else crossing.road) + crossing.nearbyWays.map { it.way }
        val enclosed = mutableListOf<LatLon>()
        for (snapshot in counterparts.distinctBy { it.id }) {
            val complete = repository.getWayComplete(snapshot.id) ?: throw ConflictException("Crossing context unavailable")
            val other = complete.getWay(snapshot.id) ?: throw ConflictException("Crossing context unavailable")
            val op = currentPositions(other)
            crossingCheck(!hasOverlappingCrossingEdges(middlePositions, op), "Overlapping crossing ways")
            val points = uniqueCrossingPoints(middlePositions, op)
            if (points.isEmpty()) continue
            // Every enclosed counterpart must independently satisfy the same editing rules.
            for (point in points) {
                crossingCheck(enclosed.none { it.distanceTo(point) < MIN_CROSSING_EDGE_LENGTH },
                    "Additional way at crossing; use manual editing")
                enclosed.add(point)
                if (structure == WaterwayStructure.BRIDGE) checkCrossing(target, other, positions, op, point)
                else checkCrossing(other, target, op, positions, point)
                crossingCheck(middlePositions.first().distanceTo(point) >= MIN_CROSSING_EDGE_LENGTH
                    && middlePositions.last().distanceTo(point) >= MIN_CROSSING_EDGE_LENGTH,
                    "Boundary too close to a crossing")
            }
        }
        crossingCheck(middlePositions.zipWithNext().any { (a, b) -> actualCrossing.distanceToArc(a, b) < 0.01 }
            && middlePositions.first().distanceTo(actualCrossing) >= MIN_CROSSING_EDGE_LENGTH
            && middlePositions.last().distanceTo(actualCrossing) >= MIN_CROSSING_EDGE_LENGTH,
            "Both boundaries must enclose the crossing")
        crossingCheck(ways.all { it.nodeIds.map(::position).measuredLength() >= MIN_CROSSING_EDGE_LENGTH },
            "Split would create a tiny way")
        val tagged = middle.copy(tags = middle.tags + when (structure) {
            WaterwayStructure.BRIDGE -> mapOf("bridge" to "yes", "layer" to "1")
            WaterwayStructure.CULVERT -> mapOf("tunnel" to "culvert", "layer" to "-1")
        })
        fun replace(element: Element) = if (element.key == middle.key) tagged else element
        return changes.copy(creations = changes.creations.map(::replace), modifications = changes.modifications.map(::replace))
    }
}
