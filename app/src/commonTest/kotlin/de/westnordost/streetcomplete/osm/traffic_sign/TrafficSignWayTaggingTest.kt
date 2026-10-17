package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.FULL
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.PARTIAL
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation.TRAFFIC_SIGN_ONLY
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived.Reason
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.ACCESS_IMPLIED_BY_HIGHWAY
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.CONTRADICTS_SIGN
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.TRAFFIC_SIGN_DIRECTION_CHANGED
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.FOOT_ON_EXCLUSIVE_CYCLEWAY
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.MORE_RESTRICTIVE_GENERAL_ACCESS
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.SEGREGATED_PART_ATTRIBUTE
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.SEGREGATION_NOT_APPLICABLE
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict.Reason.UNCLEAR_ACCESS
import de.westnordost.streetcomplete.osm.cycleway_separate.SeparateCycleway
import de.westnordost.streetcomplete.osm.cycleway_separate.parseSeparateCycleway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrafficSignWayTaggingTest {

    /* -------------------------------- mandatory regression test ------------------------------- */

    @Test fun `footway with bicycles allowed on a shared path`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:239,1022-10", tags)
        assertEquals(
            mapOf(
                "highway" to "footway",
                "foot" to "designated",
                "bicycle" to "yes",
                "traffic_sign" to "DE:239,1022-10",
                // see report: SCEE recognizes "bicycles allowed on footway by sign" only with this
                "bicycle:signed" to "yes",
            ),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
        assertEquals("path" to "footway", r.highwayChange)
        assertEquals(listOf(TrafficSignTagConflict("bicycle", "designated", CONTRADICTS_SIGN)), r.resolvedConflicts)
        assertEquals(emptyList(), r.unresolvedConflicts)
        // consistent with the existing SCEE model of separate cycleways
        assertEquals(SeparateCycleway.ALLOWED_ON_FOOTWAY, parseSeparateCycleway(r.appliedTo(tags)))
    }

    @Test fun `footway with bicycles allowed removes segregated`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "yes")
        val r = derive("DE:239,1022-10", tags)
        assertNull(r.appliedTo(tags)["segregated"])
        assertTrue(TrafficSignTagConflict("segregated", "yes", SEGREGATION_NOT_APPLICABLE) in r.resolvedConflicts)
        assertEquals(FULL, r.derivation)
    }

    @Test fun `footway with bicycles allowed keeps but reports attributes of segregated parts`() {
        val tags = mapOf(
            "highway" to "path", "bicycle" to "designated", "foot" to "designated",
            "segregated" to "yes", "cycleway:surface" to "asphalt", "footway:surface" to "paving_stones",
        )
        val r = derive("DE:239,1022-10", tags)
        val result = r.appliedTo(tags)
        assertEquals("asphalt", result["cycleway:surface"])
        assertEquals("paving_stones", result["footway:surface"])
        assertEquals(
            setOf(
                TrafficSignTagConflict("cycleway:surface", "asphalt", SEGREGATED_PART_ATTRIBUTE),
                TrafficSignTagConflict("footway:surface", "paving_stones", SEGREGATED_PART_ATTRIBUTE),
            ),
            r.unresolvedConflicts.toSet()
        )
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `footway with bicycles allowed on a footway that is already a sidewalk`() {
        val tags = mapOf("highway" to "footway", "footway" to "sidewalk")
        val r = derive("DE:239,1022-10", tags)
        assertEquals(
            mapOf("highway" to "footway", "footway" to "sidewalk", "bicycle" to "yes", "bicycle:signed" to "yes", "traffic_sign" to "DE:239,1022-10"),
            r.appliedTo(tags)
        )
        assertNull(r.highwayChange)
    }

    @Test fun `footway with bicycles allowed replaces restrictive bicycle access`() {
        val tags = mapOf("highway" to "footway", "bicycle" to "no", "bicycle:signed" to "yes")
        val r = derive("DE:239,1022-10", tags)
        assertEquals("yes", r.appliedTo(tags)["bicycle"])
        assertTrue(TrafficSignTagConflict("bicycle", "no", CONTRADICTS_SIGN) in r.resolvedConflicts)
    }

    /* ----------------------------------------- 239 ------------------------------------------- */

    @Test fun `footway`() {
        val tags = mapOf("highway" to "cycleway", "bicycle" to "designated", "foot" to "designated", "segregated" to "no")
        val r = derive("DE:239", tags)
        assertEquals(mapOf("highway" to "footway", "foot" to "designated", "traffic_sign" to "DE:239"), r.appliedTo(tags))
        assertEquals("cycleway" to "footway", r.highwayChange)
        assertEquals(FULL, r.derivation)
        assertEquals(SeparateCycleway.NON_DESIGNATED_ON_FOOTWAY, parseSeparateCycleway(r.appliedTo(tags)))
    }

    @Test fun `footway removes permission to cycle`() {
        val tags = mapOf("highway" to "footway", "bicycle" to "yes", "bicycle:signed" to "yes")
        val r = derive("DE:239", tags)
        assertEquals(mapOf("highway" to "footway", "traffic_sign" to "DE:239"), r.appliedTo(tags))
        assertTrue(TrafficSignTagConflict("bicycle", "yes", CONTRADICTS_SIGN) in r.resolvedConflicts)
    }

    @Test fun `footway keeps prohibition to cycle`() {
        val tags = mapOf("highway" to "footway", "bicycle" to "dismount")
        assertEquals("dismount", derive("DE:239", tags).appliedTo(tags)["bicycle"])
    }

    /* ----------------------------------------- 237 ------------------------------------------- */

    @Test fun `exclusive cycleway`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no")
        val r = derive("DE:237", tags)
        assertEquals(mapOf("highway" to "cycleway", "bicycle" to "designated", "traffic_sign" to "DE:237"), r.appliedTo(tags))
        assertEquals(FULL, r.derivation)
        assertEquals(SeparateCycleway.EXCLUSIVE, parseSeparateCycleway(r.appliedTo(tags)))
    }

    @Test fun `exclusive cycleway replaces restrictive bicycle access`() {
        val tags = mapOf("highway" to "cycleway", "bicycle" to "no", "bicycle:signed" to "no")
        val result = derive("DE:237", tags).appliedTo(tags)
        assertEquals("designated", result["bicycle"])
        assertEquals("yes", result["bicycle:signed"])
    }

    @Test fun `exclusive cycleway reports permission for pedestrians`() {
        val tags = mapOf("highway" to "cycleway", "foot" to "yes")
        val r = derive("DE:237", tags)
        assertEquals("yes", r.appliedTo(tags)["foot"])
        assertEquals(listOf(TrafficSignTagConflict("foot", "yes", FOOT_ON_EXCLUSIVE_CYCLEWAY)), r.unresolvedConflicts)
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `exclusive cycleway on a sidewalk is not derived`() {
        // footway=sidewalk can not be carried over to highway=cycleway
        val tags = mapOf("highway" to "footway", "footway" to "sidewalk")
        val r = derive("DE:237", tags)
        assertOnlyTrafficSign(r, tags, "DE:237")
        assertEquals(listOf(Reason.HIGHWAY_SUBTYPE_PRESENT), r.notDerived.map { it.reason })
    }

    /* -------------------------------------- 240 and 241 -------------------------------------- */

    @Test fun `shared foot and cycleway`() {
        val tags = mapOf("highway" to "path")
        val r = derive("DE:240", tags)
        assertEquals(
            mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no", "traffic_sign" to "DE:240"),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
        assertEquals(SeparateCycleway.NON_SEGREGATED, parseSeparateCycleway(r.appliedTo(tags)))
    }

    @Test fun `shared foot and cycleway does not re-classify highway`() {
        for (highway in listOf("footway", "cycleway")) {
            val tags = mapOf("highway" to highway)
            val r = derive("DE:240", tags)
            assertEquals(highway, r.appliedTo(tags)["highway"])
            assertNull(r.highwayChange)
            assertEquals("no", r.appliedTo(tags)["segregated"])
            assertEquals(SeparateCycleway.NON_SEGREGATED, parseSeparateCycleway(r.appliedTo(tags)))
        }
    }

    @Test fun `shared foot and cycleway changes segregated`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "yes")
        val r = derive("DE:240", tags)
        assertEquals("no", r.appliedTo(tags)["segregated"])
        assertTrue(TrafficSignTagConflict("segregated", "yes", CONTRADICTS_SIGN) in r.resolvedConflicts)
    }

    @Test fun `shared foot and cycleway reports attributes of segregated parts`() {
        val tags = mapOf("highway" to "path", "footway:surface" to "paving_stones")
        val r = derive("DE:240", tags)
        assertEquals(listOf(TrafficSignTagConflict("footway:surface", "paving_stones", SEGREGATED_PART_ATTRIBUTE)), r.unresolvedConflicts)
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `segregated foot and cycleway`() {
        for (value in listOf("DE:241", "DE:241-30", "DE:241-31")) {
            val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no")
            val r = derive(value, tags)
            assertEquals("yes", r.appliedTo(tags)["segregated"], value)
            assertEquals(SeparateCycleway.SEGREGATED, parseSeparateCycleway(r.appliedTo(tags)), value)
        }
        // DE:241 is written as DE:241-30
        assertEquals("DE:241-30", derive("DE:241", mapOf("highway" to "path")).trafficSign)
    }

    @Test fun `segregated foot and cycleway keeps attributes of segregated parts`() {
        val tags = mapOf("highway" to "path", "segregated" to "yes", "footway:surface" to "paving_stones", "bicycle" to "designated", "foot" to "designated")
        val r = derive("DE:241-30", tags)
        assertEquals(mapOf("traffic_sign" to "DE:241-30"), r.appliedTo(tags) - tags.keys)
        assertEquals(1, r.changes.size)
        assertEquals(FULL, r.derivation)
    }

    @Test fun `shared or segregated foot and cycleway on an area is not derived`() {
        val tags = mapOf("highway" to "footway", "area" to "yes")
        for (value in listOf("DE:240", "DE:241-30")) {
            val r = derive(value, tags)
            assertOnlyTrafficSign(r, tags, value)
            assertEquals(listOf(Reason.AREA), r.notDerived.map { it.reason })
        }
    }

    /* ------------------------------ path signs: not applicable -------------------------------- */

    @Test fun `path signs on roads are not derived`() {
        for (value in listOf("DE:237", "DE:239", "DE:239,1022-10", "DE:240", "DE:241-30")) {
            val tags = mapOf("highway" to "residential")
            val r = derive(value, tags)
            assertOnlyTrafficSign(r, tags, value)
            assertEquals(listOf(Reason.UNSUPPORTED_HIGHWAY), r.notDerived.map { it.reason })
        }
    }

    @Test fun `path signs on a bridleway are not derived`() {
        assertEquals(TRAFFIC_SIGN_ONLY, derive("DE:240", mapOf("highway" to "bridleway")).derivation)
    }

    @Test fun `path signs with conditional access are not derived`() {
        val tags = mapOf("highway" to "footway", "bicycle:conditional" to "yes @ (20:00-06:00)")
        val r = derive("DE:239,1022-10", tags)
        assertOnlyTrafficSign(r, tags, "DE:239,1022-10")
        assertEquals(listOf(Reason.CONDITIONAL_TAGS_PRESENT), r.notDerived.map { it.reason })
    }

    @Test fun `signs on a way that is no highway are not derived`() {
        val tags = mapOf("railway" to "platform")
        for (value in listOf("DE:240", "DE:250")) {
            val r = derive(value, tags)
            assertOnlyTrafficSign(r, tags, value)
            assertEquals(listOf(Reason.NOT_A_HIGHWAY), r.notDerived.map { it.reason })
        }
    }

    /* -------------------------------------- access bans ------------------------------------- */

    @Test fun `no vehicles`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle" to "yes", "bicycle" to "yes", "hgv" to "no")
        val r = derive("DE:250", tags)
        assertEquals(mapOf("highway" to "residential", "vehicle" to "no", "hgv" to "no", "traffic_sign" to "DE:250"), r.appliedTo(tags))
        assertEquals(
            setOf(
                TrafficSignTagConflict("motor_vehicle", "yes", CONTRADICTS_SIGN),
                TrafficSignTagConflict("bicycle", "yes", CONTRADICTS_SIGN),
            ),
            r.resolvedConflicts.toSet()
        )
        assertEquals(FULL, r.derivation)
    }

    @Test fun `no vehicles except destination`() {
        val tags = mapOf("highway" to "residential", "bicycle" to "yes", "motorcar" to "destination", "hgv" to "delivery")
        val r = derive("DE:250,1020-30", tags)
        val result = r.appliedTo(tags)
        assertEquals("destination", result["vehicle"])
        assertNull(result["bicycle"])
        assertEquals("destination", result["motorcar"])
        // not clear how delivery relates to destination: kept, but reported
        assertEquals("delivery", result["hgv"])
        assertEquals(listOf(TrafficSignTagConflict("hgv", "delivery", UNCLEAR_ACCESS)), r.unresolvedConflicts)
        // "Anlieger frei" may hint at highway=residential/service, which is not derived
        assertEquals(listOf(Reason.HIGHWAY_CLASSIFICATION_HINT), r.notDerived.map { it.reason })
        assertEquals("residential", result["highway"])
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `no vehicles replaces less restrictive vehicle access`() {
        val tags = mapOf("highway" to "service", "vehicle" to "destination")
        val r = derive("DE:250", tags)
        assertEquals("no", r.appliedTo(tags)["vehicle"])
        assertEquals(listOf(TrafficSignTagConflict("vehicle", "destination", CONTRADICTS_SIGN)), r.resolvedConflicts)
    }

    @Test fun `no vehicles on a cycleway is reported`() {
        val tags = mapOf("highway" to "cycleway")
        val r = derive("DE:250", tags)
        assertEquals("no", r.appliedTo(tags)["vehicle"])
        assertEquals(listOf(TrafficSignTagConflict("highway", "cycleway", ACCESS_IMPLIED_BY_HIGHWAY)), r.unresolvedConflicts)
    }

    @Test fun `no motor vehicles`() {
        val tags = mapOf("highway" to "track", "bicycle" to "yes", "motorcycle" to "yes", "moped" to "no", "motorcar" to "designated")
        val r = derive("DE:260", tags)
        assertEquals(
            mapOf("highway" to "track", "bicycle" to "yes", "motor_vehicle" to "no", "moped" to "no", "traffic_sign" to "DE:260"),
            r.appliedTo(tags)
        )
        assertEquals(FULL, r.derivation)
    }

    @Test fun `no motor vehicles reports more restrictive general access`() {
        val tags = mapOf("highway" to "track", "vehicle" to "no", "access" to "private")
        val r = derive("DE:260", tags)
        assertEquals("no", r.appliedTo(tags)["vehicle"])
        assertEquals("private", r.appliedTo(tags)["access"])
        assertEquals(
            setOf(
                TrafficSignTagConflict("vehicle", "no", MORE_RESTRICTIVE_GENERAL_ACCESS),
                TrafficSignTagConflict("access", "private", MORE_RESTRICTIVE_GENERAL_ACCESS),
            ),
            r.unresolvedConflicts.toSet()
        )
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `no motor vehicles reports exceptions not on the signs`() {
        val tags = mapOf("highway" to "track", "agricultural" to "yes", "taxi" to "yes", "emergency" to "yes")
        val r = derive("DE:260", tags)
        assertEquals(
            setOf(
                TrafficSignTagConflict("agricultural", "yes", UNCLEAR_ACCESS),
                TrafficSignTagConflict("taxi", "yes", UNCLEAR_ACCESS),
            ),
            r.unresolvedConflicts.toSet()
        )
        assertEquals(tags + ("motor_vehicle" to "no") + ("traffic_sign" to "DE:260"), r.appliedTo(tags))
    }

    @Test fun `no motor vehicles except destination`() {
        val tags = mapOf("highway" to "residential")
        assertEquals("destination", derive("DE:260,1020-30", tags).appliedTo(tags)["motor_vehicle"])
    }

    @Test fun `no motor vehicles except agricultural`() {
        val tags = mapOf("highway" to "track", "motor_vehicle" to "no", "agricultural" to "yes", "motorcar" to "yes", "motorcycle" to "destination")
        val r = derive("DE:260,1026-36", tags)
        assertEquals(
            mapOf("highway" to "track", "motor_vehicle" to "agricultural", "agricultural" to "yes", "traffic_sign" to "DE:260,1026-36"),
            r.appliedTo(tags)
        )
        assertEquals(emptyList(), r.unresolvedConflicts)
        assertEquals(listOf(Reason.HIGHWAY_CLASSIFICATION_HINT), r.notDerived.map { it.reason })
        assertEquals(PARTIAL, r.derivation)
    }

    @Test fun `access bans with conditional or lane-dependent access are not derived`() {
        for ((key, reason) in listOf(
            "motor_vehicle:conditional" to Reason.CONDITIONAL_TAGS_PRESENT,
            "access:conditional" to Reason.CONDITIONAL_TAGS_PRESENT,
            "hgv:conditional" to Reason.CONDITIONAL_TAGS_PRESENT,
            "bus:lanes" to Reason.LANE_TAGS_PRESENT,
        )) {
            val tags = mapOf("highway" to "residential", key to "no")
            val r = derive("DE:250", tags)
            assertOnlyTrafficSign(r, tags, "DE:250")
            assertEquals(listOf(reason), r.notDerived.map { it.reason }, key)
        }
    }

    @Test fun `access bans are not affected by unrelated subkeys`() {
        // bicycle:conditional is not affected by a ban for motor vehicles
        val tags = mapOf("highway" to "residential", "bicycle:conditional" to "no @ (wet)", "oneway:bicycle" to "no")
        assertEquals(FULL, derive("DE:260", tags).derivation)
    }

    @Test fun `access ban on an unsupported highway is not derived`() {
        val tags = mapOf("highway" to "platform")
        val r = derive("DE:250", tags)
        assertOnlyTrafficSign(r, tags, "DE:250")
        assertEquals(listOf(Reason.UNSUPPORTED_HIGHWAY), r.notDerived.map { it.reason })
    }

    /* ---------------------------------------- none ------------------------------------------- */

    @Test fun `none only sets traffic_sign`() {
        val tags = mapOf(
            "highway" to "path", "bicycle" to "designated", "foot" to "designated",
            "segregated" to "no", "vehicle" to "no", "traffic_sign" to "DE:240",
        )
        val r = TrafficSignValue.NONE.deriveWayTags(tags)
        assertEquals(listOf(TrafficSignTagChange.Set("traffic_sign", "none", "DE:240")), r.changes)
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation)
        assertEquals(emptyList(), r.notDerived)
        assertEquals(emptyList(), r.resolvedConflicts)
        assertEquals(TRAFFIC_SIGN_ONLY, derive("no", tags).derivation)
        assertEquals("none", derive("no", tags).trafficSign)
    }

    @Test fun `none on untagged way`() {
        val tags = mapOf("highway" to "residential")
        assertEquals(listOf(TrafficSignTagChange.Set("traffic_sign", "none", null)), TrafficSignValue.NONE.deriveWayTags(tags).changes)
    }

    /* --------------------------- unknown, unsupported, contradictory --------------------------- */

    @Test fun `unknown sign only sets traffic_sign`() {
        val tags = mapOf("highway" to "footway")
        val r = derive("DE:239,9999-99", tags)
        assertOnlyTrafficSign(r, tags, "DE:239,9999-99")
        assertEquals(listOf(TrafficSignNotDerived(Reason.UNKNOWN_SIGN, "9999-99")), r.notDerived)
    }

    @Test fun `unknown free text additional sign only sets traffic_sign`() {
        val tags = mapOf("highway" to "residential")
        assertOnlyTrafficSign(derive("DE:250,\"Anwohner frei\"", tags), tags, "DE:250,\"Anwohner frei\"")
    }

    @Test fun `unparseable value is kept as is`() {
        val tags = mapOf("highway" to "residential")
        for (value in listOf("AT:52", "DE:274[30", "250")) {
            val r = derive(value, tags)
            assertOnlyTrafficSign(r, tags, value)
            assertEquals(listOf(Reason.UNPARSEABLE_VALUE), r.notDerived.map { it.reason })
        }
    }

    @Test fun `unsupported combinations only set traffic_sign`() {
        for (value in listOf(
            "DE:237;250", // several independent signs
            "DE:239,1022-10,1012-32", // Radfahrer frei + Radfahrer absteigen (contradictory)
            "DE:240,1000-31", // supported sign with additional sign that is not supported
            "DE:250,1040-30[16:00-18:00]", // time restriction
            "DE:274-30", // not (yet) supported sign
            "DE:244.1", // bicycle road
            "city_limit",
            "DE:1022-10", // additional sign without main sign
        )) {
            val tags = mapOf("highway" to "footway", "bicycle" to "designated")
            val r = derive(value, tags)
            assertOnlyTrafficSign(r, tags, value)
            assertEquals(listOf(Reason.UNSUPPORTED_COMBINATION), r.notDerived.map { it.reason }, value)
        }
    }

    @Test fun `invalid values only set traffic_sign`() {
        val tags = mapOf("highway" to "residential")
        assertEquals(listOf(Reason.INVALID_VALUE), derive("DE:250[3]", tags).notDerived.map { it.reason })
        assertEquals(listOf(Reason.INVALID_VALUE), derive("DE:274", tags).notDerived.map { it.reason })
    }

    @Test fun `existing traffic_sign is replaced`() {
        val tags = mapOf("highway" to "footway", "traffic_sign" to "DE:239")
        val r = derive("DE:274-30", tags)
        assertEquals(listOf(TrafficSignTagChange.Set("traffic_sign", "DE:274-30", "DE:239")), r.changes)
    }

    @Test fun `same signs in both directions replace directional traffic signs`() {
        val tags = mapOf("highway" to "footway", "traffic_sign:forward" to "DE:239", "traffic_sign:backward" to "DE:240")
        val r = derive("DE:239", tags)
        assertEquals(mapOf("highway" to "footway", "traffic_sign" to "DE:239"), r.appliedTo(tags))
        // traffic_sign:forward=DE:239 still applies (now as traffic_sign), so it is no conflict
        assertEquals(
            listOf(TrafficSignTagConflict("traffic_sign:backward", "DE:240", TRAFFIC_SIGN_DIRECTION_CHANGED)),
            r.resolvedConflicts
        )
        assertEquals(FULL, r.derivation)
    }

    /* -------------------------------------- no changes --------------------------------------- */

    @Test fun `already correctly tagged ways are not changed`() {
        for ((value, tags) in listOf(
            "DE:239,1022-10" to mapOf("highway" to "footway", "foot" to "designated", "bicycle" to "yes", "bicycle:signed" to "yes"),
            "DE:239" to mapOf("highway" to "footway"),
            "DE:237" to mapOf("highway" to "cycleway"),
            "DE:240" to mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no"),
            "DE:240" to mapOf("highway" to "footway", "bicycle" to "designated", "segregated" to "no"),
            "DE:241-30" to mapOf("highway" to "cycleway", "foot" to "designated", "segregated" to "yes"),
            "DE:250" to mapOf("highway" to "residential", "vehicle" to "no"),
            "DE:250,1020-30" to mapOf("highway" to "residential", "vehicle" to "destination"),
            "DE:260,1026-36" to mapOf("highway" to "track", "motor_vehicle" to "agricultural"),
        )) {
            val alreadyTagged = tags + ("traffic_sign" to value)
            val r = derive(value, alreadyTagged)
            assertEquals(emptyList(), r.changes, value)
            assertEquals(alreadyTagged, r.appliedTo(alreadyTagged), value)
        }
    }

    /* ------------------------------------- immutability -------------------------------------- */

    @Test fun `input tags are not modified`() {
        val tags = mutableMapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "yes")
        val copy = tags.toMap()
        val r = derive("DE:239,1022-10", tags)
        assertEquals(copy, tags)
        // and the result does not change if the input is modified later
        val changes = r.changes
        tags["highway"] = "cycleway"
        assertEquals(changes, r.changes)
    }

    @Test fun `apply to tags changes builder`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated", "segregated" to "no")
        val builder = StringMapChangesBuilder(tags)
        derive("DE:239,1022-10", tags).applyTo(builder)
        assertEquals("footway", builder["highway"])
        assertEquals("yes", builder["bicycle"])
        assertNull(builder["segregated"])
        assertEquals("DE:239,1022-10", builder["traffic_sign"])
    }

    /* --------------------------- consistency with the catalogue ------------------------------- */

    @Test fun `derived tags agree with the way recommendations of the catalogue`() {
        // highway=* is not compared: shared and segregated paths are not re-classified (see report)
        for (value in listOf("DE:239,1022-10", "DE:240", "DE:241-30", "DE:241-31", "DE:250", "DE:260")) {
            val parsed = parseDE(value)
            val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
            val result = parsed.deriveWayTags(tags).appliedTo(tags)
            for (part in parsed.parts) {
                val recommendation = part.sign!!.tagRecommendations!!.first { "way" in it.geometries }
                for ((k, v) in recommendation.uniqueTags + recommendation.accessTags) {
                    assertEquals(v, result[k], "$value: $k")
                }
            }
        }
    }

    @Test fun `exceptions agree with the modifier values of the catalogue`() {
        for ((value, key) in listOf("DE:250,1020-30" to "vehicle", "DE:260,1020-30" to "motor_vehicle", "DE:260,1026-36" to "motor_vehicle")) {
            val parsed = parseDE(value)
            val modifierValue = parsed.groups.single().modifiers.single().sign!!
                .tagRecommendations!!.first { "way" in it.geometries }.modifierValue
            val tags = mapOf("highway" to "residential")
            assertEquals(modifierValue, parsed.deriveWayTags(tags).appliedTo(tags)[key], value)
        }
    }

    @Test fun `supported signs are listed`() {
        assertEquals(
            setOf(
                listOf("237"), listOf("239"), listOf("239", "1022-10"), listOf("240"), listOf("241-30"), listOf("241-31"),
                listOf("250"), listOf("250", "1020-30"), listOf("260"), listOf("260", "1020-30"), listOf("260", "1026-36"),
            ),
            SUPPORTED_WAY_TRAFFIC_SIGNS.getValue("DE")
        )
        // all of these exist in the catalogue
        for (signs in SUPPORTED_WAY_TRAFFIC_SIGNS.getValue("DE")) {
            for (sign in signs) assertTrue(TrafficSignTestData.catalogueDE.getByOsmValuePart(sign) != null, sign)
        }
    }

    /* ---------------------------------------- helpers ---------------------------------------- */

    private fun derive(value: String, tags: Map<String, String>) = parseDE(value).deriveWayTags(tags)

    private fun TrafficSignWayTagging.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.entries.associate { it.key to it.value }
    }

    private fun assertOnlyTrafficSign(r: TrafficSignWayTagging, tags: Map<String, String>, value: String) {
        assertEquals(TRAFFIC_SIGN_ONLY, r.derivation, value)
        assertEquals(listOf(TrafficSignTagChange.Set("traffic_sign", value, tags["traffic_sign"])), r.changes, value)
        assertNull(r.highwayChange, value)
    }
}
