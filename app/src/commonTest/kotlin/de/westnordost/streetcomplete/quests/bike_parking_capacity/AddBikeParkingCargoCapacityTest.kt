package de.westnordost.streetcomplete.quests.bike_parking_capacity

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.getQuestTypeList
import de.westnordost.streetcomplete.quests.osmose.OsmoseDao
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddBikeParkingCargoCapacityTest {

    private lateinit var quest: AddBikeParkingCargoCapacity
    private lateinit var totalCapacityQuest: AddBikeParkingCapacity

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        quest = AddBikeParkingCargoCapacity()
        totalCapacityQuest = AddBikeParkingCapacity()
    }

    private fun eligible(tags: Map<String, String>) =
        quest.isApplicableTo(node(tags = tags)) == true &&
            quest.isApplicableTo(way(tags = tags)) == true

    private fun excluded(tags: Map<String, String>) =
        quest.isApplicableTo(node(tags = tags)) != true &&
            quest.isApplicableTo(way(tags = tags)) != true

    @Test fun `missing capacity cargo bike is eligible`() {
        assertTrue(eligible(mapOf("amenity" to "bicycle_parking")))
    }

    @Test fun `existing total capacity does not suppress eligibility`() {
        assertTrue(eligible(mapOf("amenity" to "bicycle_parking", "capacity" to "12")))
    }

    @Test fun `existing capacity cargo bike zero suppresses eligibility`() {
        assertTrue(excluded(mapOf("amenity" to "bicycle_parking", "capacity:cargo_bike" to "0")))
    }

    @Test fun `existing positive capacity cargo bike suppresses eligibility`() {
        assertTrue(excluded(mapOf("amenity" to "bicycle_parking", "capacity:cargo_bike" to "2")))
    }

    @Test fun `access no without cargo permission remains excluded`() {
        assertTrue(excluded(mapOf("amenity" to "bicycle_parking", "access" to "no")))
    }

    @Test fun `access no with cargo bike yes is eligible`() {
        assertTrue(eligible(mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "yes",
        )))
    }

    @Test fun `access no with cargo bike designated is eligible`() {
        assertTrue(eligible(mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "designated",
        )))
    }

    @Test fun `access private remains excluded`() {
        assertTrue(excluded(mapOf("amenity" to "bicycle_parking", "access" to "private")))
    }

    @Test fun `writes zero to capacity cargo bike`() {
        assertEquals(
            setOf(StringMapEntryAdd("capacity:cargo_bike", "0")),
            quest.answerAppliedTo(0, mapOf("amenity" to "bicycle_parking")),
        )
    }

    @Test fun `writes positive integer to capacity cargo bike`() {
        assertEquals(
            setOf(StringMapEntryAdd("capacity:cargo_bike", "5")),
            quest.answerAppliedTo(5, mapOf("amenity" to "bicycle_parking")),
        )
    }

    @Test fun `preserves capacity access and cargo bike tags`() {
        val before = mapOf(
            "amenity" to "bicycle_parking",
            "capacity" to "12",
            "access" to "no",
            "cargo_bike" to "designated",
            "bicycle_parking" to "stands",
        )
        assertEquals(
            setOf(StringMapEntryAdd("capacity:cargo_bike", "2")),
            quest.answerAppliedTo(2, before),
        )
    }

    @Test fun `not eligible after answering zero`() {
        val tags = mapOf("amenity" to "bicycle_parking", "capacity:cargo_bike" to "0")
        assertTrue(excluded(tags))
    }

    @Test fun `not eligible after answering positive value`() {
        val tags = mapOf("amenity" to "bicycle_parking", "capacity:cargo_bike" to "3")
        assertTrue(excluded(tags))
    }

    @Test fun `count input with minimum zero accepts zero but not empty or negative`() {
        val minimum = 0
        fun complete(count: Int?) = count?.let { it >= minimum } == true
        assertFalse(complete(null))
        assertFalse(complete(-1))
        assertTrue(complete(0))
        assertTrue(complete(2))
    }

    @Test fun `non integer input stays incomplete via count input contract`() {
        assertEquals(null, "abc".toIntOrNull())
        assertEquals(null, "1.5".toIntOrNull())
    }

    @Test fun `total capacity quest still requires missing capacity`() {
        val tags = mapOf("amenity" to "bicycle_parking", "capacity" to "12")
        assertFalse(totalCapacityQuest.isApplicableTo(node(tags = tags)) == true)
    }

    @Test fun `registered as quest id 203`() {
        val arSupportChecker = object : ArSupportChecker {
            override fun invoke(): Boolean = false
        }
        val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo()))
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
            assertEquals(203, registry.getOrdinalOf(registry.first { it is AddBikeParkingCargoCapacity }))
            assertEquals(1, registry.count { it is AddBikeParkingCargoCapacity })
        } finally {
            connection.close()
        }
    }
}
