package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import de.westnordost.streetcomplete.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/** return fitting vehicle type emoji that corresponds to the "usable by" column in the wiki */
val Smoothness.icon: DrawableResource get() = when (this) {
    EXCELLENT ->     Res.drawable.smoothness_skateboard
    GOOD ->          Res.drawable.smoothness_scooter
    INTERMEDIATE ->  Res.drawable.smoothness_city_bike
    BAD ->           Res.drawable.smoothness_car
    VERY_BAD ->      Res.drawable.smoothness_suv
    HORRIBLE ->      Res.drawable.smoothness_pickup_truck
    VERY_HORRIBLE -> Res.drawable.smoothness_tractor
    IMPASSABLE ->    Res.drawable.smoothness_pedestrian
}

val Smoothness.emoji: String get() = when (this) {
    EXCELLENT ->     "🛹"
    GOOD ->          "🛴"
    INTERMEDIATE ->  "🚲"
    BAD ->           "🚗"
    VERY_BAD ->      "🚙"
    HORRIBLE ->      "🛻"
    VERY_HORRIBLE -> "🚜"
    IMPASSABLE ->    "🚶"
}

val Smoothness.title: StringResource get() = when (this) {
    EXCELLENT ->     Res.string.quest_smoothness_title_excellent
    GOOD ->          Res.string.quest_smoothness_title_good
    INTERMEDIATE ->  Res.string.quest_smoothness_title_intermediate
    BAD ->           Res.string.quest_smoothness_title_bad
    VERY_BAD ->      Res.string.quest_smoothness_title_very_bad
    HORRIBLE ->      Res.string.quest_smoothness_title_horrible
    VERY_HORRIBLE -> Res.string.quest_smoothness_title_very_horrible
    IMPASSABLE ->    Res.string.quest_smoothness_title_impassable
}

fun parseSmoothness(value: String?): Smoothness? = Smoothness.entries.find { it.osmValue == value }

/* ------------------------------------------------------------------------------------------- *
 * Shared surface × smoothness catalogue, used by all smoothness quests and the smoothness
 * overlay. Photos are only shown for the surface they depict; a photo of another surface is
 * explicitly marked via [SmoothnessPhoto.similarSurface]. Without a photo, a neutral placeholder
 * is shown.
 * ------------------------------------------------------------------------------------------- */

/** Example photo for a smoothness value. [similarSurface] is set when the photo shows another
 *  (similar) surface than the one asked for, so the UI can say so. */
data class SmoothnessPhoto(val image: DrawableResource, val similarSurface: String? = null)

data class SmoothnessChoice(
    val value: Smoothness,
    /** null: no suitable photo available, a neutral placeholder is shown */
    val photo: SmoothnessPhoto?,
    val description: StringResource?,
)

private class SmoothnessProfile(
    val values: List<Smoothness>,
    val photos: Map<Smoothness, SmoothnessPhoto> = emptyMap(),
    val descriptions: Map<Smoothness, StringResource> = emptyMap(),
)

private fun range(from: Smoothness, to: Smoothness) = Smoothness.entries.filter { it in from..to }

private fun Map<Smoothness, DrawableResource>.photos(similarSurface: String? = null) =
    mapValues { SmoothnessPhoto(it.value, similarSurface) }

private val asphaltPhotos = mapOf(
    EXCELLENT to Res.drawable.surface_asphalt_excellent,
    GOOD to Res.drawable.surface_asphalt_good,
    INTERMEDIATE to Res.drawable.surface_asphalt_intermediate,
    BAD to Res.drawable.surface_asphalt_bad,
    VERY_BAD to Res.drawable.surface_asphalt_very_bad,
)

private val concretePhotos = mapOf(
    EXCELLENT to Res.drawable.surface_concrete_excellent,
    GOOD to Res.drawable.surface_concrete_good,
    INTERMEDIATE to Res.drawable.surface_concrete_intermediate,
    BAD to Res.drawable.surface_concrete_bad,
    VERY_BAD to Res.drawable.surface_concrete_very_bad,
)

private val settPhotos = mapOf(
    GOOD to Res.drawable.surface_sett_good,
    INTERMEDIATE to Res.drawable.surface_sett_intermediate,
    BAD to Res.drawable.surface_sett_bad,
    VERY_BAD to Res.drawable.surface_sett_very_bad,
)

private val pavingStonesPhotos = mapOf(
    EXCELLENT to Res.drawable.surface_paving_stones_excellent,
    GOOD to Res.drawable.surface_paving_stones_good,
    INTERMEDIATE to Res.drawable.surface_paving_stones_intermediate,
    BAD to Res.drawable.surface_paving_stones_bad,
    VERY_BAD to Res.drawable.surface_paving_stones_very_bad,
)

