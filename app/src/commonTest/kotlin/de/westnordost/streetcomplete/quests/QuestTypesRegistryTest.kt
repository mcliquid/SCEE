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
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.Test
import kotlin.test.assertEquals

class QuestTypesRegistryTest {

    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))

    @Test
    fun `quest type ordinals are unique in QuestTypeRegistry`() {
        Prefs.sharedPreferences = mockPrefs()
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
}
