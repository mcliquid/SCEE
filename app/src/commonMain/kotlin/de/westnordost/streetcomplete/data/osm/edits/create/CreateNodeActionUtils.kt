package de.westnordost.streetcomplete.data.osm.edits.create

import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.createChanges
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.util.math.PositionOnCrossingWaySegments
import de.westnordost.streetcomplete.util.math.PositionOnWay
import de.westnordost.streetcomplete.util.math.PositionOnWaySegment
import de.westnordost.streetcomplete.util.math.PositionOnWaysSegment
import de.westnordost.streetcomplete.util.math.VertexOfWay

/** Creates the action for "insert node into way" from the result of the tag editor.
 *
 *  For a [VertexOfWay], [shownVertex] must be the vertex as it was shown in the tag editor and
 *  [editedTags] the confirmed tags. Only the diff between the two is stored, so tags that were added
 *  to the node in the meantime are kept, and concurrent changes of the same tag lead to a conflict
 *  when the edit is applied instead of being overwritten.
 *
 *  Returns null if nothing would change or the target does not match. */
fun createInsertNodeAction(
    positionOnWay: PositionOnWay,
    shownVertex: Node?,
    editedTags: Map<String, String>,
    getWayIdsForNode: (Long) -> List<Long>,
): ElementEditAction? = when (positionOnWay) {
    is VertexOfWay -> {
        if (shownVertex == null || shownVertex.id != positionOnWay.nodeId) {
            null
        } else {
            val changes = editedTags.createChanges(shownVertex.tags).create()
            if (changes.isEmpty()) null
            else CreateNodeFromVertexAction(shownVertex, changes, getWayIdsForNode(shownVertex.id))
        }
    }
    is PositionOnWaySegment -> CreateNodeAction(
        positionOnWay.position,
        editedTags,
        listOf(InsertIntoWayAt(positionOnWay.wayId, positionOnWay.segment.first, positionOnWay.segment.second))
    )
    is PositionOnWaysSegment -> CreateNodeAction(positionOnWay.position, editedTags, positionOnWay.insertIntoWaysAt)
    is PositionOnCrossingWaySegments -> CreateNodeAction(positionOnWay.position, editedTags, positionOnWay.insertIntoWaysAt)
}

fun createNodeAction(
    positionOnWay: PositionOnWay,
    mapDataWithEditsSource: MapDataWithEditsSource,
    createChanges: (StringMapChangesBuilder) -> Unit
): ElementEditAction? {
    when (positionOnWay) {
        is PositionOnWaySegment -> {
            val tagChanges = StringMapChangesBuilder(mapOf())
            createChanges(tagChanges)
            val insertIntoWayAt = InsertIntoWayAt(
                positionOnWay.wayId,
                positionOnWay.segment.first,
                positionOnWay.segment.second
            )
            return CreateNodeAction(positionOnWay.position, tagChanges, listOf(insertIntoWayAt))
        }
        is VertexOfWay -> {
            val node = mapDataWithEditsSource.getNode(positionOnWay.nodeId) ?: return null
            val tagChanges = StringMapChangesBuilder(node.tags)
            createChanges(tagChanges)
            val containingWayIds = mapDataWithEditsSource.getWaysForNode(positionOnWay.nodeId).map { it.id }
            return CreateNodeFromVertexAction(node, tagChanges.create(), containingWayIds)
        }
        is PositionOnWaysSegment -> {
            val tagChanges = StringMapChangesBuilder(mapOf())
            createChanges(tagChanges)
            return CreateNodeAction(positionOnWay.position, tagChanges, positionOnWay.insertIntoWaysAt)
        }
        is PositionOnCrossingWaySegments -> {
            val tagChanges = StringMapChangesBuilder(mapOf())
            createChanges(tagChanges)
            return CreateNodeAction(positionOnWay.position, tagChanges, positionOnWay.insertIntoWaysAt)
        }
    }
}
