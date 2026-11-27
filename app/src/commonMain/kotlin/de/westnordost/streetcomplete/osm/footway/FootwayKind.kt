package de.westnordost.streetcomplete.osm.footway

import de.westnordost.streetcomplete.osm.Tags
import kotlinx.serialization.Serializable

/** Kind of a separately mapped footway (highway=footway), i.e. the footway=* tag.
 *  See https://wiki.openstreetmap.org/wiki/Key:footway */
@Serializable
enum class FootwayKind(val osmValue: String?) {
    SIDEWALK("sidewalk"),
    CROSSING("crossing"),
    TRAFFIC_ISLAND("traffic_island"),
    ACCESS_AISLE("access_aisle"),
    /** footway=* is not set. This is a valid, neutral state: a footway does not need to be any of
     *  the above. Selecting it explicitly removes footway=* */
    UNSPECIFIED(null),
    /** footway=* has a value that can not be selected in this app (e.g. link, alley, lane or an
     *  invalid value). It is displayed but must never be written */
    UNSUPPORTED(null);

    companion object {
        /** the kinds that can be selected. Not offered: footway=link, as its use for connections
         *  between sidewalk and crossing is disputed (footway=sidewalk + crossing_link=yes is
         *  documented for that instead), and footway=alley/lane etc. */
        val selectable: List<FootwayKind> = listOf(SIDEWALK, CROSSING, TRAFFIC_ISLAND, ACCESS_AISLE, UNSPECIFIED)
    }
}

/** Returns the kind of footway or null if the element is not a highway=footway */
fun parseFootwayKind(tags: Map<String, String>): FootwayKind? {
    if (tags["highway"] != "footway") return null
    val footway = tags["footway"] ?: return FootwayKind.UNSPECIFIED
    return FootwayKind.entries.firstOrNull { it.osmValue == footway } ?: FootwayKind.UNSUPPORTED
}

/** Applies the footway kind to the [tags]. Only footway=* is ever changed. */
fun FootwayKind.applyTo(tags: Tags) {
    when (this) {
        FootwayKind.UNSUPPORTED ->
            throw IllegalArgumentException("Unsupported footway kind can not be applied")
        FootwayKind.UNSPECIFIED ->
            tags.remove("footway")
        else ->
            if (tags["footway"] != osmValue) tags["footway"] = osmValue!!
    }
}
