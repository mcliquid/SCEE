package de.westnordost.streetcomplete.osm.cycleway

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.visiblequests.QuestsHiddenController
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway.*
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath.BACKWARD
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath.BOTH
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath.FORWARD
import de.westnordost.streetcomplete.osm.cycleway.RoadSidepath.NONE
import de.westnordost.streetcomplete.osm.oneway.Direction
import de.westnordost.streetcomplete.quests.sidepath_usage.AddSidepathUsage
import de.westnordost.streetcomplete.testutils.way
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SidepathTest {

    private val road = mapOf("highway" to "secondary")
    private val USE = "use_sidepath"

    /* ----------------------------------- the three answers ---------------------------------- */

    @Test fun `answers on a two-way road`() {
        assertEquals(listOf(FORWARD, BACKWARD, BOTH), getSidepathAnswers(road, false))
        assertEquals(mapOf("bicycle:forward" to USE), written(road, FORWARD))
        assertEquals(mapOf("bicycle:backward" to USE), written(road, BACKWARD))
        assertEquals(mapOf("bicycle" to USE), written(road, BOTH))
    }

    @Test fun `direction does not depend on the driving side`() {
        for (lht in listOf(false, true)) {
            assertEquals(mapOf("bicycle:forward" to USE), written(road, FORWARD, lht))
            assertEquals(mapOf("bicycle:backward" to USE), written(road, BACKWARD, lht))
            assertEquals(mapOf("bicycle" to USE), written(road, BOTH, lht))
        }
    }

    @Test fun `an answer that is not allowed is not written`() {
        assertEquals(
            mapOf("bicycle:backward" to "no"),
            written(road + ("bicycle:backward" to "no"), BOTH)
        )
        assertEquals(
            mapOf("bicycle:backward" to "no"),
            written(road + ("bicycle:backward" to "no"), BACKWARD)
        )
        assertEquals(emptyMap(), written(road + ("oneway" to "yes"), BACKWARD))
    }

    /* ----------------------------------- change of the answer ------------------------------- */

    @Test fun `answer for one direction leaves no use_sidepath for the other`() {
        assertEquals(
            mapOf("bicycle:backward" to USE),
            written(road + ("bicycle:forward" to USE), BACKWARD)
        )
        assertEquals(
            mapOf("bicycle:forward" to USE),
            written(road + ("bicycle:backward" to USE), FORWARD)
        )
        // a global value is split, the remaining direction is kept
        assertEquals(mapOf("bicycle:forward" to USE), written(road + ("bicycle" to USE), FORWARD))
        assertEquals(mapOf("bicycle:backward" to USE), written(road + ("bicycle" to USE), BACKWARD))
    }

    @Test fun `second direction is merged into the global value`() {
        assertEquals(mapOf("bicycle" to USE), written(road + ("bicycle:forward" to USE), BOTH))
        assertEquals(mapOf("bicycle" to USE), written(road + ("bicycle:backward" to USE), BOTH))
        assertEquals(mapOf("bicycle" to USE), written(road + ("bicycle" to USE), BOTH))
        assertEquals(
            mapOf("bicycle" to USE),
            written(road + mapOf("bicycle:forward" to USE, "bicycle:backward" to USE), BOTH)
        )
    }

    @Test fun `changing the answer needs a confirmation if a use_sidepath is removed`() {
        assertTrue(needsSidepathChangeConfirmation(road + ("bicycle" to USE), FORWARD, false))
        assertTrue(needsSidepathChangeConfirmation(road + ("bicycle:forward" to USE), BACKWARD, false))
        assertTrue(needsSidepathChangeConfirmation(road + ("bicycle:backward" to USE), FORWARD, false))
        // nothing is removed
        assertFalse(needsSidepathChangeConfirmation(road + ("bicycle:forward" to USE), BOTH, false))
        assertFalse(needsSidepathChangeConfirmation(road + ("bicycle" to USE), BOTH, false))
        assertFalse(needsSidepathChangeConfirmation(road + ("bicycle:forward" to USE), FORWARD, false))
        assertFalse(needsSidepathChangeConfirmation(road, BOTH, false))
    }

    @Test fun `existing answer is read from the tags`() {
        assertNull(getExistingRoadSidepath(road))
        assertEquals(FORWARD, getExistingRoadSidepath(road + ("bicycle:forward" to USE)))
        assertEquals(BACKWARD, getExistingRoadSidepath(road + ("bicycle:backward" to USE)))
        assertEquals(BOTH, getExistingRoadSidepath(road + ("bicycle" to USE)))
        assertEquals(
            FORWARD,
            getExistingRoadSidepath(road + mapOf("bicycle" to USE, "bicycle:backward" to "no"))
        )
        assertNull(getExistingRoadSidepath(road + ("bicycle" to "yes")))
        assertNull(getExistingRoadSidepath(road + ("bicycle" to "optional_sidepath")))
    }

    /* ----------------------------------------- oneways -------------------------------------- */

    @Test fun `oneway only asks about the direction of the flow`() {
        assertEquals(listOf(FORWARD), getSidepathAnswers(road + ("oneway" to "yes"), false))
        assertEquals(listOf(BACKWARD), getSidepathAnswers(road + ("oneway" to "-1"), false))
        assertEquals(mapOf("bicycle:forward" to USE), written(road + ("oneway" to "yes"), FORWARD))
        assertEquals(mapOf("bicycle:backward" to USE), written(road + ("oneway" to "-1"), BACKWARD))
        // the arrows follow the way direction, so a reversed way has the other direction
        assertEquals(emptyMap(), written(road + ("oneway" to "-1"), FORWARD))
    }

    @Test fun `oneway that is open for cyclists in contraflow asks about both directions`() {
        for (tags in listOf(
            road + mapOf("oneway" to "yes", "oneway:bicycle" to "no"),
            road + mapOf("oneway" to "-1", "oneway:bicycle" to "no"),
            road + mapOf("oneway" to "yes", "cycleway:left" to "opposite_lane"),
        )) {
            assertEquals(listOf(FORWARD, BACKWARD, BOTH), getSidepathAnswers(tags, false), tags.toString())
        }
        assertEquals(
            mapOf("bicycle" to USE),
            written(road + mapOf("oneway" to "yes", "oneway:bicycle" to "no"), BOTH).filterKeys { it == "bicycle" }
        )
    }

    @Test fun `roundabout is a oneway`() {
        assertEquals(listOf(FORWARD), getSidepathAnswers(road + ("junction" to "roundabout"), false))
    }

    /* ------------------------------------- protected tags ----------------------------------- */

    @Test fun `other bicycle values protect the road`() {
        for (value in listOf(
            "yes", "no", "designated", "dismount", "private", "permissive", "destination", "customers",
            "permit", "delivery", "official", "discouraged", "agricultural", "optional_sidepath"
        )) {
            val bare = road + ("bicycle" to value)
            assertEquals(emptyList(), getSidepathAnswers(bare, false), "bicycle=$value")
            assertEquals(mapOf("bicycle" to value), written(bare, BOTH), "bicycle=$value")
            val directional = road + ("bicycle:forward" to value)
            assertEquals(listOf(BACKWARD), getSidepathAnswers(directional, false), "forward=$value")
            assertEquals(
                mapOf("bicycle:forward" to value, "bicycle:backward" to USE),
                written(directional, BACKWARD), "forward=$value"
            )
        }
    }

    @Test fun `optional_sidepath is never written nor changed`() {
        val tags = road + ("bicycle:forward" to "optional_sidepath")
        assertEquals(mapOf("bicycle:forward" to "optional_sidepath"), written(tags, BOTH))
        assertEquals(
            mapOf("bicycle:forward" to "optional_sidepath", "bicycle:backward" to USE),
            written(tags, BACKWARD)
        )
        // removal does not touch it either
        assertEquals(
            mapOf("bicycle:forward" to "optional_sidepath"),
            written(tags + ("bicycle:backward" to USE), NONE)
        )
        assertTrue(hasSidepathTagging(tags))
        assertTrue(hasSidepathTagging(road + ("bicycle" to "optional_sidepath")))
        assertFalse(hasSidepathTagging(road + ("bicycle" to "yes")))
    }

    @Test fun `bare tag use_sidepath and another value for one direction`() {
        val tags = road + mapOf("bicycle" to USE, "bicycle:backward" to "no")
        assertEquals(listOf(FORWARD), getSidepathAnswers(tags, false))
        // the other direction is not taken along
        // an equivalent, but explicit tagging: bicycle:backward=no overrides the global value anyway
        assertEquals(mapOf("bicycle:forward" to USE, "bicycle:backward" to "no"), written(tags, FORWARD))
        assertEquals(mapOf("bicycle:backward" to "no"), written(tags, NONE))
    }

    @Test fun `bicycle yes with a directional use_sidepath`() {
        val tags = road + mapOf("bicycle" to "yes", "bicycle:forward" to USE)
        assertEquals(listOf(FORWARD), getSidepathAnswers(tags, false))
        assertEquals(mapOf("bicycle" to "yes"), written(tags, NONE))
        assertEquals(mapOf("bicycle" to "yes"), written(tags, BACKWARD).filterKeys { it == "bicycle" })
    }

    @Test fun `conditional bicycle tags protect the road`() {
        for (key in listOf("bicycle:conditional", "bicycle:forward:conditional", "bicycle:backward:conditional")) {
            val tags = road + (key to "no @ (Su)")
            assertEquals(emptyList(), getSidepathAnswers(tags, false), key)
            assertEquals(mapOf(key to "no @ (Su)"), written(tags, BOTH), key)
        }
    }

    @Test fun `not applicable roads`() {
        for (tags in listOf(
            mapOf("highway" to "motorway"),
            mapOf("highway" to "track"),
            mapOf("highway" to "living_street"),
            mapOf("highway" to "cycleway"),
            road + ("motorroad" to "yes"),
            road + ("expressway" to "yes"),
            road + ("bicycle_road" to "yes"),
            road + ("cyclestreet" to "yes"),
            road + ("area" to "yes"),
            road + ("vehicle" to "no"),
            road + ("access" to "private"),
        )) {
            assertEquals(emptyList(), getSidepathAnswers(tags, false), tags.toString())
        }
        assertTrue(getSidepathAnswers(road + ("vehicle" to "yes"), false).isNotEmpty())
        assertTrue(getSidepathAnswers(road + ("access" to "yes"), false).isNotEmpty())
    }

    /* ----------------------------------- removal after confirmation -------------------------- */

    @Test fun `none removes use_sidepath`() {
        assertEquals(emptyMap(), written(road + ("bicycle" to USE), NONE))
        assertEquals(emptyMap(), written(road + ("bicycle:forward" to USE), NONE))
        assertEquals(
            emptyMap(),
            written(road + mapOf("bicycle:forward" to USE, "bicycle:backward" to USE), NONE)
        )
        assertEquals(emptyMap(), written(road, NONE))
    }

    @Test fun `stale use_sidepath after the separate cycleway has been changed`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to USE)
        val original = Sides(null, side(SEPARATE, Direction.FORWARD))
        val track = Sides(null, side(TRACK, Direction.FORWARD))
        assertTrue(needsStaleSidepathRemoval(tags, original, track))
        // still a separate cycleway
        assertFalse(needsStaleSidepathRemoval(tags, original, original))
        // another side is still separate
        val both = Sides(side(SEPARATE, Direction.BACKWARD), side(SEPARATE, Direction.FORWARD))
        val oneChanged = Sides(side(SEPARATE, Direction.BACKWARD), side(TRACK, Direction.FORWARD))
        assertFalse(needsStaleSidepathRemoval(tags, both, oneChanged))
        // nothing to remove
        assertFalse(needsStaleSidepathRemoval(road + ("cycleway:right" to "separate"), original, track))
        // not changed away from separate
        assertFalse(needsStaleSidepathRemoval(tags, track, track))
    }

    @Test fun `stale use_sidepath that is implied only`() {
        // use_sidepath without cycleway tags is parsed as separate on both sides
        val tags = road + ("bicycle" to USE)
        val original = parseCyclewaySides(tags, false)!!
        val none = Sides(side(Cycleway.NONE, Direction.BACKWARD), side(Cycleway.NONE, Direction.FORWARD))
        assertTrue(needsStaleSidepathRemoval(tags, original, none))
    }

    @Test fun `removal is only done with the confirmed answer`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle" to USE)
        val changed = Sides(null, side(Cycleway.NONE, Direction.FORWARD))
        // declined or not asked: nothing happens to bicycle tags
        assertEquals(mapOf("bicycle" to USE), applied(tags, changed))
        assertEquals(mapOf("bicycle" to USE), applied(tags, changed.withRoadSidepath(null)))
        // confirmed
        assertEquals(emptyMap(), applied(tags, changed.withRoadSidepath(NONE)))
    }

    /* ----------------------------------------- creator -------------------------------------- */

    @Test fun `creator writes the answer together with the separate cycleway`() {
        val result = fullyApplied(road, Sides(null, side(SEPARATE, Direction.FORWARD)).withRoadSidepath(FORWARD))
        assertEquals("separate", result["cycleway:right"])
        assertEquals(USE, result["bicycle:forward"])
        assertNull(result["bicycle:backward"])
        assertNull(result["bicycle"])
    }

    @Test fun `creator writes the answer for both sides`() {
        val sides = Sides(side(SEPARATE, Direction.BACKWARD), side(SEPARATE, Direction.FORWARD)).withRoadSidepath(BOTH)
        val result = fullyApplied(road, sides)
        assertEquals("separate", result["cycleway:both"])
        assertEquals(USE, result["bicycle"])
    }

    @Test fun `creator ignores an answer if there is no separate cycleway`() {
        assertEquals(
            emptyMap(),
            applied(road, Sides(null, side(TRACK, Direction.FORWARD)).withRoadSidepath(FORWARD))
        )
        assertEquals(
            emptyMap(),
            applied(road, Sides(side(Cycleway.NONE, Direction.BACKWARD), side(Cycleway.NONE, Direction.FORWARD)).withRoadSidepath(BOTH))
        )
    }

    @Test fun `creator uses a separate cycleway that is on the side that is not changed`() {
        val tags = road + ("cycleway:left" to "separate")
        val sides = Sides(null, side(TRACK, Direction.FORWARD)).withRoadSidepath(BACKWARD)
        assertEquals(mapOf("bicycle:backward" to USE), applied(tags, sides))
    }

    @Test fun `creator ignores contradicting answers on the sides`() {
        val sides = Sides(
            side(SEPARATE, Direction.BACKWARD).copy(roadSidepath = FORWARD),
            side(SEPARATE, Direction.FORWARD).copy(roadSidepath = BACKWARD),
        )
        assertEquals(emptyMap(), applied(road, sides))
    }

    @Test fun `no answer does not change existing tags`() {
        val tags = road + mapOf("cycleway:right" to "separate", "bicycle:forward" to USE)
        assertEquals(mapOf("bicycle:forward" to USE), applied(tags, Sides(null, side(SEPARATE, Direction.FORWARD))))
        assertEquals(mapOf("bicycle:forward" to USE), applied(tags, Sides(null, side(TRACK, Direction.FORWARD))))
    }

    @Test fun `creator keeps protected values`() {
        val tags = road + mapOf("bicycle" to "yes")
        val sides = Sides(null, side(SEPARATE, Direction.FORWARD)).withRoadSidepath(BOTH)
        assertEquals(mapOf("bicycle" to "yes"), applied(tags, sides))
    }

    @Test fun `parser does not read the answer`() {
        for (tags in listOf(
            mapOf("cycleway:right" to "separate"),
            mapOf("cycleway:right" to "separate", "bicycle:forward" to USE),
            mapOf("cycleway:both" to "separate", "bicycle" to USE),
        )) {
            val parsed = parseCyclewaySides(tags, false)!!
            assertNull(parsed.left?.roadSidepath)
            assertNull(parsed.right?.roadSidepath)
        }
    }

    /* --------------------------------------- follow-up -------------------------------------- */

    @Test fun `question only in Germany and the Netherlands`() {
        val sides = Sides(null, side(SEPARATE, Direction.FORWARD))
        for (country in listOf("DE", "NL")) {
            assertTrue(needsSidepathQuestion(road, sides, sides, false, country), country)
        }
        for (country in listOf("GB", "US", "AT", "FR", "PL", "IT", "FI", "DK", "JP", null)) {
            assertFalse(needsSidepathQuestion(road, sides, sides, false, country), country.toString())
        }
    }

    @Test fun `question only for separate cycleways and if an answer may be written`() {
        val track = Sides(null, side(TRACK, Direction.FORWARD))
        assertFalse(needsSidepathQuestion(road, track, track, false, "DE"))
        val separate = Sides(null, side(SEPARATE, Direction.FORWARD))
        assertFalse(needsSidepathQuestion(road + ("bicycle" to "yes"), separate, separate, false, "DE"))
        assertFalse(needsSidepathQuestion(road + ("bicycle:conditional" to "no @ (Su)"), separate, separate, false, "DE"))
        // also if only the original side is separate
        assertTrue(needsSidepathQuestion(road, separate, Sides(null, null), false, "DE"))
    }

    @Test fun `question for an existing use_sidepath too`() {
        val separate = Sides(null, side(SEPARATE, Direction.FORWARD))
        assertTrue(needsSidepathQuestion(road + ("bicycle:forward" to USE), separate, separate, false, "DE"))
    }

    @Test fun `follow-up is needed for a question or a stale tag`() {
        val separate = Sides(null, side(SEPARATE, Direction.FORWARD))
        val track = Sides(null, side(TRACK, Direction.FORWARD))
        assertTrue(needsSidepathFollowUp(road, separate, separate, false, "DE"))
        assertFalse(needsSidepathFollowUp(road, separate, separate, false, "GB"))
        assertFalse(needsSidepathFollowUp(road, separate, track, false, "DE"))
        // the stale tag does not depend on the country
        assertTrue(needsSidepathFollowUp(road + ("bicycle:forward" to USE), separate, track, false, "GB"))
    }

    @Test fun `the answer is attached to the sides`() {
        val sides = Sides(side(SEPARATE, Direction.BACKWARD), null).withRoadSidepath(BOTH)
        assertEquals(BOTH, sides.roadSidepath)
        assertNull(sides.right)
        assertNull(Sides(side(SEPARATE, Direction.BACKWARD), null).roadSidepath)
    }

    /* ------------------------------------ choices and hiding ---------------------------------- */

    @Test fun `no and cannot tell is always the last choice`() {
        assertEquals(
            listOf(SidepathChoice.FORWARD, SidepathChoice.BACKWARD, SidepathChoice.BOTH, SidepathChoice.NO_OR_UNKNOWN),
            getSidepathChoices(listOf(FORWARD, BACKWARD, BOTH))
        )
        assertEquals(
            listOf(SidepathChoice.BACKWARD, SidepathChoice.NO_OR_UNKNOWN),
            getSidepathChoices(listOf(BACKWARD))
        )
        assertEquals(listOf(SidepathChoice.NO_OR_UNKNOWN), getSidepathChoices(emptyList()))
        assertNull(SidepathChoice.NO_OR_UNKNOWN.roadSidepath)
        assertEquals(BOTH, SidepathChoice.BOTH.roadSidepath)
    }

    @Test fun `hiding uses the key of the quest type and element`() {
        assertEquals(AddSidepathUsage::class.simpleName, SIDEPATH_USAGE_QUEST_NAME)
        val controller = mock<QuestsHiddenController>()
        controller.hideSidepathUsageQuest(way(12, tags = road))
        verify { controller.hide(OsmQuestKey(ElementType.WAY, 12, "AddSidepathUsage")) }
    }

    /* ------------------------------------------ helpers ------------------------------------- */

    private fun side(cycleway: Cycleway, direction: Direction) = CyclewayAndDirection(cycleway, direction)

    /** the bicycle tags after applying the answer to the road */
    private fun written(tags: Map<String, String>, answer: RoadSidepath, lht: Boolean = false): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        builder.applyRoadSidepath(answer, lht)
        return builder.toMap().bicycleTags()
    }

    /** the bicycle tags after applying the sides with the creator */
    private fun applied(tags: Map<String, String>, sides: Sides<CyclewayAndDirection>): Map<String, String> =
        fullyApplied(tags, sides).bicycleTags()

    private fun fullyApplied(tags: Map<String, String>, sides: Sides<CyclewayAndDirection>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        sides.applyTo(builder, false)
        return builder.toMap()
    }

    private fun Map<String, String>.bicycleTags() = filterKeys { it == "bicycle" || it.startsWith("bicycle:") }
}
