package de.westnordost.streetcomplete.quests.cycleway

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.osm.cycleway.parseCyclewaySides
import de.westnordost.streetcomplete.osm.cycleway.selectableOrNullValues
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AddCyclewayTest {
    private var countryInfo = CountryInfo(
        null,
        listOf(IncompleteCountryInfo(hasAdvisoryCycleLane = false, isLeftHandTraffic = false))
    )

    private lateinit var questType: AddCycleway

    @BeforeTest
    fun setUp() {
        questType = AddCycleway(getCountryInfoByLocation = { _ -> countryInfo })
    }

    @Test
    fun `applicable to road with missing cycleway`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary"
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(way)!!)
    }

    @Test fun `applicable to road with separately mapped sidewalk without inferring cycleway`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "sidewalk" to "separate",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(way)!!)
        assertNull(parseCyclewaySides(way.tags, false))
        assertTrue(way.tags.keys.none { it.startsWith("cycleway") })
    }

    @Test fun `side-specific separate sidewalk tags do not affect applicability`() {
        for (sidewalkKey in listOf("sidewalk:left", "sidewalk:right", "sidewalk:both")) {
            val way = way(
                1L, listOf(1, 2, 3), mapOf(
                    "highway" to "primary",
                    sidewalkKey to "separate",
                )
            )
            val mapData = TestMapDataWithGeometry(listOf(way))

            assertEquals(1, questType.getApplicableElements(mapData).toList().size)
            assertTrue(questType.isApplicableTo(way)!!)
        }
    }

    @Test fun `separate sidewalk does not bypass use-sidepath exclusion`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "sidewalk" to "separate",
                "bicycle" to "use_sidepath",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `not applicable to road with complete separate cycleway information`() {
        for (cyclewayKey in listOf("cycleway", "cycleway:both")) {
            val way = way(
                1L, listOf(1, 2, 3), mapOf(
                    "highway" to "primary",
                    cyclewayKey to "separate"
                )
            )
            val mapData = TestMapDataWithGeometry(listOf(way))

            assertEquals(0, questType.getApplicableElements(mapData).toList().size)
            assertFalse(questType.isApplicableTo(way)!!)
        }
    }

    @Test fun `applicable to two-way road with only one side tagged`() {
        val values = listOf(
            mapOf("cycleway:{side}" to "separate"),
            mapOf("cycleway:{side}" to "track"),
            mapOf("cycleway:{side}" to "lane", "cycleway:{side}:lane" to "exclusive"),
            mapOf("cycleway:{side}" to "no"),
            mapOf("cycleway:{side}" to "shoulder"),
        )

        for (side in listOf("left", "right")) {
            for (value in values) {
                val sideTags = value.mapKeys { (key, _) -> key.replace("{side}", side) }
                assertApplicability(sideTags, bulk = true, single = true)
            }
        }
    }

    @Test fun `not applicable to two-way road with both sides tagged`() {
        assertApplicability(
            mapOf("cycleway:right" to "separate", "cycleway:left" to "no"),
            bulk = false,
            single = false,
        )
        assertApplicability(
            mapOf(
                "cycleway:right" to "lane",
                "cycleway:right:lane" to "exclusive",
                "cycleway:left" to "track",
            ),
            bulk = false,
            single = false,
        )
    }

    @Test fun `not applicable to two-way road with unsuffixed cycleway tag`() {
        assertApplicability(mapOf("cycleway" to "track"), bulk = false, single = false)
    }

    @Test fun `not applicable to two-way road with unknown value on one side`() {
        assertApplicability(mapOf("cycleway:right" to "something"), bulk = false, single = false)
    }

    @Test fun `major forward oneway only requires flow side`() {
        assertApplicability(
            mapOf("oneway" to "yes", "cycleway:right" to "track"),
            bulk = false,
            single = null,
        )
        assertApplicability(
            mapOf("oneway" to "yes", "cycleway:left" to "track"),
            bulk = true,
            single = null,
        )
    }

    @Test fun `oneway bicycle no follows parser completeness`() {
        assertApplicability(
            mapOf(
                "oneway" to "yes",
                "oneway:bicycle" to "no",
                "cycleway:right" to "track",
            ),
            bulk = false,
            single = null,
        )
        assertApplicability(
            mapOf(
                "oneway" to "yes",
                "oneway:bicycle" to "no",
                "cycleway:left" to "track",
            ),
            bulk = true,
            single = null,
        )
    }

    @Test fun `legacy opposite tagging makes both sides relevant`() {
        for (legacyValue in listOf("opposite", "opposite_lane", "opposite_track")) {
            val way = way(
                tags = mapOf(
                    "highway" to "primary",
                    "oneway" to "yes",
                    "cycleway" to legacyValue,
                )
            )
            val cycleways = parseCyclewaySides(way.tags, false)!!
                .selectableOrNullValues(countryInfo)

            assertEquals(
                CyclewaySideRelevance(left = true, right = true),
                getCyclewaySideRelevance(way, cycleways, false),
                legacyValue,
            )
        }
    }

    @Test fun `major reversed oneway only requires flow side`() {
        assertApplicability(
            mapOf("oneway" to "-1", "cycleway:left" to "track"),
            bulk = false,
            single = null,
        )
        assertApplicability(
            mapOf("oneway" to "-1", "cycleway:right" to "track"),
            bulk = true,
            single = null,
        )
    }

    @Test fun `major left-hand-traffic oneway only requires flow side`() {
        countryInfo = CountryInfo(
            "GB",
            listOf(IncompleteCountryInfo(hasAdvisoryCycleLane = false, isLeftHandTraffic = true)),
        )

        assertApplicability(
            mapOf("oneway" to "yes", "cycleway:left" to "track"),
            bulk = false,
            single = null,
        )
        assertApplicability(
            mapOf("oneway" to "yes", "cycleway:right" to "track"),
            bulk = true,
            single = null,
        )
        assertApplicability(
            mapOf("oneway" to "-1", "cycleway:right" to "track"),
            bulk = false,
            single = null,
        )
        assertApplicability(
            mapOf("oneway" to "-1", "cycleway:left" to "track"),
            bulk = true,
            single = null,
        )
    }

    @Test fun `not applicable to non-road`() {
        val way = way(tags = mapOf("waterway" to "river"))
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `not applicable to road with cycleway that is not old enough`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "track"
            ), timestamp = nowAsEpochMilliseconds()
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `applicable to road with cycleway that is old enough`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "track",
                "check_date:cycleway" to "2001-01-01"
            ), timestamp = nowAsEpochMilliseconds()
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(way)!!)
    }

    @Test fun `not applicable to road with cycleway that is old enough but has unknown cycleway tagging`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "whatsthis",
                "check_date:cycleway" to "2001-01-01"
            ), timestamp = nowAsEpochMilliseconds()
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `applicable to road with cycleway that is tagged with an invalid value`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "yes",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(way)!!)
    }

    @Test fun `not applicable to road with cycleway that is tagged with an unknown + invalid value`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway:left" to "yes", // invalid
                "cycleway:right" to "doorzone2", // unknown
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `applicable to road with ambiguous cycleway value`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "shared_lane",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(way)!!)
    }

    @Test fun `not applicable to road with ambiguous + unknown cycleway value`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway:left" to "shared_lane",
                "cycleway:right" to "strange",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(way)!!)
    }

    @Test fun `applicable to road with ambiguous cycle lane not in Belgium`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "lane",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)

        countryInfo = CountryInfo(
            "DE",
            listOf(IncompleteCountryInfo(hasAdvisoryCycleLane = true, isLeftHandTraffic = false)),
        )

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        // because we don't know if we are in Belgium
        assertNull(questType.isApplicableTo(way))
    }

    @Test fun `unspecified cycle lane is not ambiguous in Belgium`() {
        val way = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "primary",
                "cycleway" to "lane",
            )
        )
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)

        countryInfo = CountryInfo(
            "BE",
            listOf(IncompleteCountryInfo(hasAdvisoryCycleLane = true, isLeftHandTraffic = false)),
        )

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        // because we don't know if we are in Belgium
        assertNull(questType.isApplicableTo(way))
    }

    @Test
    fun `not applicable to maxspeed 30 zone with zone_traffic urban`() {
        val residentialWayIn30Zone = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "residential",
                "maxspeed" to "30",
                "zone:traffic" to "DE:urban",
                "zone:maxspeed" to "DE:30",
            )
        )

        val mapData = TestMapDataWithGeometry(listOf(residentialWayIn30Zone))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(residentialWayIn30Zone)!!)
    }

    @Test
    fun `applicable to maxspeed 30 in built-up area`() {
        val residentialWayInBuiltUpAreaWithMaxspeed30 = way(
            tags = mapOf(
                "highway" to "residential",
                "maxspeed" to "30",
                "zone:traffic" to "DE:urban"
            )
        )

        val mapData = TestMapDataWithGeometry(listOf(residentialWayInBuiltUpAreaWithMaxspeed30))

        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertTrue(questType.isApplicableTo(residentialWayInBuiltUpAreaWithMaxspeed30)!!)
    }

    @Test
    fun `not applicable to residential road in maxspeed 30 zone`() {
        val residentialWayIn30Zone = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "residential",
                "maxspeed" to "30",
                "zone:maxspeed" to "DE:30",
            )
        )

        val mapData = TestMapDataWithGeometry(listOf(residentialWayIn30Zone))

        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(residentialWayIn30Zone)!!)
    }

    @Test
    fun `not applicable to residential road with maxspeed 30`() {
        val residentialWayWithMaxspeed30 = way(
            1L, listOf(1, 2, 3), mapOf(
                "highway" to "residential",
                "maxspeed" to "30",
            )
        )

        val mapData = TestMapDataWithGeometry(listOf(residentialWayWithMaxspeed30))

        // a residential way with maxspeed=30 and no other maxspeed tags is assumed to be in a max-speed 30 zone
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertFalse(questType.isApplicableTo(residentialWayWithMaxspeed30)!!)
    }

    private fun assertApplicability(
        extraTags: Map<String, String>,
        bulk: Boolean,
        single: Boolean?,
    ) {
        val way = way(
            1L,
            listOf(1, 2, 3),
            mapOf("highway" to "primary") + extraTags,
        )
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)

        assertEquals(bulk, questType.getApplicableElements(mapData).any())
        assertEquals(single, questType.isApplicableTo(way))
    }
}
