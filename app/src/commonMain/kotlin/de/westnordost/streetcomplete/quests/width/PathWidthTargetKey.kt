package de.westnordost.streetcomplete.quests.width

import de.westnordost.streetcomplete.osm.Tags

/** Width tag written by [AddCyclewayWidth]; keep [AddCyclewayWidth.elementFilter] in sync. */
internal fun cyclewayWidthTargetKey(tags: Tags): String =
    if (tags["segregated"] == "yes") "cycleway:width" else "width"

/** Width tag written by [AddFootwayWidth]; keep [AddFootwayWidth.elementFilter] in sync. */
internal fun footwayWidthTargetKey(tags: Tags): String {
    val isExclusive =
        tags["highway"] == "footway" && tags["bicycle"] != "yes" && tags["bicycle"] != "designated"
    val isSharedSurfaceFootway =
        tags["highway"] == "footway" && tags["bicycle"] == "yes" && tags["segregated"] != "yes"
    return if (isExclusive || isSharedSurfaceFootway) "width" else "footway:width"
}
