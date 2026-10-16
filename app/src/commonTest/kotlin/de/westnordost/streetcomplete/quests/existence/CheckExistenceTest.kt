package de.westnordost.streetcomplete.quests.existence

import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.testutils.feature
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckExistenceTest {
    private val questType = CheckExistence { element ->
        when {
            element.tags["amenity"] == "telephone" -> feature()
            element.tags["amenity"] == "bicycle_rental" &&
                element.tags["bicycle_rental"] == "docking_station" -> feature()
            element.tags["amenity"] == "bench" -> feature()
            else -> null
        }
    }

    private val millisecondsFor800Days: Long = 1000L * 60 * 60 * 24 * 800
    private val millisecondsFor5Years: Long = 1000L * 60 * 60 * 24 * 365 * 5
    private val millisecondsFor7Years: Long = 1000L * 60 * 60 * 24 * 365 * 7

    private fun oldDockingStation(extraTags: Map<String, String> = emptyMap()) = node(
        tags = mapOf(
            "amenity" to "bicycle_rental",
            "bicycle_rental" to "docking_station",
        ) + extraTags,
        timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
    )

    @Test
    fun `isApplicableTo returns false for known places with recently edited amenity=telephone`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf(
                        "amenity" to "telephone",
                    ), timestamp = nowAsEpochMilliseconds()
                )
            )
        )
    }

    @Test
    fun `isApplicableTo returns true for known places with old amenity=telephone`() {
        assertTrue(
            questType.isApplicableTo(
                node(
                    tags = mapOf(
                        "amenity" to "telephone",
                    ), timestamp = nowAsEpochMilliseconds() - millisecondsFor800Days
                )
            )
        )
    }

    @Test
    fun `applicable to old bicycle rental docking station`() {
        assertTrue(questType.isApplicableTo(oldDockingStation()))
    }

    @Test
    fun `applicable to old bicycle rental docking station with gbfs station id`() {
        assertTrue(questType.isApplicableTo(oldDockingStation(mapOf("gbfs:station_id" to "123"))))
    }

    @Test
    fun `applicable to old bicycle rental docking station with other gbfs tags`() {
        assertTrue(
            questType.isApplicableTo(
                oldDockingStation(
                    mapOf(
                        "gbfs:station_id" to "abc",
                        "gbfs:system_id" to "system-1",
                        "gbfs:feed" to "feed-1",
                    )
                )
            )
        )
    }

    @Test
    fun `not applicable to bicycle rental dropoff point`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf(
                        "amenity" to "bicycle_rental",
                        "bicycle_rental" to "dropoff_point",
                    ),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
                )
            )
        )
    }

    @Test
    fun `not applicable to bicycle rental without subtype`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf("amenity" to "bicycle_rental"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
                )
            )
        )
    }

    @Test
    fun `not applicable to shop bicycle rental`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf("shop" to "bicycle_rental"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
                )
            )
        )
    }

    @Test
    fun `not applicable to docking station with recent check date`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf(
                        "amenity" to "bicycle_rental",
                        "bicycle_rental" to "docking_station",
                        "check_date" to nowAsCheckDateString(),
                    ),
                    timestamp = nowAsEpochMilliseconds()
                )
            )
        )
    }

    @Test
    fun `not applicable to seasonal docking station`() {
        assertFalse(
            questType.isApplicableTo(oldDockingStation(mapOf("seasonal" to "yes")))
        )
    }

    @Test
    fun `not applicable to private docking station`() {
        assertFalse(
            questType.isApplicableTo(oldDockingStation(mapOf("access" to "private")))
        )
    }

    @Test
    fun `and has higher precedence so docking station alone is not enough`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf("bicycle_rental" to "docking_station"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
                )
            )
        )
    }

    @Test
    fun `two year block still applies to telephone after less than four years`() {
        assertTrue(
            questType.isApplicableTo(
                node(
                    tags = mapOf("amenity" to "telephone"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor800Days
                )
            )
        )
    }

    @Test
    fun `six year block still requires more than four years for bench`() {
        assertFalse(
            questType.isApplicableTo(
                node(
                    tags = mapOf("amenity" to "bench"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor5Years
                )
            )
        )
        assertTrue(
            questType.isApplicableTo(
                node(
                    tags = mapOf("amenity" to "bench"),
                    timestamp = nowAsEpochMilliseconds() - millisecondsFor7Years
                )
            )
        )
    }
}