private val compactedPhotos = mapOf(
    GOOD to Res.drawable.surface_compacted_good,
    INTERMEDIATE to Res.drawable.surface_compacted_intermediate,
    BAD to Res.drawable.surface_compacted_bad,
    VERY_BAD to Res.drawable.surface_compacted_very_bad,
)

private val gravelPhotos = mapOf(
    INTERMEDIATE to Res.drawable.surface_gravel_intermediate,
    BAD to Res.drawable.surface_gravel_bad,
    VERY_BAD to Res.drawable.surface_gravel_very_bad,
)

/* The shared photos for the worst unpaved values show ground (horrible), dirt (very horrible)
   and an unspecified unpaved track (impassable) */
private val unpavedHorrible = SmoothnessPhoto(Res.drawable.surface_unpaved_horrible, "ground")
private val unpavedVeryHorrible = SmoothnessPhoto(Res.drawable.surface_unpaved_very_horrible, "dirt")
private val unpavedImpassable = SmoothnessPhoto(Res.drawable.surface_unpaved_impassable, "unpaved")

private val worstUnpavedPhotos = mapOf(
    HORRIBLE to unpavedHorrible,
    VERY_HORRIBLE to unpavedVeryHorrible,
    IMPASSABLE to unpavedImpassable,
)

/** Drop the similar-surface marker for photos that do show [surface] */
private fun Map<Smoothness, SmoothnessPhoto>.ownedBy(surface: String) =
    mapValues { (_, p) -> if (p.similarSurface == surface) p.copy(similarSurface = null) else p }

private val pavedDescriptions = mapOf(
    EXCELLENT to Res.string.quest_smoothness_description_excellent_paved,
    GOOD to Res.string.quest_smoothness_description_good_paved,
    INTERMEDIATE to Res.string.quest_smoothness_description_intermediate_paved,
    BAD to Res.string.quest_smoothness_description_bad_paved,
    VERY_BAD to Res.string.quest_smoothness_description_very_bad_paved,
)

private val settDescriptions = mapOf(
    GOOD to Res.string.quest_smoothness_description_good_sett,
    INTERMEDIATE to Res.string.quest_smoothness_description_intermediate_sett,
    BAD to Res.string.quest_smoothness_description_bad_sett,
    VERY_BAD to Res.string.quest_smoothness_description_very_bad_sett,
)

private val pavingStonesDescriptions = mapOf(
    EXCELLENT to Res.string.quest_smoothness_description_excellent_paving_stones,
    GOOD to Res.string.quest_smoothness_description_good_paving_stones,
    INTERMEDIATE to Res.string.quest_smoothness_description_intermediate_paving_stones,
    BAD to Res.string.quest_smoothness_description_bad_paving_stones,
    VERY_BAD to Res.string.quest_smoothness_description_very_bad_paving_stones,
)

private val compactedOrGravelDescriptions = mapOf(
    GOOD to Res.string.quest_smoothness_description_good_compacted_gravel,
    INTERMEDIATE to Res.string.quest_smoothness_description_intermediate_compacted_gravel,
    BAD to Res.string.quest_smoothness_description_bad_compacted_gravel,
    VERY_BAD to Res.string.quest_smoothness_description_very_bad_compacted_gravel,
)

private val unhewnCobblestoneDescriptions = mapOf(
    GOOD to Res.string.smoothness_description_good_unhewn_cobblestone,
    INTERMEDIATE to Res.string.smoothness_description_intermediate_unhewn_cobblestone,
    BAD to Res.string.smoothness_description_bad_unhewn_cobblestone,
    VERY_BAD to Res.string.smoothness_description_very_bad_unhewn_cobblestone,
)

private val grassPaverDescriptions = mapOf(
    EXCELLENT to Res.string.smoothness_description_excellent_grass_paver,
    GOOD to Res.string.smoothness_description_good_grass_paver,
    INTERMEDIATE to Res.string.smoothness_description_intermediate_grass_paver,
    BAD to Res.string.smoothness_description_bad_grass_paver,
    VERY_BAD to Res.string.smoothness_description_very_bad_grass_paver,
)

private val woodDescriptions = mapOf(
    GOOD to Res.string.smoothness_description_good_wood,
    INTERMEDIATE to Res.string.smoothness_description_intermediate_wood,
    BAD to Res.string.smoothness_description_bad_wood,
    VERY_BAD to Res.string.smoothness_description_very_bad_wood,
)

