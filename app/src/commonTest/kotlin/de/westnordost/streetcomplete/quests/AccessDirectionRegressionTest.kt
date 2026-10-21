package de.westnordost.streetcomplete.quests

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.quests.aerialway.AddAerialwayBicycleAccess
import de.westnordost.streetcomplete.quests.aerialway.AerialwayBicycleAccessAnswer
import de.westnordost.streetcomplete.quests.barrier_bicycle_barrier_installation.AddBicycleBarrierInstallation
import de.westnordost.streetcomplete.quests.barrier_bicycle_barrier_installation.BicycleBarrierInstallationAnswer
import de.westnordost.streetcomplete.quests.cycleway.AddCycleway
import de.westnordost.streetcomplete.quests.ferry.AddFerryAccessBicycle
import de.westnordost.streetcomplete.quests.ferry.FerryBicycleAccess
import de.westnordost.streetcomplete.quests.oneway.AddOneway
import de.westnordost.streetcomplete.quests.oneway.AddOnewayAerialway
import de.westnordost.streetcomplete.quests.oneway.AddOnewayBicycle
import de.westnordost.streetcomplete.quests.oneway.OnewayAnswer
import de.westnordost.streetcomplete.quests.parking_access.AddBikeParkingAccess
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AccessDirectionRegressionTest {

    @Test fun `vehicle exception makes bicycle direction quest applicable without overriding bicycle prohibition`() {
        val quest = AddOnewayBicycle()
        val allowed = way(tags = mapOf("highway" to "cycleway", "access" to "no", "vehicle" to "yes"))
        assertNotEquals(false, quest.isApplicableTo(allowed))
        assertEquals(listOf(allowed), quest.getApplicableElements(TestMapDataWithGeometry(listOf(allowed))).toList())
        assertEquals(false, quest.isApplicableTo(way(tags = allowed.tags + ("bicycle" to "no"))))
    }

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
    }

    private fun assertExcluded(
        quest: OsmElementQuestType<*>,
        base: Map<String, String>,
        specialKeys: List<String>,
    ) {
        assertSelected(quest, base)
        for (key in specialKeys) {
            val values = if (key.endsWith(":conditional")) listOf("yes @ (Su)", "no @ (Su)") else listOf("no", "yes")
            for (value in values) {
                val element = way(1, listOf(1, 2), base + (key to value))
                assertEquals(false, quest.isApplicableTo(element), "$key=$value")
                assertTrue(quest.getApplicableElements(mapDataFor(element)).none(), "$key=$value")
            }
        }
    }

    private fun mapDataFor(element: Way): TestMapDataWithGeometry =
        TestMapDataWithGeometry(listOf(
            element,
            way(2, listOf(element.nodeIds.first(), 10), mapOf("highway" to "residential", "maxspeed" to "30")),
            way(3, listOf(element.nodeIds.last(), 11), mapOf("highway" to "residential", "maxspeed" to "30")),
        ))

    private fun assertSelected(quest: OsmElementQuestType<*>, tags: Map<String, String>) {
        val element = way(1, listOf(1, 2), tags)
        assertNotEquals(false, quest.isApplicableTo(element), tags.toString())
        assertEquals(listOf(element), quest.getApplicableElements(mapDataFor(element)).toList(), tags.toString())
    }

    @Test fun `access schedules do not suppress independent direction or infrastructure questions`() {
        for (key in listOf("access:conditional", "vehicle:conditional", "bicycle:conditional")) {
            for (value in listOf("no @ (Su)", "yes @ (Mo-Fr 08:00-18:00)")) {
                val conditional = mapOf(key to value)
                for (highway in listOf("cycleway", "path", "footway")) {
                    assertSelected(AddOnewayBicycle(), mapOf("highway" to highway, "bicycle" to "yes") + conditional)
                }
                assertSelected(AddOneway(), mapOf("highway" to "residential", "width" to "3") + conditional)
                assertSelected(AddCycleway { CountryInfo(null, emptyList()) }, mapOf("highway" to "primary") + conditional)
            }
        }
    }

    @Test fun `bicycle direction exception does not suppress general road direction question`() {
        val original = mapOf("highway" to "residential", "width" to "3",
            "oneway:bicycle:conditional" to "no @ (Su)", "access:conditional" to "no @ (Mo)")
        val quest = AddOneway()
        assertSelected(quest, original)
        for (answer in OnewayAnswer.entries) {
            assertEquals(setOf("oneway"), quest.answerAppliedTo(answer, original).map { it.key }.toSet())
        }
    }

    @Test fun `conditional permission does not bypass explicit bicycle prohibition`() {
        for (quest in listOf(AddOnewayBicycle(), AddCycleway { CountryInfo(null, emptyList()) })) {
            val highway = if (quest is AddOnewayBicycle) "cycleway" else "primary"
            val element = way(1, listOf(1, 2), mapOf("highway" to highway, "bicycle" to "no", "bicycle:conditional" to "yes @ (Su)"))
            assertEquals(false, quest.isApplicableTo(element))
            assertTrue(quest.getApplicableElements(mapDataFor(element)).none())
        }
    }

    @Test fun `access schedules do not bypass conditional direction guards`() {
        for (quest in listOf(AddOnewayBicycle(), AddCycleway { CountryInfo(null, emptyList()) }, AddOneway())) {
            val base = when (quest) {
                is AddOnewayBicycle -> mapOf("highway" to "cycleway")
                is AddCycleway -> mapOf("highway" to "primary")
                else -> mapOf("highway" to "residential", "width" to "3")
            }
            assertExcluded(quest, base + ("access:conditional" to "no @ (Su)"), listOf("oneway:conditional"))
        }
    }

    @Test fun `unrelated direction schedules do not suppress access questions`() {
        val conditional = mapOf("oneway:conditional" to "yes @ (Su)")
        assertSelected(AddFerryAccessBicycle(), mapOf("route" to "ferry") + conditional)
        assertSelected(AddAerialwayBicycleAccess(), mapOf("aerialway" to "gondola") + conditional)
        assertSelected(AddBikeParkingAccess(), mapOf("amenity" to "bicycle_parking", "bicycle_parking" to "shed") + conditional)
    }

    @Test fun `bicycle direction quest excludes conditional direction and directional access`() {
        assertExcluded(AddOnewayBicycle(), mapOf("highway" to "cycleway"), listOf(
            "oneway:conditional", "oneway:bicycle:conditional", "bicycle:forward", "bicycle:backward",
        ))
    }

    @Test fun `road direction quest excludes conditional oneway`() {
        assertExcluded(AddOneway(), mapOf("highway" to "residential", "width" to "3"), listOf(
            "oneway:conditional",
        ))
    }

    @Test fun `aerialway direction quest excludes conditional oneway`() {
        assertExcluded(AddOnewayAerialway(), mapOf("aerialway" to "gondola"), listOf("oneway:conditional"))
    }

    @Test fun `cycleway quest excludes conditional direction rules`() {
        assertExcluded(AddCycleway { CountryInfo(null, emptyList()) }, mapOf("highway" to "primary"), listOf(
            "oneway:conditional", "oneway:bicycle:conditional",
        ))
    }

    @Test fun `ferry bicycle quest excludes conditional and directional access`() {
        assertExcluded(AddFerryAccessBicycle(), mapOf("route" to "ferry"), listOf(
            "bicycle:conditional", "vehicle:conditional", "access:conditional", "bicycle:forward", "bicycle:backward",
        ))
    }

    @Test fun `aerialway bicycle quest excludes conditional access`() {
        assertExcluded(AddAerialwayBicycleAccess(), mapOf("aerialway" to "gondola"), listOf(
            "aerialway:bicycle:conditional", "bicycle:conditional",
        ))
    }

    @Test fun `bicycle parking access quest excludes conditional access`() {
        assertExcluded(AddBikeParkingAccess(), mapOf("amenity" to "bicycle_parking", "bicycle_parking" to "shed"), listOf("access:conditional"))
    }

    @Test fun `aerialway no sign answer ends applicability without inferring access`() {
        val quest = AddAerialwayBicycleAccess()
        val source = mapOf("aerialway" to "gondola")
        val changes = quest.answerAppliedTo(AerialwayBicycleAccessAnswer.NO_SIGN, source)
        assertEquals(setOf("aerialway:bicycle:signed"), changes.map { it.key }.toSet())
        assertFalse(quest.isApplicableTo(way(tags = source + ("aerialway:bicycle:signed" to "no"))))
    }

    @Test fun `bicycle direction answers retain mode specific key and existing access`() {
        for (highway in listOf("cycleway", "path", "footway")) {
            for (answer in OnewayAnswer.entries) {
                val original = mapOf("highway" to highway, "bicycle" to "yes", "access" to "private",
                    "bicycle:conditional" to "no @ (Su)", "oneway:bicycle:conditional" to "no @ (Su)")
                val changes = AddOnewayBicycle().answerAppliedTo(answer, original)
                val key = if (highway == "cycleway") "oneway" else "oneway:bicycle"
                assertEquals(setOf(key), changes.map { it.key }.toSet())
            }
        }
    }

    @Test fun `ferry answer preserves inherited and conditional access`() {
        val original = mapOf("route" to "ferry", "access" to "customers", "vehicle" to "private",
            "bicycle:conditional" to "no @ (Su)", "access:conditional" to "no @ (Su)")
        val changes = AddFerryAccessBicycle().answerAppliedTo(FerryBicycleAccess.YES, original)
        assertEquals(setOf("bicycle"), changes.map { it.key }.toSet())
    }

    @Test fun `not a bicycle barrier removes obsolete type and preserves access`() {
        val original = mapOf("barrier" to "cycle_barrier", "cycle_barrier" to "double",
            "bicycle" to "no", "access" to "private", "bicycle:conditional" to "yes @ (Su)")
        val changes = AddBicycleBarrierInstallation().answerAppliedTo(BicycleBarrierInstallationAnswer.NotBicycleBarrier, original)
        assertTrue(changes.contains(StringMapEntryDelete("cycle_barrier", "double")))
        assertEquals(setOf("barrier", "cycle_barrier"), changes.map { it.key }.toSet())
    }

    @Test fun `optional road and bicycle path extensions remain excluded`() {
        val cycleway = AddCycleway { CountryInfo(null, emptyList()) }
        assertNotEquals(false, cycleway.isApplicableTo(way(tags = mapOf("highway" to "residential", "maxspeed" to "30", "oneway" to "yes"))))
        assertEquals(false, cycleway.isApplicableTo(way(tags = mapOf("highway" to "residential", "maxspeed" to "30"))))
        assertEquals(false, cycleway.isApplicableTo(way(tags = mapOf("highway" to "trunk", "bicycle" to "yes"))))
        assertEquals(false, AddOnewayBicycle().isApplicableTo(way(tags = mapOf("highway" to "path", "bicycle" to "permissive"))))
    }
}
