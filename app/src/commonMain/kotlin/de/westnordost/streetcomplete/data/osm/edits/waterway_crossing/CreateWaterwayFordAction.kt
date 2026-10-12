package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.osm.edits.*
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.InsertIntoWayAt
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.util.math.distanceToArc
import kotlinx.serialization.Serializable

/** Only an interior crossing of two unconnected simple ways. Existing vertices are never merged. */
@Serializable
data class CreateWaterwayFordAction(val crossing: WaterwayCrossing) : ElementEditAction {
    override val newElementsCount get() = NewElementsCount(1, 0, 0)
    override val elementKeys get() = crossing.elementKeys
    override fun idsUpdatesApplied(updatedIds: Map<ElementKey, Long>) =
        copy(crossing = crossing.idsUpdatesApplied(updatedIds))

    override fun createUpdates(mapDataRepository: MapDataRepository, idProvider: ElementIdProvider): MapDataChanges {
        val repository = CrossingMapDataRepository(mapDataRepository)
        val (road, water) = crossing.validate(repository)
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
