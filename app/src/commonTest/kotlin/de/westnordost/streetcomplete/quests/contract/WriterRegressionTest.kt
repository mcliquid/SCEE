package de.westnordost.streetcomplete.quests.contract

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway
import de.westnordost.streetcomplete.osm.cycleway.CyclewayAndDirection
import de.westnordost.streetcomplete.osm.cycleway.applyTo
import de.westnordost.streetcomplete.osm.fee.Fee
import de.westnordost.streetcomplete.osm.fee.applyTo
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.oneway.Direction
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.osm.surface.applyTo
import de.westnordost.streetcomplete.quests.barrier_locked.AddBarrierLocked
import de.westnordost.streetcomplete.quests.barrier_locked.Locked
import de.westnordost.streetcomplete.quests.barrier_locked.NotLocked
import de.westnordost.streetcomplete.quests.building_material.AddBuildingMaterial
import de.westnordost.streetcomplete.quests.building_material.BuildingMaterial
import de.westnordost.streetcomplete.quests.camping.AddCampDrinkingWater
import de.westnordost.streetcomplete.quests.camping.AddCampPower
import de.westnordost.streetcomplete.quests.camping.AddCampShower
import de.westnordost.streetcomplete.quests.diet_type.AddVegetarian
import de.westnordost.streetcomplete.quests.diet_type.AddVegan
import de.westnordost.streetcomplete.quests.diet_type.DietAvailability
import de.westnordost.streetcomplete.quests.fire_hydrant_diameter.AddFireHydrantDiameter
import de.westnordost.streetcomplete.quests.fire_hydrant_diameter.FireHydrantDiameterAnswer
import de.westnordost.streetcomplete.quests.parking_fee.AddParkingFee
import de.westnordost.streetcomplete.quests.parking_fee.ParkingFee
import de.westnordost.streetcomplete.quests.charge.AddParkingCharge
import de.westnordost.streetcomplete.quests.oneway.AddOneway
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WriterRegressionTest {
    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    @Test fun `camping confirmations update check dates and close resurvey`() {
        for ((quest, key) in listOf(
            AddCampShower() to "shower", AddCampPower() to "power_supply",
            AddCampDrinkingWater() to "drinking_water",
        )) {
            for (value in listOf(true, false)) {
                val after = assertQuestContract(quest, value,
                    mapOf("tourism" to "camp_site", key to if (value) "yes" else "no",
                        "check_date:$key" to "2000-01-01"),
                    { quest.isApplicableTo(node(tags = it)) == true })
                assertEquals(nowAsCheckDateString(), after["check_date:$key"])
            }
        }
    }

    @Test fun `unsigned hydrant confirmation closes resurvey`() {
        val quest = AddFireHydrantDiameter()
        val after = assertQuestContract(quest, FireHydrantDiameterAnswer.NoSign,
            mapOf("emergency" to "fire_hydrant", "fire_hydrant:type" to "pillar",
                "fire_hydrant:diameter:signed" to "no",
                "check_date:fire_hydrant:diameter:signed" to "2000-01-01"),
            { quest.isApplicableTo(node(tags = it)) == true })
        assertEquals(nowAsCheckDateString(), after["check_date:fire_hydrant:diameter:signed"])
    }

    @Test fun `locked base confirmation preserves independent night condition`() {
        val quest = AddBarrierLocked()
        for ((answer, base) in listOf(Locked to "yes", NotLocked to "no")) {
            val condition = if (base == "no") "yes @ (22:00-06:00)" else "no @ (08:00-18:00)"
            val after = assertQuestContract(quest, answer,
                mapOf("barrier" to "gate", "locked" to base, "locked:conditional" to condition,
                    "check_date:locked" to "2000-01-01"),
                { quest.getApplicableElements(de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry(
                    listOf(way(tags = it)))).any() }, setOf("locked:conditional"))
            assertEquals(nowAsCheckDateString(), after["check_date:locked"])
        }
    }

    @Test fun `no parking fee removes obsolete charges without removing other payments`() {
        val quest = AddParkingFee()
        val after = assertQuestContract(quest, ParkingFee(Fee.No), mapOf(
            "amenity" to "parking", "access" to "yes", "fee" to "yes",
            "charge" to "2 EUR/hour", "charge:conditional" to "1 EUR/hour @ (Su)",
            "check_date:fee" to "2000-01-01", "charge:lastcheck" to "2000-01-01",
            "check_date:charge:conditional" to "2000-01-01", "payment:cash" to "yes",
            "fee:disabled" to "no"),
            { quest.isApplicableTo(node(tags = it)) == true }, setOf("payment:cash", "fee:disabled"))
        for (key in listOf("charge", "charge:conditional", "charge:lastcheck", "check_date:charge:conditional")) {
            assertNull(after[key], key)
        }
        assertFalse(AddParkingCharge().isApplicableTo(node(tags = after)))
    }

    @Test fun `time restricted fee retains its charges`() {
        val tags = mapOf("fee" to "yes", "charge" to "2 EUR/hour", "charge:conditional" to "1 EUR/hour @ (Su)")
        val builder = StringMapChangesBuilder(tags)
        Fee.Yes().applyTo(builder)
        val after = tags.toMutableMap().also { builder.create().applyTo(it) }
        assertEquals(tags["charge"], after["charge"])
        assertEquals(tags["charge:conditional"], after["charge:conditional"])
    }

    @Test fun `coarse or deprecated surface refinement preserves independent details`() {
        for ((old, answer) in listOf("paved" to Surface.ASPHALT, "cobblestone" to Surface.SETT,
            "unpaved" to Surface.DIRT, "ground" to Surface.GRASS)) {
            for (prefix in listOf("", "footway:")) {
                val before = mapOf("${prefix}surface" to old, "${prefix}smoothness" to "bad",
                    "${prefix}surface:colour" to "grey", "note" to "keep")
                val builder = StringMapChangesBuilder(before)
                answer.applyTo(builder, prefix.removeSuffix(":").ifEmpty { null })
                val after = before.toMutableMap().also { builder.create().applyTo(it) }
                assertEquals("bad", after["${prefix}smoothness"])
                assertEquals("grey", after["${prefix}surface:colour"])
                assertEquals("keep", after["note"])
            }
        }
    }

    @Test fun `removed cycleway clears only its side surface and smoothness`() {
        val before = mapOf("highway" to "residential", "cycleway:both" to "track",
            "cycleway:both:surface" to "asphalt", "cycleway:both:smoothness" to "good",
            "check_date:cycleway:both:surface" to "2001-01-01",
            "check_date:cycleway:right:surface" to "2000-01-01", "sidewalk:right:surface" to "sett")
        val builder = StringMapChangesBuilder(before)
        Sides(null, CyclewayAndDirection(Cycleway.NONE, Direction.FORWARD)).applyTo(builder, false)
        val after = before.toMutableMap().also { builder.create().applyTo(it) }
        assertNull(after["cycleway:right:surface"])
        assertNull(after["cycleway:right:smoothness"])
        assertNull(after["cycleway:both:surface"])
        assertNull(after["check_date:cycleway:right:surface"])
        assertNull(after["check_date:cycleway:both:surface"])
        assertEquals("2001-01-01", after["check_date:cycleway:left:surface"])
        assertEquals("asphalt", after["cycleway:left:surface"])
        assertEquals("good", after["cycleway:left:smoothness"])
        assertEquals("sett", after["sidewalk:right:surface"])
    }

    @Test fun `vegetarian no retains consistent vegan no and closes both surveys`() {
        val quest = AddVegetarian()
        val after = assertQuestContract(quest, DietAvailability.NO,
            mapOf("amenity" to "restaurant", "diet:vegan" to "no"),
            { quest.isApplicableTo(node(tags = it)) == true }, setOf("diet:vegan"))
        assertFalse(AddVegan().isApplicableTo(node(tags = after)))
    }

    @Test fun `oneway excludes legacy contraflow without inferring a direction`() {
        for (value in listOf("opposite", "opposite_lane", "opposite_track", "opposite_share_busway")) {
            assertEquals(false, AddOneway().isApplicableTo(way(tags = mapOf(
                "highway" to "residential", "width" to "3", "cycleway" to value))))
        }
        assertNull(AddOneway().isApplicableTo(way(tags = mapOf("highway" to "residential", "width" to "3"))))
    }

    @Test fun `building material excludes indoor underground and ruined structures`() {
        val quest = AddBuildingMaterial()
        for (extra in listOf(mapOf("indoor" to "yes"), mapOf("location" to "underground"),
            mapOf("building" to "ruins"), mapOf("building:part" to "ruins"), mapOf("ruins" to "yes"))) {
            assertFalse(quest.isApplicableTo(way(tags = mapOf("building" to "yes") + extra)))
        }
        for (extra in listOf(emptyMap(), mapOf("indoor" to "no"))) {
            assertQuestContract(quest, BuildingMaterial.entries.first(), mapOf("building" to "yes") + extra,
                { quest.isApplicableTo(way(tags = it)) == true })
        }
    }
}
