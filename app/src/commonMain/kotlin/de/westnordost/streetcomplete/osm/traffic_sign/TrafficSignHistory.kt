package de.westnordost.streetcomplete.osm.traffic_sign

import kotlinx.serialization.Serializable

/** A combination of signs the user entered, shared by quest and overlay. Both directions are kept
 *  as the traffic_sign values they are tagged as ("" = not surveyed, "none" = no signs), so that
 *  different signs per direction are stored without loss. */
@Serializable
data class TrafficSignCombination(val forward: String, val backward: String) {
    val isSameInBothDirections: Boolean get() = forward == backward

    fun toSelection(catalogue: TrafficSignCatalogue) = WaySignSelection(
        DirectionSelection.decode(forward, catalogue),
        DirectionSelection.decode(backward, catalogue),
    )

    companion object {
        /** null if there are no signs in either direction: only real signs are worth remembering */
        fun of(selection: WaySignSelection): TrafficSignCombination? =
            if (selection.forward is DirectionSelection.Signed || selection.backward is DirectionSelection.Signed) {
                TrafficSignCombination(selection.forward.encode(), selection.backward.encode())
            } else {
                null
            }
    }
}

const val TRAFFIC_SIGN_HISTORY_SIZE = 6

/** [combination] first (most recent), without duplicates, at most [TRAFFIC_SIGN_HISTORY_SIZE] */
fun List<TrafficSignCombination>.withPicked(combination: TrafficSignCombination): List<TrafficSignCombination> =
    (listOf(combination) + filter { it != combination }).take(TRAFFIC_SIGN_HISTORY_SIZE)
