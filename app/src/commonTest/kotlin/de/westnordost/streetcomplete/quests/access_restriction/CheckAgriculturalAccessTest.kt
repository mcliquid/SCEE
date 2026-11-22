package de.westnordost.streetcomplete.quests.access_restriction

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSign.NO_MOTOR_VEHICLES_WITH_EXCEPTION
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSign.NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSign.NO_VEHICLES_WITH_EXCEPTION
import de.westnordost.streetcomplete.quests.access_restriction.AgriculturalAccessSign.SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.contract.assertQuestContract
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckAgriculturalAccessTest {

    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    private val questType = CheckAgriculturalAccess()

    @Test fun `German signs remain Germany only even in expert mode`() {
        assertEquals(de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept("DE"), questType.enabledInCountries)
        assertFalse(questType.isCountryRestrictionOverridable)
    }

    // region applicability

    @Test fun `applicable to agricultural track`() {
        assertIsApplicable("highway" to "track", "access" to "agricultural")
    }

    @Test fun `applicable to forestry unclassified road`() {
        assertIsApplicable("highway" to "unclassified", "access" to "forestry")
    }

    @Test fun `applicable to combined agricultural and forestry service road`() {
        assertIsApplicable("highway" to "service", "access" to "agricultural;forestry")
    }

    @Test fun `applicable to reversed combined forestry and agricultural residential road`() {
        assertIsApplicable("highway" to "residential", "access" to "forestry;agricultural")
    }

    @Test fun `applicable to living_street, major roads, trunk and highway=road`() {
        assertIsApplicable("highway" to "living_street", "access" to "agricultural")
        assertIsApplicable("highway" to "tertiary", "access" to "forestry")
        assertIsApplicable("highway" to "primary", "access" to "agricultural")
        assertIsApplicable("highway" to "primary_link", "access" to "agricultural")
        assertIsApplicable("highway" to "secondary", "access" to "forestry")
        assertIsApplicable("highway" to "trunk", "access" to "agricultural")
        assertIsApplicable("highway" to "trunk_link", "access" to "forestry")
        assertIsApplicable("highway" to "road", "access" to "agricultural")
        assertIsApplicable("highway" to "busway", "access" to "agricultural")
    }

    @Test fun `Alter Kirchweg style unclassified with bicycle and foot yes is applicable`() {
        assertIsApplicable(
            "highway" to "unclassified",
            "access" to "agricultural",
            "bicycle" to "yes",
            "foot" to "yes",
            "name" to "Alter Kirchweg",
            "surface" to "asphalt",
            "cycleway:both" to "no",
        )
    }

    @Test fun `compatible mode tags do not hide the quest`() {
        assertIsApplicable("highway" to "track", "access" to "agricultural", "foot" to "yes")
        assertIsApplicable("highway" to "track", "access" to "agricultural", "bicycle" to "yes")
        assertIsApplicable("highway" to "service", "access" to "forestry", "horse" to "yes")
        assertIsApplicable("highway" to "residential", "access" to "agricultural", "motorcycle" to "no")
        assertIsApplicable("highway" to "track", "access" to "agricultural", "hgv" to "no")
        assertIsApplicable("highway" to "unclassified", "access" to "agricultural", "bicycle" to "designated")
        assertIsApplicable("highway" to "track", "access" to "forestry", "foot" to "no")
        assertIsApplicable("highway" to "track", "access" to "agricultural", "agricultural" to "yes")
    }

    @Test fun `applicable to path with broad agricultural access`() {
        assertIsApplicable("highway" to "path", "access" to "agricultural")
        assertIsApplicable("highway" to "path", "access" to "forestry;agricultural")
    }

    @Test fun `not applicable to footway and cycleway where other sign families apply`() {
        assertIsNotApplicable("highway" to "footway", "access" to "agricultural")
        assertIsNotApplicable("highway" to "cycleway", "access" to "forestry")
        assertIsNotApplicable("highway" to "bridleway", "access" to "agricultural")
        assertIsNotApplicable("highway" to "pedestrian", "access" to "agricultural")
        assertIsNotApplicable("highway" to "steps", "access" to "forestry")
        assertIsNotApplicable(
            "highway" to "cycleway",
            "access" to "agricultural",
            "bicycle" to "designated",
        )
        assertIsApplicable(
            "highway" to "path",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
        )
        assertIsNotApplicable(
            "highway" to "path",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
            "traffic_sign" to "DE:240,1026-36",
        )
        assertIsNotApplicable(
            "highway" to "footway",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "yes",
        )
    }

    @Test fun `not applicable to motorway`() {
        assertIsNotApplicable("highway" to "motorway", "access" to "agricultural")
        assertIsNotApplicable("highway" to "motorway_link", "access" to "forestry")
    }

    @Test fun `not applicable to bicycle road or cyclestreet`() {
        assertIsNotApplicable("highway" to "residential", "access" to "agricultural", "bicycle_road" to "yes")
        assertIsNotApplicable("highway" to "residential", "access" to "agricultural", "cyclestreet" to "yes")
    }

    @Test fun `not applicable without access or with other access values`() {
        assertIsNotApplicable("highway" to "track")
        assertIsNotApplicable("highway" to "track", "access" to "private")
        assertIsNotApplicable("highway" to "track", "access" to "yes")
        assertIsNotApplicable("highway" to "track", "access" to "destination")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural;destination")
        assertIsNotApplicable("highway" to "track", "access" to "customers")
    }

    @Test fun `not applicable when vehicle or motor_vehicle already exist`() {
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "vehicle" to "agricultural")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "motor_vehicle" to "agricultural")
        assertIsNotApplicable("highway" to "unclassified", "access" to "forestry", "vehicle" to "no")
        assertIsNotApplicable("highway" to "primary", "access" to "agricultural", "motor_vehicle" to "private")
    }

    @Test fun `still applicable when unrelated directional or lane tags exist`() {
        assertIsApplicable("highway" to "track", "access" to "agricultural", "maxspeed:forward" to "30")
        assertIsApplicable("highway" to "unclassified", "access" to "forestry", "turn:lanes" to "left|through")
        assertIsApplicable("highway" to "track", "access" to "agricultural", "source:access" to "survey")
        assertIsApplicable("highway" to "track", "access" to "agricultural", "foot:forward" to "yes")
    }

    @Test fun `not applicable to directional or conditional access on tags this quest mutates`() {
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "access:forward" to "agricultural")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "access:conditional" to "no @ (22:00-06:00)")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "vehicle:backward" to "no")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "bicycle:conditional" to "no @ (22:00-06:00)")
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "motor_vehicle:both_ways" to "no")
    }

    @Test fun `still applicable when traffic_sign already present`() {
        assertIsApplicable("highway" to "track", "access" to "agricultural", "traffic_sign" to "DE:260,1026-36")
        assertIsApplicable("highway" to "track", "access" to "forestry", "traffic_sign" to "DE:274-30")
        assertIsApplicable("highway" to "unclassified", "access" to "agricultural", "traffic_sign:forward" to "DE:250")
    }

    @Test fun `not applicable to barrier on the way`() {
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "barrier" to "gate")
    }

    @Test fun `not applicable to areas or motorroads`() {
        assertIsNotApplicable("highway" to "track", "access" to "agricultural", "area" to "yes")
        assertIsNotApplicable("highway" to "unclassified", "access" to "agricultural", "motorroad" to "yes")
    }

    // endregion

    // region completion filter (DE:240)

    @Test fun `only access agricultural remains applicable without mode or sign tags`() {
        assertIsApplicable("highway" to "track", "access" to "agricultural")
        assertIsApplicable("highway" to "path", "access" to "forestry")
    }

    @Test fun `designated modes without traffic_sign stay applicable for sign survey`() {
        val tags = mapOf(
            "highway" to "track",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
        )
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = tags)))
        assertTrue(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(tags))
        assertEquals(
            setOf(StringMapEntryAdd("traffic_sign", "DE:240,1026-36")),
            questType.answerAppliedTo(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION, tags),
        )
    }

    @Test fun `fully tagged DE 240 way is excluded from filter`() {
        for ((access, sign) in listOf(
            "agricultural" to "DE:240,1026-36",
            "forestry" to "DE:240,1026-37",
            "agricultural;forestry" to "DE:240,1026-38",
            "agricultural" to "DE:240,DE:1026-36",
        )) {
            assertIsNotApplicable(
                "highway" to "track",
                "access" to access,
                "foot" to "designated",
                "bicycle" to "designated",
                "segregated" to "no",
                "traffic_sign" to sign,
            )
            assertFalse(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(mapOf(
                "highway" to "track", "access" to access, "foot" to "designated",
                "bicycle" to "designated", "segregated" to "no", "traffic_sign" to sign,
            )))
        }
    }

    @Test fun `modes matching DE 240 with wrong or road sign traffic_sign remain applicable`() {
        assertIsApplicable(
            "highway" to "track",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
            "traffic_sign" to "DE:260,1026-36",
        )
        assertIsApplicable(
            "highway" to "path",
            "access" to "forestry",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
            "traffic_sign" to "DE:240,1026-36",
        )
    }

    @Test fun `conflicting mode tags block DE 240 answer but other answers may still apply`() {
        val tags = mapOf("highway" to "path", "access" to "agricultural", "bicycle" to "no")
        assertFalse(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(tags))
        assertTrue(NO_MOTOR_VEHICLES_WITH_EXCEPTION.canApplyTo(tags))
    }

    @Test fun `trafficSignMatchesDe240Exception accepts common OSM spellings`() {
        assertTrue(trafficSignMatchesDe240Exception("DE:240,1026-36", "agricultural"))
        assertTrue(trafficSignMatchesDe240Exception("DE:240,DE:1026-36", "agricultural"))
        assertFalse(trafficSignMatchesDe240Exception("DE:240,1026-37", "agricultural"))
        assertFalse(trafficSignMatchesDe240Exception("DE:260,1026-36", "agricultural"))
    }

  /** DE:240 in WAY_RULES sets the same mode tags; the quest additionally keeps access=* and records 1026 plates (not in WAY_RULES). */
    @Test fun `DE 240 quest tagging aligns with traffic sign path rule without vehicle keys`() {
        val questTags = mapOf("highway" to "path", "access" to "agricultural")
        val after = questTags.toMutableMap()
        questType.answerAppliedTo(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION, questTags).forEach { it.applyTo(after) }
        assertEquals(
            mapOf(
                "highway" to "path",
                "access" to "agricultural",
                "foot" to "designated",
                "bicycle" to "designated",
                "segregated" to "no",
                "traffic_sign" to "DE:240,1026-36",
            ),
            after,
        )
        assertNull(after["vehicle"])
        assertNull(after["motor_vehicle"])
    }

    // endregion

    // region tag diffs

    @Test fun `DE 240 with agricultural exception on track`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("foot", "designated"),
                StringMapEntryAdd("bicycle", "designated"),
                StringMapEntryAdd("segregated", "no"),
                StringMapEntryAdd("traffic_sign", "DE:240,1026-36"),
            ),
            questType.answerAppliedTo(
                SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural"),
            )
        )
    }

    @Test fun `DE 240 with agricultural exception on path`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("foot", "designated"),
                StringMapEntryAdd("bicycle", "designated"),
                StringMapEntryAdd("segregated", "no"),
                StringMapEntryAdd("traffic_sign", "DE:240,1026-36"),
            ),
            questType.answerAppliedTo(
                SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION,
                mapOf("highway" to "path", "access" to "agricultural"),
            )
        )
    }

    @Test fun `DE 240 with combined land and forestry exception`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("foot", "designated"),
                StringMapEntryAdd("bicycle", "designated"),
                StringMapEntryAdd("segregated", "no"),
                StringMapEntryAdd("traffic_sign", "DE:240,1026-38"),
            ),
            questType.answerAppliedTo(
                SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION,
                mapOf("highway" to "path", "access" to "agricultural;forestry"),
            )
        )
    }

    /** OSM way 332188656 (fixture, no live API): shared track with agricultural exception. */
    @Test fun `DE 240 fixture way 332188656 keeps access and unrelated tags`() {
        val complete = mapOf(
            "highway" to "track",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
            "traffic_sign" to "DE:240,1026-36",
            "surface" to "asphalt",
            "tracktype" to "grade1",
            "width" to "2",
        )
        assertIsNotApplicable(
            "highway" to "track",
            "access" to "agricultural",
            "foot" to "designated",
            "bicycle" to "designated",
            "segregated" to "no",
            "traffic_sign" to "DE:240,1026-36",
        )
        val before = mapOf(
            "highway" to "track",
            "access" to "agricultural",
            "surface" to "asphalt",
            "tracktype" to "grade1",
            "width" to "2",
        )
        assertIsApplicable(*before.map { it.toPair() }.toTypedArray())
        val after = before.toMutableMap()
        questType.answerAppliedTo(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION, before).forEach { it.applyTo(after) }
        assertEquals(complete - "traffic_sign" + mapOf("traffic_sign" to "DE:240,1026-36"), after)
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = after)))
    }

    @Test fun `DE 250 with agricultural exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-36"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural"),
            )
        )
    }

    @Test fun `DE 250 with forestry exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "forestry"),
                StringMapEntryAdd("vehicle", "forestry"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-37"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "forestry"),
            )
        )
    }

    @Test fun `DE 260 with agricultural exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("motor_vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:260,1026-36"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural"),
            )
        )
    }

    @Test fun `DE 260 with forestry exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "forestry"),
                StringMapEntryAdd("motor_vehicle", "forestry"),
                StringMapEntryAdd("traffic_sign", "DE:260,1026-37"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "forestry"),
            )
        )
    }

    @Test fun `DE 250 with bicycles allowed and agricultural exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
                StringMapEntryAdd("bicycle", "yes"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-36,1022-10"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural"),
            )
        )
    }

    @Test fun `DE 250 with bicycles allowed and forestry exception`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "forestry"),
                StringMapEntryAdd("vehicle", "forestry"),
                StringMapEntryAdd("bicycle", "yes"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-37,1022-10"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "forestry"),
            )
        )
    }

    @Test fun `combined agricultural and forestry uses 1026-38 and keeps the existing value`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural;forestry"),
                StringMapEntryAdd("vehicle", "agricultural;forestry"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-38"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural;forestry"),
            )
        )
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "forestry;agricultural"),
                StringMapEntryAdd("motor_vehicle", "forestry;agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:260,1026-38"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "forestry;agricultural"),
            )
        )
    }

    @Test fun `DE 250 with bicycles allowed and combined agricultural and forestry`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural;forestry"),
                StringMapEntryAdd("vehicle", "agricultural;forestry"),
                StringMapEntryAdd("bicycle", "yes"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-38,1022-10"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "agricultural;forestry"),
            )
        )
    }

    @Test fun `preserves unrelated tags`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-36"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "track",
                    "access" to "agricultural",
                    "name" to "Feldweg",
                    "surface" to "gravel",
                    "tracktype" to "grade3",
                    "width" to "3",
                    "lit" to "no",
                    "smoothness" to "bad",
                ),
            )
        )
    }

    @Test fun `preserves existing foot and bicycle yes on Alter Kirchweg style tags`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("motor_vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:260,1026-36"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "unclassified",
                    "access" to "agricultural",
                    "bicycle" to "yes",
                    "foot" to "yes",
                    "name" to "Alter Kirchweg",
                ),
            )
        )
    }

    @Test fun `preserves bicycle yes when the bicycle-allowed sign is selected`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-36,1022-10"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
                mapOf(
                    "highway" to "unclassified",
                    "access" to "agricultural",
                    "bicycle" to "yes",
                    "foot" to "yes",
                ),
            )
        )
    }

    @Test fun `preserves bicycle designated instead of downgrading it to yes`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:250,1026-36,1022-10"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION,
                mapOf(
                    "highway" to "track",
                    "access" to "agricultural",
                    "bicycle" to "designated",
                ),
            )
        )
    }

    @Test fun `explicit bicycle-allowed sign never overwrites a conflicting bicycle restriction`() {
        for (value in listOf("no", "private", "destination", "agricultural", "forestry",
            "agricultural;forestry", "permissive", "use_sidepath", "dismount", "unknown")) {
            val tags = mapOf("highway" to "track", "access" to "agricultural", "bicycle" to value)
            assertFalse(NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION.canApplyTo(tags))
            assertEquals(emptySet(), questType.answerAppliedTo(NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION, tags))
        }
    }

    @Test fun `DE 240 never overwrites conflicting foot or bicycle restrictions`() {
        for (foot in listOf("no", "private", "agricultural", "use_sidepath")) {
            val tags = mapOf("highway" to "path", "access" to "agricultural", "foot" to foot)
            assertFalse(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(tags))
            assertEquals(emptySet(), questType.answerAppliedTo(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION, tags))
        }
        for (bicycle in listOf("no", "private", "forestry", "dismount")) {
            val tags = mapOf("highway" to "path", "access" to "forestry", "bicycle" to bicycle)
            assertFalse(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(tags))
            assertEquals(emptySet(), questType.answerAppliedTo(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION, tags))
        }
        assertFalse(SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION.canApplyTo(
            mapOf("highway" to "track", "access" to "agricultural", "segregated" to "yes"),
        ))
    }

    @Test fun `preserves more-specific access tags if they are already present`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("motor_vehicle", "agricultural"),
                StringMapEntryAdd("traffic_sign", "DE:260,1026-36"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "track",
                    "access" to "agricultural",
                    "foot" to "yes",
                    "horse" to "yes",
                    "motorcycle" to "no",
                ),
            )
        )
    }

    @Test fun `does not overwrite an existing matching traffic_sign`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("motor_vehicle", "agricultural"),
            ),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "track",
                    "access" to "agricultural",
                    "traffic_sign" to "DE:260,1026-36",
                ),
            )
        )
    }

    @Test fun `does not overwrite an existing unrelated traffic_sign`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "agricultural"),
                StringMapEntryAdd("vehicle", "agricultural"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "unclassified",
                    "access" to "agricultural",
                    "traffic_sign" to "DE:274-30",
                    "maxspeed" to "30",
                ),
            )
        )
    }

    @Test fun `does not overwrite directional traffic_sign subkeys`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("access", "forestry"),
                StringMapEntryAdd("vehicle", "forestry"),
            ),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf(
                    "highway" to "track",
                    "access" to "forestry",
                    "traffic_sign:forward" to "DE:274",
                ),
            )
        )
    }

    @Test fun `does not invent an exception when access is missing or unsupported`() {
        assertEquals(
            emptySet(),
            questType.answerAppliedTo(
                NO_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track"),
            )
        )
        assertEquals(
            emptySet(),
            questType.answerAppliedTo(
                NO_MOTOR_VEHICLES_WITH_EXCEPTION,
                mapOf("highway" to "track", "access" to "private"),
            )
        )
    }

    // endregion

    @Test fun `additional traffic sign ids`() {
        assertEquals("1026-36", additionalTrafficSignId("agricultural"))
        assertEquals("1026-37", additionalTrafficSignId("forestry"))
        assertEquals("1026-38", additionalTrafficSignId("agricultural;forestry"))
        assertEquals("1026-38", additionalTrafficSignId("forestry;agricultural"))
        assertNull(additionalTrafficSignId("private"))
        assertNull(additionalTrafficSignId("agricultural;destination"))
    }

    @Test fun `additional sign text uses official hyphenation`() {
        assertEquals("landwirtschaft-\nlicher Verkehr\nfrei", additionalSignText("agricultural"))
        assertEquals("forstwirtschaft-\nlicher Verkehr\nfrei", additionalSignText("forestry"))
        assertEquals(
            "Land- und forst-\nwirtschaftlicher\nVerkehr frei",
            additionalSignText("agricultural;forestry"),
        )
    }

    @Test fun `detects existing traffic sign keys`() {
        assertFalse(hasExistingTrafficSign(mapOf("highway" to "track")))
        assertTrue(hasExistingTrafficSign(mapOf("traffic_sign" to "DE:260,1026-36")))
        assertTrue(hasExistingTrafficSign(mapOf("traffic_sign:forward" to "DE:274")))
        assertTrue(hasExistingTrafficSign(mapOf("traffic_sign:backward" to "DE:250")))
    }

    @Test fun `all sign and exception combinations close quest without losing tags`() {
        for ((access, plate) in listOf("agricultural" to "1026-36", "forestry" to "1026-37",
            "agricultural;forestry" to "1026-38", "forestry;agricultural" to "1026-38")) {
            for (answer in AgriculturalAccessSign.entries) {
                val before = mapOf("highway" to "track", "access" to access,
                    "foot" to "no", "horse" to "private", "motorcycle" to "no",
                    "surface" to "gravel", "name" to "Waldweg", "note" to "keep")
                val answerBefore = if (answer == SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION) before - "foot" else before
                val preserveKeys = if (answer == SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION) {
                    answerBefore.keys
                } else {
                    answerBefore.keys - "access"
                }
                val after = assertQuestContract(
                    questType, answer, answerBefore,
                    { questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = it)) },
                    preserveKeys,
                )
                when (answer) {
                    SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION -> assertEquals(answerBefore + mapOf(
                        "foot" to "designated", "bicycle" to "designated", "segregated" to "no",
                        "traffic_sign" to "DE:240,$plate",
                        "source:ref" to "survey reference",
                    ), after)
                    NO_MOTOR_VEHICLES_WITH_EXCEPTION -> assertEquals((answerBefore - "access") + mapOf(
                        "motor_vehicle" to access, "traffic_sign" to "DE:260,$plate",
                        "source:ref" to "survey reference",
                    ), after)
                    NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION -> assertEquals((answerBefore - "access") + mapOf(
                        "vehicle" to access, "bicycle" to "yes",
                        "traffic_sign" to "DE:250,$plate,1022-10",
                        "source:ref" to "survey reference",
                    ), after)
                    NO_VEHICLES_WITH_EXCEPTION -> assertEquals((answerBefore - "access") + mapOf(
                        "vehicle" to access, "traffic_sign" to "DE:250,$plate",
                        "source:ref" to "survey reference",
                    ), after)
                }
            }
        }
    }

    @Test fun `existing vehicle and motor vehicle restrictions are never overwritten even outside filter`() {
        for (key in listOf("vehicle", "motor_vehicle")) {
            for (value in listOf("agricultural", "forestry", "agricultural;forestry", "no", "private", "yes", "destination")) {
                for (answer in AgriculturalAccessSign.entries) {
                    val before = mapOf("highway" to "track", "access" to "agricultural", key to value)
                    assertFalse(answer.canApplyTo(before))
                    assertEquals(emptySet(), questType.answerAppliedTo(answer, before))
                }
            }
        }
    }

    @Test fun `conditional and directional access remains untouched even outside filter`() {
        for (key in listOf("access:conditional", "vehicle:forward", "motor_vehicle:backward", "bicycle:conditional")) {
            for (answer in AgriculturalAccessSign.entries) {
                val before = mapOf("highway" to "track", "access" to "forestry", key to "no")
                assertFalse(answer.canApplyTo(before))
                assertEquals(emptySet(), questType.answerAppliedTo(answer, before))
            }
        }
    }

    @Test fun `other sign answers preserve all existing bicycle values`() {
        for (bicycle in listOf("no", "private", "destination", "agricultural", "forestry", "yes",
            "designated", "official", "permissive", "use_sidepath", "dismount")) {
            for (answer in listOf(NO_VEHICLES_WITH_EXCEPTION, NO_MOTOR_VEHICLES_WITH_EXCEPTION)) {
                val before = mapOf("highway" to "track", "access" to "agricultural", "bicycle" to bicycle)
                val after = before.toMutableMap()
                questType.answerAppliedTo(answer, before).forEach { it.applyTo(after) }
                assertEquals(bicycle, after["bicycle"])
                assertNull(after["access"])
            }
        }
    }

    @Test fun `bicycle exemption preserves existing public access values`() {
        for (bicycle in listOf("yes", "designated", "official")) {
            val before = mapOf("highway" to "track", "access" to "forestry", "bicycle" to bicycle)
            assertTrue(NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION.canApplyTo(before))
            val after = before.toMutableMap()
            questType.answerAppliedTo(NO_VEHICLES_BICYCLES_ALLOWED_WITH_EXCEPTION, before).forEach { it.applyTo(after) }
            assertEquals(bicycle, after["bicycle"])
            assertNull(after["access"])
        }
    }

    @Test fun `unsupported and missing access values produce no edits for every answer`() {
        for (access in listOf(null, "no", "private", "agricultural;destination", "agricultural; forestry", "AGRICULTURAL", "")) {
            for (answer in AgriculturalAccessSign.entries) {
                val before = mapOf("highway" to "track") + (access?.let { mapOf("access" to it) } ?: emptyMap())
                assertEquals(emptySet(), questType.answerAppliedTo(answer, before))
            }
        }
    }

    @Test fun `unchanged OSM data remains eligible without fake survey date or suppression tags`() {
        val before = mapOf("highway" to "track", "access" to "agricultural")
        repeat(2) { assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = before))) }
        for (answer in AgriculturalAccessSign.entries) {
            val changes = questType.answerAppliedTo(answer, before)
            assertTrue(changes.none { it.key.startsWith("check_date") || it.key.startsWith("survey:") })
        }
    }

    @Test fun `changed highway or road restrictions prevent stale answers`() {
        val before = mapOf("highway" to "track", "access" to "agricultural")
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = before)))
        val changes = listOf<Map<String, String>>(
            emptyMap(), mapOf("area" to "yes"), mapOf("motorroad" to "yes"),
            mapOf("bicycle_road" to "yes"), mapOf("cyclestreet" to "yes"),
            mapOf("barrier" to "gate"),
        ) + agriculturalAccessExcludedHighways.split('|').map { mapOf("highway" to it) }
        for (change in changes) {
            val updated = if (change.isEmpty()) before - "highway" else before + change
            assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = updated)))
            for (answer in AgriculturalAccessSign.entries) {
                assertFalse(answer.canApplyTo(updated))
                assertEquals(emptySet(), questType.answerAppliedTo(answer, updated))
            }
        }
    }

    @Test fun `combined exceptions preserve all specific mode access tags`() {
        for (exception in listOf("agricultural;forestry", "forestry;agricultural")) {
            for (answer in AgriculturalAccessSign.entries) {
                val specific = mapOf("foot" to "no", "horse" to "private", "motorcar" to "destination",
                    "motorcycle" to "no", "hgv" to "no", "bicycle" to "designated")
                val answerBefore = if (answer == SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION) {
                    mapOf("highway" to "track", "access" to exception) + (specific - "foot")
                } else {
                    mapOf("highway" to "track", "access" to exception) + specific
                }
                val after = answerBefore.toMutableMap()
                questType.answerAppliedTo(answer, answerBefore).forEach { it.applyTo(after) }
                when (answer) {
                    SHARED_FOOT_AND_CYCLE_WITH_EXCEPTION -> {
                        assertEquals(exception, after["access"])
                        assertTrue(specific.filterKeys { it != "foot" }.all { (key, value) -> after[key] == value })
                        assertEquals("designated", after["foot"])
                        assertEquals("designated", after["bicycle"])
                        assertEquals("no", after["segregated"])
                    }
                    else -> {
                        assertTrue(specific.all { (key, value) -> after[key] == value })
                        assertNull(after["access"])
                    }
                }
                assertTrue(after.getValue("traffic_sign").contains("1026-38"))
            }
        }
    }

    private fun assertIsApplicable(vararg pairs: Pair<String, String>) {
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }

    private fun assertIsNotApplicable(vararg pairs: Pair<String, String>) {
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }
}
