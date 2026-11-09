package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.osm.FILTER_FOOT_OR_BICYCLE_ACCESSIBLE
import de.westnordost.streetcomplete.osm.Tags

/** Shared highway/path tag shape for eligible sidepath ways (classification + completion). */
private const val SIDEPATH_ELIGIBLE_PATH_SHAPE = """
      (
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
"""

/** Tag filter for paths without [is_sidepath] and for yes-sidepaths missing road association. */
internal val ADD_IS_SIDEPATH_ELEMENT_FILTER = """
    ways with
      area != yes
      and ($FILTER_FOOT_OR_BICYCLE_ACCESSIBLE)
      and (
        (
          !is_sidepath
          and (
            (
              highway = cycleway
              and cycleway !~ sidewalk|link|sidepath|crossing
            )
            or
            (
              highway = footway
              and footway !~ sidewalk|link|crossing
            )
            or
            (
              highway = path
              and (bicycle or foot)
              and path !~ sidewalk|link|crossing|sidepath
            )
          )
        )
        or
        (
          is_sidepath = yes
          and (!is_sidepath:of or (!is_sidepath:of:name and !is_sidepath:of:ref))
          and $SIDEPATH_ELIGIBLE_PATH_SHAPE
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

/**
 * Explicit road selection from the quest form: rewrite association tags as one unit.
 * Optional name/ref are cleared when the chosen road provides none.
 */
internal fun applySidepathRoadAssociationFromSelection(
    tags: Tags,
    ofName: String?,
    ofHighway: String,
    ofRef: String?,
) {
    if (tags["is_sidepath:of"] != ofHighway) {
        tags["is_sidepath:of"] = ofHighway
    }
    if (!ofName.isNullOrEmpty()) {
        if (tags["is_sidepath:of:name"] != ofName) {
            tags["is_sidepath:of:name"] = ofName
        }
    } else if (tags.containsKey("is_sidepath:of:name")) {
        tags.remove("is_sidepath:of:name")
    }
    if (!ofRef.isNullOrEmpty()) {
        if (tags["is_sidepath:of:ref"] != ofRef) {
            tags["is_sidepath:of:ref"] = ofRef
        }
    } else if (tags.containsKey("is_sidepath:of:ref")) {
        tags.remove("is_sidepath:of:ref")
    }
}

internal fun sidepathAssociationTagsMatchSelection(
    tags: Map<String, String>,
    ofName: String?,
    ofHighway: String?,
    ofRef: String?,
): Boolean {
    if (ofHighway.isNullOrEmpty()) return false
    if (tags["is_sidepath:of"] != ofHighway) return false
    val taggedName = tags["is_sidepath:of:name"]
    val taggedRef = tags["is_sidepath:of:ref"]
    val nameMatches = (taggedName.isNullOrEmpty() && ofName.isNullOrEmpty()) || taggedName == ofName
    val refMatches = (taggedRef.isNullOrEmpty() && ofRef.isNullOrEmpty()) || taggedRef == ofRef
    return nameMatches && refMatches
}

/** Explicit correction: not a sidepath; same association cleanup as [AddIsSidepath] “No”. */
internal fun applySidepathNotSidepath(tags: Tags) {
    tags["is_sidepath"] = "no"
    tags.remove("is_sidepath:of")
    tags.remove("is_sidepath:of:name")
    tags.remove("is_sidepath:of:ref")
}
