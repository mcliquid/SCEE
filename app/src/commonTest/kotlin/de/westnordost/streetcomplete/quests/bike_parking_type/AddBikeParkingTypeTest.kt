package de.westnordost.streetcomplete.quests.bike_parking_type

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.bike_parking_type.BikeParkingType.*
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.allDrawableResources
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class AddBikeParkingTypeTest {

    private lateinit var quest: AddBikeParkingType

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        quest = AddBikeParkingType()
    }

    @Test fun `bollard answer writes bicycle_parking tag`() {
        assertEquals(
            setOf(StringMapEntryAdd("bicycle_parking", "bollard")),
            quest.answerAppliedTo(BOLLARD, mapOf("amenity" to "bicycle_parking")),
        )
    }

    @Test fun `answer preserves unrelated tags`() {
        val before = mapOf(
            "amenity" to "bicycle_parking",
            "capacity" to "2",
            "covered" to "no",
            "access" to "yes",
        )
        assertEquals(
            setOf(StringMapEntryAdd("bicycle_parking", "bollard")),
            quest.answerAppliedTo(BOLLARD, before),
        )
    }

    @Test fun `existing parking types keep osm mappings`() {
        val expected = mapOf(
            STANDS to "stands",
            WALL_LOOPS to "wall_loops",
            SAFE_LOOPS to "safe_loops",
            SHED to "shed",
            LOCKERS to "lockers",
            BUILDING to "building",
            HANDLEBAR_HOLDER to "handlebar_holder",
            SADDLE_HOLDER to "saddle_holder",
            TWO_TIER to "two-tier",
            FLOOR to "floor",
            BOLLARD to "bollard",
        )
        for ((type, value) in expected) {
            assertEquals(type.osmValue, value)
            assertEquals(
                setOf(StringMapEntryAdd("bicycle_parking", value)),
                quest.answerAppliedTo(type, mapOf("amenity" to "bicycle_parking")),
            )
        }
    }

    @Test fun `bollard serializes and deserializes`() {
        val encoded = Json.encodeToString(BikeParkingType.serializer(), BOLLARD)
        assertEquals(BOLLARD, Json.decodeFromString(BikeParkingType.serializer(), encoded))
    }

    @Test fun `eligible without bicycle_parking type`() {
        assertTrue(quest.isApplicableTo(node(tags = mapOf("amenity" to "bicycle_parking"))))
        assertTrue(quest.isApplicableTo(way(tags = mapOf("amenity" to "bicycle_parking"))))
    }

    @Test fun `not eligible after bicycle_parking is set`() {
        val answered = mapOf("amenity" to "bicycle_parking", "bicycle_parking" to "bollard")
        assertFalse(quest.isApplicableTo(node(tags = answered)))
    }

    @Test fun `access no still excluded`() {
        assertFalse(quest.isApplicableTo(node(tags = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
        ))))
    }

    @Test fun `access no with cargo_bike yes is eligible`() {
        assertTrue(quest.isApplicableTo(node(tags = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "yes",
        ))))
    }

    @Test fun `answer preserves access and cargo_bike on cargo-only parking`() {
        val before = mapOf(
            "amenity" to "bicycle_parking",
            "access" to "no",
            "cargo_bike" to "designated",
        )
        assertEquals(
            setOf(StringMapEntryAdd("bicycle_parking", "bollard")),
            quest.answerAppliedTo(BOLLARD, before),
        )
    }

    @Test fun `each type has drawable and title resource`() {
        for (type in BikeParkingType.entries) {
            assertTrue(Res.allDrawableResources.containsValue(type.icon), type.name)
            type.title // exhaustive when + compile-time StringResource
        }
    }
}
