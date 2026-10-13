package de.westnordost.streetcomplete.data.osm.mapdata

/** Optional local bulk read. The result must equal the union of the individual area reads,
 * including complete way nodes, parent relations and the current local edits. */
internal interface MapDataAreasSource {
    fun getMapDataForAreas(areas: List<BoundingBox>): MutableMapDataWithGeometry
}
