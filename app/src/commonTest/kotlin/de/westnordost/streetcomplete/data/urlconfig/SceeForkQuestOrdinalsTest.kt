package de.westnordost.streetcomplete.data.urlconfig

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.quests.bike_parking_capacity.AddBikeParkingCargoCapacity
import de.westnordost.streetcomplete.quests.cycleway.AddCyclewayDirection
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.getQuestTypeList
import de.westnordost.streetcomplete.quests.osmose.OsmoseDao
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceeForkQuestOrdinalsTest {
    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }
    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    private fun withRegistry(block: (QuestTypeRegistry) -> Unit) {
        Prefs.preferences = inMemoryPrefs()
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            val database = DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) }
            val reg = QuestTypeRegistry(
                load = {
                    getQuestTypeList(
                        arSupportChecker = arSupportChecker,
                        getCountryInfoByLocation = { countryInfo },
                        getCountryOrSubdivisionCode = { null },
                        getFeature = { null },
                        osmoseDao = OsmoseDao(database, Prefs.preferences),
                        customQuestList = CustomQuestList(),
                    )
                }
            )
            block(reg)
        } finally {
            connection.close()
        }
    }

    @Test fun `cycleway direction uses upstream ordinal 203`() {
        withRegistry { reg ->
            assertEquals(203, reg.getOrdinalOf(reg.first { it is AddCyclewayDirection }))
        }
    }

    @Test fun `cargo capacity moved to fork ordinal`() {
        withRegistry { reg ->
            assertEquals(
                ApplicationConstants.EE_QUEST_OFFSET + 214,
                reg.getOrdinalOf(reg.first { it is AddBikeParkingCargoCapacity }),
            )
        }
    }

    @Test fun `legacy cargo ordinal helper duplicates bit without dropping cycleway slot`() {
        val mapped = Ordinals(setOf(LEGACY_SCEE_CARGO_CAPACITY_ORDINAL)).withSceeLegacyCargoOrdinalDuplicated()
        assertTrue(CARGO_CAPACITY_ORDINAL in mapped)
        assertTrue(LEGACY_SCEE_CARGO_CAPACITY_ORDINAL in mapped)
    }
}