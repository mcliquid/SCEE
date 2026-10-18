package de.westnordost.streetcomplete.data.quest

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.osmose.OsmoseDao
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.quests.questTypeRegistry
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.Test
import kotlin.test.assertTrue

class TestQuestFilters {

    @Test
    fun getHighlightedElements_filters_are_valid_and_parsed_correctly() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = inMemoryPrefs()

        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            val database = DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) }
            val registry = questTypeRegistry(
                arSupportChecker = object : ArSupportChecker { override fun invoke() = false },
                getCountryInfoByLocation = { CountryInfo("DE", emptyList()) },
                getCountryOrSubdivisionCode = { null },
                getFeature = { null },
                osmoseDao = OsmoseDao(database, Prefs.preferences),
                customQuestList = CustomQuestList(),
            )

            val elementQuestTypes = registry.filterIsInstance<OsmElementQuestType<*>>()
            assertTrue(elementQuestTypes.isNotEmpty())

            val node = node(1)
            val way = way(1, nodes = listOf(1, 2))
            val relation = rel(1)
            val mapData = TestMapDataWithGeometry(emptyList())

            for (questType in elementQuestTypes) {
                val element = when {
                    questType.isApplicableTo(node) == true -> node
                    questType.isApplicableTo(way) == true -> way
                    questType.isApplicableTo(relation) == true -> relation
                    else -> way
                }
                val highlightedElements = questType.getHighlightedElements(element, mapData)
                highlightedElements.toList()
            }
        } finally {
            connection.close()
        }
    }

    @Test
    fun element_filters_are_valid_and_parsed_correctly() {
        val registry = questTypeRegistry(
            arSupportChecker = object : ArSupportChecker { override fun invoke() = false },
            getCountryInfoByLocation = { CountryInfo("DE", emptyList()) },
            getCountryOrSubdivisionCode = { null },
            getFeature = { null }
        )

        val elementQuestTypes = registry.filterIsInstance<OsmFilterQuestType<*>>()
        assertTrue(elementQuestTypes.isNotEmpty())

        val mapData = TestMapDataWithGeometry(emptyList())

        for (questType in elementQuestTypes) {
            val filteredElements = questType.getApplicableElements(mapData)
            filteredElements.toList()
        }
    }
}
