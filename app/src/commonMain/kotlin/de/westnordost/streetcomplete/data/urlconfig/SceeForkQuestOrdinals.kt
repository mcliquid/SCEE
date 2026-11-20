package de.westnordost.streetcomplete.data.urlconfig

import de.westnordost.streetcomplete.ApplicationConstants

/**
 * SCEE-mc used ordinal 203 for [de.westnordost.streetcomplete.quests.bike_parking_capacity.AddBikeParkingCargoCapacity]
 * until upstream #7268 reassigned 203 to [de.westnordost.streetcomplete.quests.cycleway.AddCyclewayDirection].
 *
 * Preset visibility, hidden quests and prefs keys use quest class names and are migrated via [renamedQuests].
 * URL configs encode ordinals only: upstream StreetComplete URLs with bit 203 mean AddCyclewayDirection (unchanged).
 *
 * Legacy SCEE-mc share URLs that only enabled cargo at ordinal 203 cannot be distinguished from upstream cycleway URLs.
 * Re-export presets after upgrade or enable cargo manually at ordinal [CARGO_CAPACITY_ORDINAL].
 */
const val LEGACY_SCEE_CARGO_CAPACITY_ORDINAL = 203
const val CARGO_CAPACITY_ORDINAL = ApplicationConstants.EE_QUEST_OFFSET + 214

/**
 * Optional remap for known legacy SCEE-mc-only URLs (not used for upstream StreetComplete links):
 * enables cargo at [CARGO_CAPACITY_ORDINAL] without removing [LEGACY_SCEE_CARGO_CAPACITY_ORDINAL] (cycleway).
 */
fun Ordinals.withSceeLegacyCargoOrdinalDuplicated(): Ordinals {
    if (LEGACY_SCEE_CARGO_CAPACITY_ORDINAL !in this) return this
    return Ordinals(this + CARGO_CAPACITY_ORDINAL)
}