package de.westnordost.streetcomplete.quests.charging_station_access

import de.westnordost.streetcomplete.testutils.node
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddChargingStationBicyclesTest {

    @Test fun `USB C device socket is eligible without duplicate socket prefix`() {
        for (value in listOf("yes", "1", "2")) {
            assertTrue(questType.isApplicableTo(node(tags = mapOf(
                "amenity" to "charging_station", "socket:device:USB-C" to value,
            ))))
        }
        assertFalse(questType.isApplicableTo(node(tags = mapOf(
            "amenity" to "charging_station", "socket:device:USB-C" to "0",
        ))))
    }


    private val questType = AddChargingStationBicycles()

    @Test fun `is not applicable to empty tags`() {
        assertFalse(questType.isApplicableTo(node()))
    }

    @Test fun `is applicable to charging station with schuko = 2`() {
        assertTrue(questType.isApplicableTo(node(tags = mapOf(
            "amenity" to "charging_station",
            "socket:schuko" to "2",
        ))))
    }

    @Test fun `is not applicable charging station without bicycle compatible sockets`() {
        assertFalse(questType.isApplicableTo(node(tags = mapOf(
            "amenity" to "charging_station",
            "socket:schuko" to "0",
            "socket:type2" to "10",
        ))))
    }
}
