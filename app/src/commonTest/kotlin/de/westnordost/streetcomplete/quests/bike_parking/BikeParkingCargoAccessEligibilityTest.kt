package de.westnordost.streetcomplete.quests.bike_parking

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.quests.bike_parking_capacity.AddBikeParkingCapacity
import de.westnordost.streetcomplete.quests.bike_parking_cover.AddBikeParkingCover
import de.westnordost.streetcomplete.quests.bike_parking_type.AddBikeParkingType
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BikeParkingCargoAccessEligibilityTest {

    private lateinit var typeQuest: AddBikeParkingType
    private lateinit var capacityQuest: AddBikeParkingCapacity
    private lateinit var coverQuest: AddBikeParkingCover

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        typeQuest = AddBikeParkingType()
        capacityQuest = AddBikeParkingCapacity()
        coverQuest = AddBikeParkingCover()
    }

    private fun applicable(quest: OsmFilterQuestType<*>, tags: Map<String, String>) =
        quest.isApplicableTo(node(tags = tags)) == true &&
            quest.isApplicableTo(way(tags = tags)) == true

    private fun excluded(quest: OsmFilterQuestType<*>, tags: Map<String, String>) =
        quest.isApplicableTo(node(tags = tags)) != true &&
            quest.isApplicableTo(way(tags = tags)) != true

    @Test fun `unrestricted parking stays eligible`() {
        val tags = mapOf("amenity" to "bicycle_parking")
        assertTrue(applicable(typeQuest, tags))
        assertTrue(applicable(capacityQuest, tags))
        assertTrue(applicable(coverQuest, tags + ("bicycle_parking" to "stands")))
    }

    @Test fun `access no without cargo permission stays excluded`() {
        val tags = mapOf("amenity" to "bicycle_parking", "access" to "no")
        assertTrue(excluded(typeQuest, tags))
        assertTrue(excluded(capacityQuest, tags))
        assertTrue(excluded(coverQuest, tags + ("bicycle_parking" to "bollard")))
    }

    @Test fun `access no with cargo_bike no stays excluded`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "no",
        )
        assertTrue(excluded(typeQuest, tags))
        assertTrue(excluded(capacityQuest, tags))
        assertTrue(excluded(coverQuest, tags + ("bicycle_parking" to "stands")))
    }

    @Test fun `access no with cargo_bike yes is eligible when other conditions match`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "yes",
        )
        assertTrue(applicable(typeQuest, tags))
        assertTrue(applicable(capacityQuest, tags))
        assertTrue(applicable(coverQuest, tags + ("bicycle_parking" to "stands")))
    }

    @Test fun `access no with cargo_bike designated is eligible when other conditions match`() {
        val tags = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "designated",
        )
        assertTrue(applicable(typeQuest, tags))
        assertTrue(applicable(capacityQuest, tags))
        assertTrue(applicable(coverQuest, tags + ("bicycle_parking" to "bollard")))
    }

    @Test fun `access private stays excluded even with cargo permission`() {
        for (cargo in listOf(null, "yes", "designated")) {
            val tags = buildMap {
                put("amenity", "bicycle_parking")
                put("access", "private")
                if (cargo != null) put("cargo_bike", cargo)
            }
            assertTrue(excluded(typeQuest, tags), "cargo=$cargo")
            assertTrue(excluded(capacityQuest, tags), "cargo=$cargo")
            assertTrue(excluded(coverQuest, tags + ("bicycle_parking" to "stands")), "cargo=$cargo")
        }
    }

    @Test fun `customers access preserves prior eligibility`() {
        val tags = mapOf("amenity" to "bicycle_parking", "access" to "customers")
        assertTrue(applicable(typeQuest, tags))
        assertTrue(applicable(capacityQuest, tags))
        assertTrue(applicable(coverQuest, tags + ("bicycle_parking" to "stands")))
    }

    @Test fun `not eligible without amenity bicycle_parking`() {
        val tags = mapOf("access" to "no", "cargo_bike" to "yes")
        assertTrue(excluded(typeQuest, tags))
        assertTrue(excluded(capacityQuest, tags))
        assertTrue(excluded(coverQuest, tags))
    }

    @Test fun `charging station access no stays excluded despite cargo_bike on cover quest`() {
        val tags = mapOf(
            "amenity" to "charging_station",
            "bicycle" to "yes",
            "lockable" to "no",
            "motorcar" to "no",
            "access" to "no",
            "cargo_bike" to "yes",
        )
        assertFalse(coverQuest.isApplicableTo(node(tags = tags)) == true)
    }
}
