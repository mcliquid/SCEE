package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeFromVertexAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.InsertIntoWayAt
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.util.math.distanceToArc
import kotlinx.serialization.Serializable

/** Interior ford: reuse a safe shared vertex or insert one node into two unconnected ways. */
@Serializable
data class CreateWaterwayFordAction(val crossing: WaterwayCrossing) : ElementEditAction {
    override val newElementsCount get() = NewElementsCount(if (crossing.road.nodeIds.any { it in crossing.waterway.nodeIds }) 0 else 1, 0, 0)
    override val elementKeys get() = crossing.elementKeys
    override fun idsUpdatesApplied(updatedIds: Map<ElementKey, Long>) =
        copy(crossing = crossing.idsUpdatesApplied(updatedIds))

    override fun createUpdates(mapDataRepository: MapDataRepository, idProvider: ElementIdProvider): MapDataChanges {
        val repository = CrossingMapDataRepository(mapDataRepository)
        val (road, water) = crossing.validate(repository, allowSharedFord = true)
        crossingCheck(crossing.nearbyWays.none { context -> repository.positionsForWay(context.way.id).zipWithNext().any { (a, b) ->
            crossing.position.distanceToArc(a, b) < MIN_CROSSING_EDGE_LENGTH
        } }, "Additional way at ford; use manual editing")
        crossingCheck(road.allowsSimpleWaterwayFord(),
            "Ford on this road requires manual editing")
        fun positions(way: Way) = way.nodeIds.map {
            repository.getNode(it)?.position ?: throw de.westnordost.streetcomplete.data.ConflictException("Incomplete crossing geometry")
        }
        val roadPositions = positions(road)
        val waterPositions = positions(water)
        val shared = road.nodeIds.singleOrNull { it in water.nodeIds }
        if (shared != null) {
            val node = repository.getNode(shared) ?: throw de.westnordost.streetcomplete.data.ConflictException("Crossing node unavailable")
            crossingCheck(node.tags.isEmpty(), "Existing crossing node tags require manual editing")
            crossingCheck(repository.getRelationsForNode(shared).isEmpty(), "Crossing node in relation requires manual editing")
            val changes = StringMapChangesBuilder(node.tags).apply { this["ford"] = "yes" }.create()
            // The existing action rechecks exact membership. Use fresh coordinates already
            // validated to OSM precision, so upload rounding does not cause a false conflict.
            return CreateNodeFromVertexAction(node, changes, listOf(road.id, water.id))
                .createUpdates(repository, idProvider)
        }
        val currentPosition = crossingPoints(roadPositions, waterPositions).single()
        val inserts = listOf(road to roadPositions, water to waterPositions).map { (way, positions) ->
            val segment = positions.zipWithNext().singleOrNull { (a, b) -> currentPosition.distanceToArc(a, b) < 0.01 }
                ?: throw de.westnordost.streetcomplete.data.ConflictException("Ambiguous ford segment")
            InsertIntoWayAt(way.id, segment.first, segment.second)
        }
        return CreateNodeAction(currentPosition, mapOf("ford" to "yes"), inserts)
            .createUpdates(repository, idProvider)
    }
}