private val metalDescriptions = mapOf(
    GOOD to Res.string.smoothness_description_good_metal,
    INTERMEDIATE to Res.string.smoothness_description_intermediate_metal,
    BAD to Res.string.smoothness_description_bad_metal,
)

private val naturalDescriptions = mapOf(
    INTERMEDIATE to Res.string.smoothness_description_intermediate_natural,
    BAD to Res.string.smoothness_description_bad_natural,
    VERY_BAD to Res.string.smoothness_description_very_bad_natural,
)

private val Smoothness.descriptionFallback: StringResource? get() = when (this) {
    HORRIBLE -> Res.string.quest_smoothness_description_horrible
    VERY_HORRIBLE -> Res.string.quest_smoothness_description_very_horrible
    IMPASSABLE -> Res.string.quest_smoothness_description_impassable
    else -> null
}

private fun natural(surface: String, from: Smoothness, photos: Map<Smoothness, SmoothnessPhoto> = emptyMap()) =
    SmoothnessProfile(range(from, IMPASSABLE), photos.ownedBy(surface), naturalDescriptions)

private val PROFILES: Map<String, SmoothnessProfile> = mapOf(
    // paved
    "asphalt" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), asphaltPhotos.photos(), pavedDescriptions),
    "chipseal" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), asphaltPhotos.photos("asphalt"), pavedDescriptions),
    "concrete" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), concretePhotos.photos(), pavedDescriptions),
    // two strips of concrete look nothing like full-width concrete
    "concrete:lanes" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), emptyMap(), pavedDescriptions),
    "paving_stones" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), pavingStonesPhotos.photos(), pavingStonesDescriptions),
    "sett" to SmoothnessProfile(range(GOOD, VERY_BAD), settPhotos.photos(), settDescriptions),
    "unhewn_cobblestone" to SmoothnessProfile(range(GOOD, VERY_BAD), mapOf(
        BAD to Res.drawable.surface_unhewn_cobblestone_bad,
        VERY_BAD to Res.drawable.surface_unhewn_cobblestone_very_bad,
    ).photos(), unhewnCobblestoneDescriptions),
    "grass_paver" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), mapOf(
        INTERMEDIATE to Res.drawable.surface_grass_paver_intermediate,
        BAD to Res.drawable.surface_grass_paver_bad,
        VERY_BAD to Res.drawable.surface_grass_paver_very_bad,
    ).photos(), grassPaverDescriptions),
    "wood" to SmoothnessProfile(range(GOOD, VERY_BAD), mapOf(
        GOOD to Res.drawable.surface_wood_good,
        INTERMEDIATE to Res.drawable.surface_wood_intermediate,
        BAD to Res.drawable.surface_wood_bad,
        VERY_BAD to Res.drawable.surface_wood_very_bad,
    ).photos(), woodDescriptions),
    "metal" to SmoothnessProfile(range(GOOD, BAD), mapOf(
        GOOD to Res.drawable.surface_metal_good,
        INTERMEDIATE to Res.drawable.surface_metal_intermediate,
    ).photos(), metalDescriptions),
    "metal_grid" to SmoothnessProfile(range(GOOD, BAD), emptyMap(), metalDescriptions),
    "paved" to SmoothnessProfile(range(EXCELLENT, VERY_BAD), emptyMap(), pavedDescriptions),
    // unpaved
    "compacted" to SmoothnessProfile(range(GOOD, IMPASSABLE), compactedPhotos.photos() + worstUnpavedPhotos, compactedOrGravelDescriptions),
    "gravel" to SmoothnessProfile(range(INTERMEDIATE, IMPASSABLE), gravelPhotos.photos() + worstUnpavedPhotos, compactedOrGravelDescriptions),
    "fine_gravel" to SmoothnessProfile(
        range(INTERMEDIATE, IMPASSABLE),
        gravelPhotos.photos("gravel") + worstUnpavedPhotos +
            (INTERMEDIATE to SmoothnessPhoto(Res.drawable.surface_fine_gravel_intermediate)),
        compactedOrGravelDescriptions
    ),
    "pebblestone" to SmoothnessProfile(range(INTERMEDIATE, IMPASSABLE), gravelPhotos.photos("gravel") + worstUnpavedPhotos, compactedOrGravelDescriptions),
    "woodchips" to SmoothnessProfile(range(INTERMEDIATE, VERY_BAD), mapOf(
        INTERMEDIATE to Res.drawable.surface_woodchips_intermediate,
    ).photos(), naturalDescriptions),
    "unpaved" to SmoothnessProfile(range(INTERMEDIATE, IMPASSABLE)),
    // natural
    "ground" to natural("ground", INTERMEDIATE, mapOf(
        INTERMEDIATE to SmoothnessPhoto(Res.drawable.surface_ground_intermediate),
        BAD to SmoothnessPhoto(Res.drawable.surface_ground_bad),
        VERY_BAD to SmoothnessPhoto(Res.drawable.surface_compacted_very_bad), // shows a ground track
        HORRIBLE to unpavedHorrible,
        VERY_HORRIBLE to unpavedVeryHorrible,
        IMPASSABLE to unpavedImpassable,
    )),
    "dirt" to natural("dirt", INTERMEDIATE, mapOf(
        INTERMEDIATE to SmoothnessPhoto(Res.drawable.surface_dirt_intermediate),
        BAD to SmoothnessPhoto(Res.drawable.surface_dirt_bad),
        VERY_BAD to SmoothnessPhoto(Res.drawable.surface_dirt_very_bad),
        HORRIBLE to SmoothnessPhoto(Res.drawable.surface_dirt_horrible),
        VERY_HORRIBLE to unpavedVeryHorrible,
        IMPASSABLE to unpavedImpassable,
    )),
    "laterite" to natural("laterite", INTERMEDIATE),
    "grass" to natural("grass", INTERMEDIATE, mapOf(
        INTERMEDIATE to SmoothnessPhoto(Res.drawable.surface_grass_intermediate),
        HORRIBLE to SmoothnessPhoto(Res.drawable.surface_grass_horrible),
    )),
    "mud" to natural("mud", VERY_BAD, mapOf(
        VERY_BAD to SmoothnessPhoto(Res.drawable.surface_mud_very_bad),
        VERY_HORRIBLE to unpavedVeryHorrible,
    )),
    "sand" to natural("sand", BAD, mapOf(
        BAD to SmoothnessPhoto(Res.drawable.surface_sand_bad),
        VERY_BAD to SmoothnessPhoto(Res.drawable.surface_sand_very_bad),
    )),
    "rock" to natural("rock", BAD),
)

