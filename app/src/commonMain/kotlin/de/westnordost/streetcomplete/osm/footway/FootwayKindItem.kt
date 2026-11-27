package de.westnordost.streetcomplete.osm.footway

import de.westnordost.streetcomplete.osm.footway.FootwayKind.*
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

val FootwayKind.title: StringResource get() = when (this) {
    SIDEWALK ->       Res.string.footway_kind_sidewalk
    CROSSING ->       Res.string.footway_kind_crossing
    TRAFFIC_ISLAND -> Res.string.footway_kind_traffic_island
    ACCESS_AISLE ->   Res.string.footway_kind_access_aisle
    UNSPECIFIED ->    Res.string.footway_kind_unspecified
    UNSUPPORTED ->    Res.string.footway_kind_unsupported
}

/** There are no dedicated illustrations for most kinds. Existing crossing and island icons show
 *  zebra markings and access aisle candidates show wheelchairs, which would suggest properties
 *  that footway=* does not state. So, a neutral pedestrian symbol is used instead, the label
 *  carries the meaning. */
val FootwayKind.icon: DrawableResource? get() = when (this) {
    SIDEWALK ->       Res.drawable.quest_sidewalk
    CROSSING, TRAFFIC_ISLAND, ACCESS_AISLE -> Res.drawable.quest_pedestrian
    UNSPECIFIED ->    Res.drawable.separate_cycleway_no
    UNSUPPORTED ->    null
}
