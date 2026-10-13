package de.westnordost.streetcomplete.quests.bicycle_in_pedestrian_street

import de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street.BicycleInPedestrianStreet
import kotlinx.serialization.Serializable

@Serializable
sealed interface BicycleInPedestrianStreetAnswer {
    @Serializable
    data class Sign(val value: BicycleInPedestrianStreet) : BicycleInPedestrianStreetAnswer

    @Serializable
    data class Conditional(val value: String) : BicycleInPedestrianStreetAnswer
}
