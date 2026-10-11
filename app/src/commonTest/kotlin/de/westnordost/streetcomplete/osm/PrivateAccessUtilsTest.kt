package de.westnordost.streetcomplete.osm

import de.westnordost.streetcomplete.data.elementfilter.toElementFilterExpression
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals

class PrivateAccessUtilsTest {

    @Test fun `vehicle permission overrides inherited restriction but not bicycle restriction`() {
        for (access in listOf("no", "private")) {
            for (vehicle in listOf("yes", "permissive", "destination", "customers", "permit")) {
                val tags = mapOf("access" to access, "vehicle" to vehicle)
                assertEquals(true, bicycleAccessibleFilter.matches(way(tags = tags)), tags.toString())
                for (bicycle in listOf("no", "private")) {
                    assertEquals(false, bicycleAccessibleFilter.matches(way(tags = tags + ("bicycle" to bicycle))))
                }
            }
        }
    }

    private val bicycleAccessibleFilter =
        "ways with ($FILTER_BICYCLE_ACCESSIBLE)".toElementFilterExpression()

    @Test fun `bicycle access follows OSM access hierarchy`() {
        val cases = listOf(
            mapOf("access" to "private") to false,
            mapOf("access" to "private", "bicycle" to "yes") to true,
            mapOf("access" to "no", "bicycle" to "designated") to true,
            mapOf("vehicle" to "private") to false,
            mapOf("vehicle" to "private", "bicycle" to "yes") to true,
            mapOf("access" to "yes", "bicycle" to "no") to false,
            mapOf("bicycle" to "private") to false,
            mapOf("access" to "permissive") to true,
            mapOf("access" to "customers", "bicycle" to "yes") to true,
            mapOf("motor_vehicle" to "private") to true,
            emptyMap<String, String>() to true,
        )

        for ((tags, expected) in cases) {
            assertEquals(expected, bicycleAccessibleFilter.matches(way(tags = tags)), tags.toString())
        }
    }
}
