package de.westnordost.streetcomplete.data.osm.osmquests

import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.quests.fire_hydrant_diameter.AddFireHydrantDiameter
import de.westnordost.streetcomplete.quests.police_type.AddPoliceType
import de.westnordost.streetcomplete.quests.sidepath_usage.AddSidepathUsage
import de.westnordost.streetcomplete.util.countryboundaries.AllCountries
import de.westnordost.streetcomplete.util.countryboundaries.Countries
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CountryRestrictionOverrideTest {

    private val restricted = NoCountriesExcept("DE")

    private val overridable = object : TestQuestTypeA() {
        override val enabledInCountries: Countries get() = restricted
        override val isCountryRestrictionOverridable get() = true
    }
    private val notOverridable = object : TestQuestTypeA() {
        override val enabledInCountries: Countries get() = restricted
    }

    @Test fun `override is only active in expert mode with the setting enabled`() {
        assertTrue(CountryRestrictionOverride.isActive(expertMode = true, overrideEnabled = true))
        assertFalse(CountryRestrictionOverride.isActive(expertMode = true, overrideEnabled = false))
        assertFalse(CountryRestrictionOverride.isActive(expertMode = false, overrideEnabled = true))
        assertFalse(CountryRestrictionOverride.isActive(expertMode = false, overrideEnabled = false))
    }

    @Test fun `inactive override keeps restrictions`() {
        assertEquals(restricted, CountryRestrictionOverride.effectiveCountries(overridable, false))
        assertEquals(restricted, CountryRestrictionOverride.effectiveCountries(notOverridable, false))
    }

    @Test fun `active override lifts restriction only for overridable quest types`() {
        assertEquals(AllCountries, CountryRestrictionOverride.effectiveCountries(overridable, true))
        assertEquals(restricted, CountryRestrictionOverride.effectiveCountries(notOverridable, true))
    }

    @Test fun `quest types are not overridable by default`() {
        assertFalse(TestQuestTypeA().isCountryRestrictionOverridable)
    }

    @Test fun `quests with country specific semantics are explicitly not overridable`() {
        // hydrant diameter: community decision against AT, units; police type: Italy only;
        // sidepath usage: legal prerequisite of DE/NL
        assertFalse(AddFireHydrantDiameter().isCountryRestrictionOverridable)
        assertFalse(AddPoliceType().isCountryRestrictionOverridable)
        assertFalse(AddSidepathUsage().isCountryRestrictionOverridable)
    }
}
