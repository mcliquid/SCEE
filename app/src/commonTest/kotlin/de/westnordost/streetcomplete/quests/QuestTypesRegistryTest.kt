package de.westnordost.streetcomplete.quests

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.osmose.OsmoseDao
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import de.westnordost.streetcomplete.quests.cycleway.AddCycleway
import de.westnordost.streetcomplete.quests.sidepath_usage.AddSidepathUsage

class QuestTypesRegistryTest {

    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    @Test
    fun `quest type ordinals are unique in QuestTypeRegistry`() {
        Prefs.preferences = inMemoryPrefs()

        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            val database = DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) }
            val registry = QuestTypeRegistry(
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

            val ordinals = registry.mapNotNull { registry.getOrdinalOf(it) }
            val duplicates = ordinals
                .groupBy { it }
                .filter { it.value.size > 1 }
                .keys
                .sorted()

            assertEquals(emptyList(), duplicates, "Duplicate quest type ordinals: $duplicates")
        } finally {
            connection.close()
        }
    }

    @Test
    fun `AddSidepathUsage directly follows AddCycleway in quest order`() {
        Prefs.preferences = inMemoryPrefs()

        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            val database = DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) }
            val registry = QuestTypeRegistry(
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
            val order = registry.toList()
            val cyclewayIndex = order.indexOfFirst { it is AddCycleway }
            val sidepathIndex = order.indexOfFirst { it is AddSidepathUsage }
            assertTrue(cyclewayIndex >= 0 && sidepathIndex >= 0)
            assertEquals(cyclewayIndex + 1, sidepathIndex)
        } finally {
            connection.close()
        }
    }
}
