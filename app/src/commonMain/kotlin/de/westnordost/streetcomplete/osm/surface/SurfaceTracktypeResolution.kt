package de.westnordost.streetcomplete.osm.surface

import de.westnordost.streetcomplete.osm.Tags
import de.westnordost.streetcomplete.osm.updateCheckDateForKey
import de.westnordost.streetcomplete.osm.updateWithCheckDate
import kotlinx.serialization.Serializable

fun hasSurfaceTracktypeConflict(surface: String?, tracktype: String?): Boolean {
    if (surface.isNullOrEmpty() || tracktype.isNullOrEmpty()) return false
    return surface in INVALID_SURFACES_FOR_TRACKTYPES[tracktype].orEmpty()
}

fun tagsHaveSurfaceTracktypeConflict(tags: Map<String, String>): Boolean =
    hasSurfaceTracktypeConflict(tags["surface"], tags["tracktype"])

fun surfaceAnswerNeedsConflictDialog(tags: Map<String, String>, surface: Surface): Boolean {
    if (tags["highway"] != "track") return false
    val tracktype = tags["tracktype"] ?: return false
    val surfaceValue = surface.osmValue ?: return false
    return hasSurfaceTracktypeConflict(surfaceValue, tracktype)
}

fun tracktypeAnswerNeedsConflictDialog(tags: Map<String, String>, tracktype: String): Boolean {
    if (tags["highway"] != "track") return false
    val surface = tags["surface"] ?: return false
    return hasSurfaceTracktypeConflict(surface, tracktype)
}

fun resolutionAfterSurfaceAndTracktypeEdit(
    before: Map<String, String>,
    surface: Surface,
    tracktype: String,
): SurfaceTracktypeResolution {
    val surfaceValue = surface.osmValue ?: error("surface must have OSM value")
    check(!hasSurfaceTracktypeConflict(surfaceValue, tracktype)) {
        "resolutionAfterSurfaceAndTracktypeEdit requires a consistent pair"
    }
    val surfaceChanged = before["surface"] != surfaceValue &&
        !(before["surface"] != null && parseSurface(before["surface"]) == surface)
    val tracktypeChanged = before["tracktype"] != tracktype
    return when {
        surfaceChanged && tracktypeChanged -> SurfaceTracktypeResolution.PairedCorrection(surface, tracktype)
        surfaceChanged -> SurfaceTracktypeResolution.MaterialOnly(surface)
        tracktypeChanged -> SurfaceTracktypeResolution.TracktypeOnly(tracktype)
        else -> SurfaceTracktypeResolution.MaterialOnly(surface)
    }
}

@Serializable
sealed interface SurfaceTracktypeResolution {
    @Serializable data class MaterialOnly(val surface: Surface) : SurfaceTracktypeResolution

    @Serializable data class TracktypeOnly(val tracktype: String) : SurfaceTracktypeResolution

    @Serializable data class PairedCorrection(val surface: Surface, val tracktype: String) : SurfaceTracktypeResolution

    /** Both values deliberately kept although the heuristic still flags a conflict. */
    @Serializable data class JustifiedException(val surface: Surface, val tracktype: String) : SurfaceTracktypeResolution

    @Serializable data object Unresolved : SurfaceTracktypeResolution
}

fun SurfaceTracktypeResolution.applyTo(tags: Tags) {
    when (this) {
        is SurfaceTracktypeResolution.MaterialOnly -> surface.applyTo(tags)
        is SurfaceTracktypeResolution.TracktypeOnly -> tags.updateWithCheckDate("tracktype", tracktype)
        is SurfaceTracktypeResolution.PairedCorrection -> {
            surface.applyTo(tags)
            tags.updateWithCheckDate("tracktype", tracktype)
        }
        is SurfaceTracktypeResolution.JustifiedException -> {
            val surfaceValue = surface.osmValue ?: return
            if (tags["surface"] == surfaceValue) {
                tags.updateCheckDateForKey("surface")
            } else {
                surface.applyTo(tags)
            }
            if (tags["tracktype"] == tracktype) {
                tags.updateCheckDateForKey("tracktype")
            } else {
                tags.updateWithCheckDate("tracktype", tracktype)
            }
        }
        SurfaceTracktypeResolution.Unresolved -> Unit
    }
}