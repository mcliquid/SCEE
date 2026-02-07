package de.westnordost.streetcomplete.osm

import de.westnordost.streetcomplete.data.osm.mapdata.MapDataRepository
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.util.math.finalBearingTo
import de.westnordost.streetcomplete.util.math.initialBearingTo

fun Node.getDirection(mapDataSource: MapDataRepository): Double? {
    val direction = tags["direction"]
        ?: tags["traffic_sign:direction"]
        ?: tags["traffic_signals:direction"]
        ?: tags["railway:signal:direction"]
        ?: tags["camera:direction"]
        ?: return null
    direction.toDoubleOrNull()?.let { return it }
    return when (direction) {
        "S" -> 180.0
        "W" -> 270.0
        "N" -> 0.0
        "E" -> 90.0
        "SE" -> 135.0
        "SW" -> 225.0
        "NE" -> 45.0
        "NW" -> 315.0
        "forward", "backward" -> {
            val way = mapDataSource.getWaysForNode(id).singleOrNull() ?: return null
            val indexInWay = way.nodeIds.indexOf(id)
            getSignDirection(
                way.nodeIds.getOrNull(indexInWay - 1)?.let { mapDataSource.getNode(it) },
                way.nodeIds.getOrNull(indexInWay + 1)?.let { mapDataSource.getNode(it) },
                direction == "forward"
            )
        }
        else -> null
    }
}

fun Node.getSignDirection(previousNode: Node?, nextNode: Node?, isForward: Boolean): Double? {
    if (previousNode == null && nextNode == null) return null
    if (isForward && previousNode != null) {
        // sign points to previous node
        return position.initialBearingTo(previousNode.position)
    }
    if (isForward && nextNode != null) {
        // first node in way -> sign points away from next node
        return nextNode.position.finalBearingTo(position)
    }
    // backward -> previous and next nodes switch role
    if (nextNode != null) {
        return position.initialBearingTo(nextNode.position)
    }
    return previousNode!!.position.finalBearingTo(position)
}
