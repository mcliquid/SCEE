package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.osm.FILTER_FOOT_OR_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags

/** Tag filter for sidepaths that may still need road association metadata. Geometry refines further. */
internal val SIDEPATH_ROAD_METADATA_COMPLETION_FILTER = """
    ways with
      is_sidepath = yes
      and area != yes
      and ($FILTER_FOOT_OR_BICYCLE_ACCESSIBLE)
      and (!is_sidepath:of or (!is_sidepath:of:name and !is_sidepath:of:ref))
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

/**
 * Association is complete when [is_sidepath:of] exists and either an identifier is recorded or no
 * nearby candidate can supply a missing name or ref (see wiki Key:is_sidepath:of*).
 */
internal fun isSidepathRoadMetadataAssociationComplete(
    tags: Map<String, String>,
    geometry: ElementGeometry,
    mapData: MapDataWithGeometry,
): Boolean {
    if (tags["is_sidepath:of"].isNullOrEmpty()) return false
    if (!tags["is_sidepath:of:name"].isNullOrEmpty()) return true
    if (!tags["is_sidepath:of:ref"].isNullOrEmpty()) return true
    val candidates = findCandidateRoads(geometry, mapData)
    return candidates.none { candidate ->
        sidepathRoadMetadataAnswerProducesChanges(
            tags,
            candidate.name,
            candidate.highway,
            candidate.ref,
        )
    }
}

internal fun selectDefaultSidepathRoadCandidate(
    candidates: List<CandidateRoad>,
    roadSelectionProducesChanges: (ofName: String?, ofHighway: String?, ofRef: String?) -> Boolean,
): CandidateRoad {
    return candidates.firstOrNull { roadSelectionProducesChanges(it.name, it.highway, it.ref) }
        ?: candidates.firstOrNull { it.name != null || it.ref != null }
        ?: candidates.first()
}

internal fun isSidepathRoadMetadataCompletionEligible(
    element: Element,
    geometry: ElementGeometry,
    mapData: MapDataWithGeometry,
): Boolean {
    val tags = element.tags
    if (tags["is_sidepath"] != "yes") return false
    if (isSidepathRoadMetadataAssociationComplete(tags, geometry, mapData)) return false
    if (!hasNearbyAlignedRoad(geometry, mapData)) return false
    return findCandidateRoads(geometry, mapData).any { candidate ->
        sidepathRoadMetadataAnswerProducesChanges(
            tags,
            candidate.name,
            candidate.highway,
            candidate.ref,
        )
    }
}

/** Whether [applySidepathRoadMetadata] would change anything (uses the same writer). */
internal fun sidepathRoadMetadataAnswerProducesChanges(
    tags: Map<String, String>,
    ofName: String?,
    ofHighway: String?,
    ofRef: String? = null,
): Boolean {
    val builder = StringMapChangesBuilder(tags)
    applySidepathRoadMetadata(builder, ofName, ofHighway, ofRef)
    return !builder.create().isEmpty()
}

/** Fill only missing association tags; never overwrite existing values or touch unrelated keys. */
internal fun applySidepathRoadMetadata(
    tags: Tags,
    ofName: String?,
    ofHighway: String?,
    ofRef: String?,
) {
    if (tags["is_sidepath:of"].isNullOrEmpty() && !ofHighway.isNullOrEmpty()) {
        tags["is_sidepath:of"] = ofHighway
    }
    if (tags["is_sidepath:of:name"].isNullOrEmpty() && !ofName.isNullOrEmpty()) {
        tags["is_sidepath:of:name"] = ofName
    }
    if (tags["is_sidepath:of:ref"].isNullOrEmpty() && !ofRef.isNullOrEmpty()) {
        tags["is_sidepath:of:ref"] = ofRef
    }
}

/** Explicit correction: not a sidepath; same association cleanup as [AddIsSidepath] “No”. */
internal fun applySidepathNotSidepath(tags: Tags) {
    tags["is_sidepath"] = "no"
    tags.remove("is_sidepath:of")
    tags.remove("is_sidepath:of:name")
    tags.remove("is_sidepath:of:ref")
}
