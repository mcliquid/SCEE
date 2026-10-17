package de.westnordost.streetcomplete.quests.cycleway

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway
import de.westnordost.streetcomplete.osm.cycleway.CyclewayAndDirection
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath
import de.westnordost.streetcomplete.osm.cycleway.parseCyclewaySides
import de.westnordost.streetcomplete.osm.cycleway.selectableOrNullValues
import de.westnordost.streetcomplete.osm.oneway.Direction.BACKWARD
import de.westnordost.streetcomplete.osm.oneway.Direction.FORWARD
import de.westnordost.streetcomplete.quests.answerAppliedTo
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
    @Test fun `oneway aliases have identical side relevance`() {
        for (value in listOf("yes", "1", "true", "-1")) {
            val element = way(tags = mapOf("highway" to "primary", "oneway" to value))
            val relevance = getCyclewaySideRelevance(element, Sides(null, null), false)
            assertEquals(CyclewaySideRelevance(left = value == "-1", right = value != "-1"), relevance, value)
            val flowSide = if (value == "-1") "left" else "right"
            val oppositeSide = if (value == "-1") "right" else "left"
            assertApplicability(mapOf("oneway" to value, "cycleway:$flowSide" to "track"), bulk = false, single = null)
            assertApplicability(mapOf("oneway" to value, "cycleway:$oppositeSide" to "track"), bulk = true, single = null)
        }
    }

    @Test fun `junction does not override explicit bidirectional aliases`() {
        for (junction in listOf("roundabout", "circular")) {
            for (value in listOf("no", "0", "false")) {
                assertApplicability(mapOf("junction" to junction, "oneway" to value, "cycleway:right" to "track"),
                    bulk = true, single = true)
            }
        }
        assertApplicability(mapOf("junction" to "circular", "cycleway:right" to "track"), bulk = true, single = true)
        assertApplicability(mapOf("junction" to "roundabout", "cycleway:right" to "track"), bulk = false, single = null)
    }

    @Test fun `bidirectional circular road requires both cycleway sides`() {
        assertApplicability(
            mapOf("junction" to "circular", "oneway" to "no", "cycleway:right" to "track"),
            bulk = true,
            single = true,
        )
    }

    @Test fun `oneway minor road without cycleway tags is applicable`() {
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes"), true)
        assertMinorOnewayApplicability(mapOf("highway" to "living_street", "oneway" to "yes"), true)
        assertMinorOnewayApplicability(mapOf("highway" to "service", "service" to "alley", "oneway" to "yes"), true)
    }

    @Test fun `oneway minor road contraflow survey stays narrowly filtered`() {
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30"), false)
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes", "oneway:bicycle" to "no"), false)
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes", "bicycle:backward" to "no"), false)
        assertMinorOnewayApplicability(mapOf("highway" to "service", "service" to "driveway", "oneway" to "yes"), false)
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes", "surface" to "gravel"), false)
        assertMinorOnewayApplicability(mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes", "bicycle" to "use_sidepath"), false)
    }

    @Test fun `oneway minor road only requires flow side until contraflow is surveyed`() {
        val way = way(tags = mapOf("highway" to "residential", "oneway" to "yes"))
        assertEquals(
            CyclewaySideRelevance(left = false, right = true),
            getCyclewaySideRelevance(way, Sides(null, null), false),
        )
    }

    @Test fun `oneway minor road with partial cycleway tagging still surveys missing side`() {
        for (highway in listOf("residential", "living_street")) {
            val tags = mapOf("highway" to highway, "oneway" to "yes", "cycleway:right" to "track")
            val element = way(tags = tags)
            val cycleways = parseCyclewaySides(tags, false)!!.selectableOrNullValues(countryInfo)
            assertEquals(
                CyclewaySideRelevance(left = true, right = true),
                getCyclewaySideRelevance(element, cycleways, false),
                highway,
            )
            assertMinorOnewayApplicability(tags, true)
        }
        val serviceTags = mapOf("highway" to "service", "service" to "alley", "oneway" to "-1", "cycleway:left" to "track")
        val serviceWay = way(tags = serviceTags)
        val serviceCycleways = parseCyclewaySides(serviceTags, false)!!.selectableOrNullValues(countryInfo)
        assertEquals(
            CyclewaySideRelevance(left = true, right = true),
            getCyclewaySideRelevance(serviceWay, serviceCycleways, false),
        )
        assertMinorOnewayApplicability(serviceTags, true)
    }

    @Test fun `oneway minor road with flow side no does not reopen both sides`() {
        for (oneway in listOf("yes", "1", "true", "-1")) {
            val flowSide = if (oneway == "-1") "left" else "right"
            val tags = mapOf("highway" to "residential", "oneway" to oneway, "cycleway:$flowSide" to "no")
            val element = way(tags = tags)
            val cycleways = parseCyclewaySides(tags, false)!!.selectableOrNullValues(countryInfo)
            assertEquals(
                CyclewaySideRelevance(left = oneway == "-1", right = oneway != "-1"),
                getCyclewaySideRelevance(element, cycleways, false),
                oneway,
            )
            // With country (RHT): quest must not reopen. Without country, RHT/LHT disagree on
            // which side is flow vs contraflow for asymmetric cycleway:*=no → null.
            assertMinorOnewayApplicability(tags, bulk = false, single = null)
        }
        for (oneway in listOf("yes", "1", "true", "-1")) {
            val flowSide = if (oneway == "-1") "right" else "left"
            val tags = mapOf("highway" to "residential", "oneway" to oneway, "cycleway:$flowSide" to "no")
            val element = way(tags = tags)
            val cycleways = parseCyclewaySides(tags, true)!!.selectableOrNullValues(countryInfo)
            assertEquals(
                CyclewaySideRelevance(left = oneway != "-1", right = oneway == "-1"),
                getCyclewaySideRelevance(element, cycleways, true),
                "LHT $oneway",
            )
        }
        for (serviceTags in listOf(
            mapOf("highway" to "service", "service" to "alley", "oneway" to "yes", "cycleway:right" to "no"),
            mapOf("highway" to "service", "oneway" to "yes", "cycleway:right" to "no"),
        )) {
            val element = way(tags = serviceTags)
            val cycleways = parseCyclewaySides(serviceTags, false)!!.selectableOrNullValues(countryInfo)
            assertEquals(
                CyclewaySideRelevance(left = false, right = true),
                getCyclewaySideRelevance(element, cycleways, false),
                serviceTags.toString(),
            )
            assertMinorOnewayApplicability(serviceTags, bulk = false, single = null)
        }
    }

    @Test fun `confirmed bicycle contraflow on oneway minor road sets oneway bicycle no`() {
        val original = mapOf("highway" to "residential", "oneway" to "yes")
        val answer = Sides(
            left = CyclewayAndDirection(Cycleway.NONE_NO_ONEWAY, BACKWARD),
            right = CyclewayAndDirection(Cycleway.NONE, FORWARD),
        )
        val changes = questType.answerAppliedTo(answer, original)
        assertTrue(changes.contains(StringMapEntryAdd("oneway:bicycle", "no")))
        assertTrue(
            changes.contains(StringMapEntryAdd("cycleway:both", "no")) ||
                changes.containsAll(
                    listOf(
                        StringMapEntryAdd("cycleway:left", "no"),
                        StringMapEntryAdd("cycleway:right", "no"),
                    )
                )
        )
    }

    @Test fun `no bicycle contraflow on oneway minor road does not set oneway bicycle`() {
        val original = mapOf("highway" to "residential", "oneway" to "yes")
        val answer = Sides(
            left = CyclewayAndDirection(Cycleway.NONE, BACKWARD),
            right = CyclewayAndDirection(Cycleway.NONE, FORWARD),
        )
        val changes = questType.answerAppliedTo(answer, original).map { it.key }.toSet()
        assertFalse("oneway:bicycle" in changes)
    }

    @Test fun `separate answer without usage writes no bicycle tags`() {
        val answer = Sides(
            left = null,
            right = CyclewayAndDirection(Cycleway.SEPARATE, FORWARD),
        )
        val changes = questType.answerAppliedTo(answer, mapOf("highway" to "secondary"))
        assertEquals(setOf("cycleway:right"), changes.map { it.key }.toSet())
    }

    @Test fun `separate answer with compulsory usage writes directional use_sidepath`() {
        val answer = Sides(
            left = null,
            right = CyclewayAndDirection(Cycleway.SEPARATE, FORWARD, RoadSidepath.FORWARD),
        )
        val changes = questType.answerAppliedTo(answer, mapOf("highway" to "secondary"))
        assertTrue(changes.contains(StringMapEntryAdd("cycleway:right", "separate")))
        assertTrue(changes.contains(StringMapEntryAdd("bicycle:forward", "use_sidepath")))
    }

    @Test fun `compulsory usage does not overwrite bicycle yes`() {
        val answer = Sides(
            left = null,
            right = CyclewayAndDirection(Cycleway.SEPARATE, FORWARD, RoadSidepath.FORWARD),
        )
        val changes = questType.answerAppliedTo(answer, mapOf("highway" to "secondary", "bicycle" to "yes"))
        assertEquals(setOf("cycleway:right"), changes.map { it.key }.toSet())
    }

    @Test fun `road with use_sidepath stays excluded from initial survey`() {
        assertApplicability(mapOf("bicycle:forward" to "use_sidepath", "cycleway:right" to "separate"), bulk = false, single = false)
        assertApplicability(mapOf("bicycle" to "use_sidepath"), bulk = false, single = false)
    }

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

    @Test fun `bicycle access hierarchy and designated exclusion are applied`() {
        assertApplicability(
            mapOf("access" to "private", "bicycle" to "yes"),
            bulk = true,
            single = true,
        )
        assertApplicability(mapOf("vehicle" to "private"), bulk = false, single = false)
        assertApplicability(
            mapOf("access" to "no", "bicycle" to "designated"),
            bulk = false,
            single = false,
        )
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

    private fun assertMinorOnewayApplicability(
        tags: Map<String, String>,
        applicable: Boolean,
    ) {
        assertMinorOnewayApplicability(tags, bulk = applicable, single = applicable)
    }

    private fun assertMinorOnewayApplicability(
        tags: Map<String, String>,
        bulk: Boolean,
        single: Boolean?,
    ) {
        val way = way(1L, listOf(1, 2, 3), tags)
        val mapData = TestMapDataWithGeometry(listOf(way))
        mapData.wayGeometriesById[1L] = pGeom(0.0, 0.0)

        assertEquals(bulk, questType.getApplicableElements(mapData).any(), tags.toString())
        assertEquals(single, questType.isApplicableTo(way), tags.toString())
    }
}