/** Tag values that are displayed with the profile of another value. */
private val SMOOTHNESS_SURFACE_ALIASES = mapOf(
    "concrete:plates" to "concrete",
    "bricks" to "paving_stones",
    "brick" to "paving_stones",
    "earth" to "dirt",
    "soil" to "dirt",
)

/** Used for unknown, missing and special-purpose surfaces (stepping stones, sport surfaces, …):
 *  all values, no photos, no surface-specific descriptions. */
private val GENERIC_PROFILE = SmoothnessProfile(Smoothness.entries)

private fun profileOf(surface: String?): SmoothnessProfile =
    PROFILES[SMOOTHNESS_SURFACE_ALIASES[surface] ?: surface] ?: GENERIC_PROFILE

/** Whether [surface] has its own smoothness profile. If not, a generic selection is used. */
fun hasSmoothnessProfile(surface: String?): Boolean =
    PROFILES.containsKey(SMOOTHNESS_SURFACE_ALIASES[surface] ?: surface)

/** Example and description for [value] on [surface], also if [value] is not usually offered. */
fun smoothnessChoice(value: Smoothness, surface: String?): SmoothnessChoice {
    val profile = profileOf(surface)
    return SmoothnessChoice(value, profile.photos[value], profile.descriptions[value] ?: value.descriptionFallback)
}

/** The smoothness values usually offered for [surface] */
fun smoothnessChoices(surface: String?): List<SmoothnessChoice> =
    profileOf(surface).values.map { smoothnessChoice(it, surface) }

/** Like [smoothnessChoices], but always includes the [existing] value so that it stays
 *  selectable even if it is not usually offered for the surface. */
fun selectableSmoothnessChoices(surface: String?, existing: Smoothness?): List<SmoothnessChoice> {
    val choices = smoothnessChoices(surface)
    if (existing == null || choices.any { it.value == existing }) return choices
    return (choices + smoothnessChoice(existing, surface)).sortedBy { it.value.ordinal }
}

fun smoothnessAnswersForSurface(surface: String?): List<Smoothness> =
    profileOf(surface).values

/** Smoothness choices governed by the surface stored under [surfaceKey], e.g. `cycleway:surface`. */
fun smoothnessAnswersForSurfaceKey(tags: Map<String, String>, surfaceKey: String = "surface"): List<Smoothness> =
    smoothnessAnswersForSurface(tags[surfaceKey])
