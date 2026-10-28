package de.westnordost.streetcomplete.overlays.restriction

import de.westnordost.streetcomplete.quests.max_weight.MaxAxleLoad
import de.westnordost.streetcomplete.quests.max_weight.MaxTandemAxleLoad
import de.westnordost.streetcomplete.quests.max_weight.MaxWeight
import de.westnordost.streetcomplete.quests.max_weight.MaxWeightRating
import de.westnordost.streetcomplete.quests.max_weight.MaxWeightRatingHgv
import de.westnordost.streetcomplete.quests.max_weight.Weight
import de.westnordost.streetcomplete.quests.max_weight.getIcon
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.DrawableResource
import de.westnordost.streetcomplete.quests.max_weight.WeightRestriction as MaxWeightRestriction

/** Kind of weight limit edited in the restriction overlay. StreetComplete replaced its former
 *  `MaxWeightType` with [MaxWeightRestriction] (#7298); the overlay keeps its own type and maps to
 *  the quest's model for icons and the sign form. The Swedish weight categories are not offered
 *  here. */
@Serializable
enum class MaxWeightType {
    MAX_WEIGHT,
    MAX_WEIGHT_RATING,
    MAX_WEIGHT_RATING_HGV,
    MAX_AXLE_LOAD,
    MAX_TANDEM_AXLE_LOAD,
}

val MaxWeightType.osmKey: String get() = when (this) {
    MaxWeightType.MAX_WEIGHT            -> "maxweight"
    MaxWeightType.MAX_WEIGHT_RATING     -> "maxweightrating"
    MaxWeightType.MAX_WEIGHT_RATING_HGV -> "maxweightrating:hgv"
    MaxWeightType.MAX_AXLE_LOAD         -> "maxaxleload"
    MaxWeightType.MAX_TANDEM_AXLE_LOAD  -> "maxbogieweight"
}

fun MaxWeightType.toMaxWeightRestriction(weight: Weight?): MaxWeightRestriction = when (this) {
    MaxWeightType.MAX_WEIGHT            -> MaxWeight(weight)
    MaxWeightType.MAX_WEIGHT_RATING     -> MaxWeightRating(weight)
    MaxWeightType.MAX_WEIGHT_RATING_HGV -> MaxWeightRatingHgv(weight)
    MaxWeightType.MAX_AXLE_LOAD         -> MaxAxleLoad(weight)
    MaxWeightType.MAX_TANDEM_AXLE_LOAD  -> MaxTandemAxleLoad(weight)
}

/** Sign icon in the given country, or null if this kind of sign does not exist there */
fun MaxWeightType.getIcon(countryCode: String): DrawableResource? {
    val restriction = toMaxWeightRestriction(null)
    val isKnown = MaxWeightRestriction.getSelectableEntries(countryCode).any { it::class == restriction::class }
    return if (isKnown) restriction.getIcon(countryCode) else null
}
