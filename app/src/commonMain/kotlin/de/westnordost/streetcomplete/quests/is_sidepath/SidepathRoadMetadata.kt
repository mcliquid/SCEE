package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.osm.FILTER_FOOT_OR_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags

/** Tag filter for sidepaths that still need road association metadata. */
internal val SIDEPATH_ROAD_METADATA_COMPLETION_FILTER = """
    ways with
      is_sidepath = yes
      and area != yes
      and ($FILTER_FOOT_OR_BICYCLE_ACCESSIBLE)
      and (!is_sidepath:of or !is_sidepath:of:name)
      and (
        (
          highway = cycleway
          and cycleway !~ sidewalk|link|sidepath|crossing
        )
        or
        (
          highway = footway
          and footway !~ link|crossing
        )
        or
        (
          highway = path
          and (bicycle or foot)
          and path !~ sidewalk|link|crossing|sidepath
        )
      )
""".trimIndent()

internal fun isSidepathRoadMetadataCompletionEligible(
    element: Element,
    geometry: ElementGeometry,
    mapData: MapDataWithGeometry,
): Boolean {
    val tags = element.tags
    if (tags["is_sidepath"] != "yes") return false
    val missingOf = tags["is_sidepath:of"].isNullOrEmpty()
    val missingName = tags["is_sidepath:of:name"].isNullOrEmpty()
    if (!missingOf && !missingName) return false
    if (!hasNearbyAlignedRoad(geometry, mapData)) return false
    return findCandidateRoads(geometry, mapData).any { candidate ->
        sidepathRoadMetadataAnswerProducesChanges(tags, candidate.name, candidate.highway)
    }
}

/** Whether [applySidepathRoadMetadata] would change anything (mirrors its fill-only rules). */
internal fun sidepathRoadMetadataAnswerProducesChanges(
    tags: Map<String, String>,
    ofName: String?,
    ofHighway: String?,
): Boolean {
    if (tags["is_sidepath:of"].isNullOrEmpty() && !ofHighway.isNullOrEmpty()) return true
    if (tags["is_sidepath:of:name"].isNullOrEmpty() && !ofName.isNullOrEmpty()) return true
    return false
}

/** Fill only missing association tags; never overwrite existing values or touch unrelated keys. */
internal fun applySidepathRoadMetadata(tags: Tags, ofName: String?, ofHighway: String?) {
    if (tags["is_sidepath:of"].isNullOrEmpty() && !ofHighway.isNullOrEmpty()) {
        tags["is_sidepath:of"] = ofHighway
    }
    if (tags["is_sidepath:of:name"].isNullOrEmpty() && !ofName.isNullOrEmpty()) {
        tags["is_sidepath:of:name"] = ofName
    }
}

/** Explicit correction: not a sidepath; same association cleanup as [AddIsSidepath] “No”. */
internal fun applySidepathNotSidepath(tags: Tags) {
    tags["is_sidepath"] = "no"
    tags.remove("is_sidepath:of")
    tags.remove("is_sidepath:of:name")
    tags.remove("is_sidepath:of:ref")
}
