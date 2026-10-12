package de.westnordost.streetcomplete.data.osm.edits.waterway_crossing

import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.data.osm.mapdata.*

/** Reuse complete responses only within one replay attempt. Relations remain fresh. */
internal class CrossingMapDataRepository(private val delegate: MapDataRepository) : MapDataRepository by delegate {
    private val completeWays = mutableMapOf<Long, MapData>()
    private val nodes = mutableMapOf<Long, Node>()

    override fun getWayComplete(id: Long): MapData? {
        completeWays[id]?.let { return it }
        val data = delegate.getWayComplete(id) ?: return null
        val way = data.getWay(id) ?: throw ConflictException("Crossing way unavailable")
        if (way.nodeIds.any { data.getNode(it) == null })
            throw ConflictException("Incomplete crossing geometry")
        completeWays[id] = data
        for (nodeId in way.nodeIds) {
            val node = data.getNode(nodeId) ?: throw ConflictException("Incomplete crossing geometry")
            nodes[nodeId] = node
        }
        return data
    }

    fun positionsForWay(id: Long): List<LatLon> {
        val data = getWayComplete(id) ?: throw ConflictException("Crossing way unavailable")
        val way = data.getWay(id) ?: throw ConflictException("Crossing way unavailable")
        return way.nodeIds.map { data.getNode(it)?.position ?: throw ConflictException("Incomplete crossing geometry") }
    }

    override fun getNode(id: Long): Node? = nodes[id] ?: delegate.getNode(id)
}
