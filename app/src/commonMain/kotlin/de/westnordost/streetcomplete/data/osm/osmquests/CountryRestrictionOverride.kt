package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.util.countryboundaries.AllCountries
import de.westnordost.streetcomplete.util.countryboundaries.Countries

/** Central place deciding which countries a quest type is effectively enabled in.
 *
 *  The override only replaces the *geographic* restriction [OsmElementQuestType.enabledInCountries]
 *  and only for quest types that opt in with [OsmElementQuestType.isCountryRestrictionOverridable].
 *  Element filters and everything else stay untouched. */
object CountryRestrictionOverride {
    /** The override is only ever active in expert mode, regardless of the stored preference. */
    fun isActive(expertMode: Boolean, overrideEnabled: Boolean): Boolean =
        expertMode && overrideEnabled

    fun effectiveCountries(questType: OsmElementQuestType<*>, overrideActive: Boolean): Countries =
        if (overrideActive && questType.isCountryRestrictionOverridable) AllCountries
        else questType.enabledInCountries
}
