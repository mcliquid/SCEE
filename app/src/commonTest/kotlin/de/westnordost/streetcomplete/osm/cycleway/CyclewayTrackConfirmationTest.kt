package de.westnordost.streetcomplete.osm.cycleway

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.osm.cycleway.Cycleway.TRACK
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.oneway.Direction
import de.westnordost.streetcomplete.osm.oneway.Direction.*
import de.westnordost.streetcomplete.quests.contract.answeredTags
import de.westnordost.streetcomplete.quests.cycleway.AddCycleway
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CyclewayTrackConfirmationTest {
    @Test fun `track direction is independent of sidewalk direction`() {
        for (lht in listOf(false, true)) for (side in listOf("left", "right")) {
            for (road in listOf("no", "yes", "-1")) {
                for (sidewalkDirection in listOf("yes", "-1", "no", "alternating")) {
                    for (trackDirection in listOf(null, "yes", "-1", "no")) {
                        val tags = mapOf(
                            "oneway" to road, "cycleway:$side" to "track",
                            "sidewalk:$side:bicycle" to "yes", "sidewalk:$side:bicycle:signed" to "yes",
                            "sidewalk:$side:oneway:bicycle" to sidewalkDirection,
                        ) + (trackDirection?.let { mapOf("cycleway:$side:oneway" to it) } ?: emptyMap())
                        val expected = when (trackDirection ?: road.takeUnless { it == "no" }) {
                            "yes" -> FORWARD
                            "-1" -> BACKWARD
                            "no" -> BOTH
                            else -> Direction.getDefault(side == "right", lht)
                        }
                        val parsed = assertNotNull(parseCyclewaySides(tags, lht))
                        assertEquals(CyclewayAndDirection(TRACK, expected),
                            if (side == "right") parsed.right else parsed.left, tags.toString())
                    }
                }
            }
        }
    }

    @Test fun `confirmation preserves complete side-specific tag sets`() {
        for (lht in listOf(false, true)) for (side in listOf("left", "right")) {
            val other = if (side == "left") "right" else "left"
            for (road in listOf("no", "yes", "-1")) for (access in listOf("designated", "yes", "private")) {
                for (sidewalkDirection in listOf("yes", "-1", "no", "alternating")) {
                    val direction = if (road == "yes") FORWARD else if (road == "-1") BACKWARD
                        else Direction.getDefault(side == "right", lht)
                    val before = mapOf(
                        "highway" to "secondary", "oneway" to road,
                        "cycleway:$side" to "track", "cycleway:$side:oneway" to direction.tagValue(),
                        "cycleway:$side:surface" to "asphalt", "cycleway:$side:smoothness" to "good",
                        "cycleway:$side:width" to "2", "cycleway:$other" to "separate",
                        "sidewalk:$side" to "yes", "sidewalk:$side:bicycle" to access,
                        "sidewalk:$side:bicycle:signed" to "yes",
                        "sidewalk:$side:oneway:bicycle" to sidewalkDirection,
                        "sidewalk:$side:oneway" to "-1",
                        "sidewalk:$side:bicycle:conditional" to "no @ (Mo-Fr)",
                        "sidewalk:$side:oneway:bicycle:conditional" to "no @ (Su)",
                        "sidewalk:$side:surface" to "paving_stones", "sidewalk:$side:width" to "1.5",
                        "sidewalk:$side:smoothness" to "intermediate",
                        "sidewalk:$other:bicycle" to "dismount", "sidewalk:$other:oneway:bicycle" to "no",
                        "note" to "keep", "check_date:cycleway" to "2001-01-01",
                    )
                    val answer = answer(side, direction)
                    val after = apply(answer, before, lht)
                    val expected = (before + ("check_date:cycleway" to nowAsCheckDateString())).toMutableMap()
                    if (sidewalkDirection == "no") {
                        expected.remove("sidewalk:left:oneway:bicycle")
                        expected.remove("sidewalk:right:oneway:bicycle")
                        expected["sidewalk:both:oneway:bicycle"] = "no"
                    }
                    assertEquals(expected, after)
                    assertEquals(after, apply(answer, after, lht), "Repeated confirmation")
                }
            }
        }
    }

    @Test fun `bare and both track tagging preserve independent sidewalk tags`() {
        for (lht in listOf(false, true)) for (road in listOf("no", "yes", "-1")) {
            for (key in listOf("cycleway", "cycleway:both")) {
                val side = if ((road == "-1") xor lht) "left" else "right"
                val before = mapOf(
                    "highway" to "secondary", "oneway" to road, key to "track",
                    "sidewalk:both:bicycle" to "designated", "sidewalk:both:oneway:bicycle" to "-1",
                    "sidewalk:both:bicycle:conditional" to "yes @ (Su)",
                    "check_date:cycleway" to "2001-01-01",
                )
                val parsed = assertNotNull(parseCyclewaySides(before, lht))
                val expectedDirection = when (road) {
                    "yes" -> FORWARD
                    "-1" -> BACKWARD
                    else -> Direction.getDefault(side == "right", lht)
                }
                assertEquals(expectedDirection, (if (side == "right") parsed.right else parsed.left)?.direction)
                val after = apply(parsed, before, lht)
                val expectedKey = if (key == "cycleway" && road != "no") "cycleway:$side" else "cycleway:both"
                assertEquals(before - key + (expectedKey to "track") +
                    ("check_date:cycleway" to nowAsCheckDateString()), after)
                assertEquals(after, apply(parsed, after, lht))
            }
        }
    }

    @Test fun `literal request example keeps unusual sidewalk keys`() {
        val before = mapOf(
            "highway" to "secondary", "cycleway" to "track", "cycleway:right" to "yes",
            "sidewalk" to "yes", "sidewalk:right" to "designated", "sidewalk:right:oneway" to "-1",
        )
        assertEquals(before - "cycleway" - "cycleway:right" + ("cycleway:both" to "track"),
            apply(answer("right", FORWARD), before))
    }

    @Test fun `side-specific facility overrides both before deciding whether track is confirmed`() {
        for (existing in listOf("track", "lane")) {
            val before = mapOf(
                "cycleway:both" to "lane", "cycleway:right" to existing,
                "sidewalk:right:bicycle" to "designated", "sidewalk:right:oneway:bicycle" to "-1",
            )
            val expected = mapOf("cycleway:left" to "lane", "cycleway:right" to "track") +
                if (existing == "track") before.filterKeys { it.startsWith("sidewalk:") } else emptyMap()
            assertEquals(expected, apply(answer("right", FORWARD), before))
        }
    }

    @Test fun `bare and both signed sidewalk access is retained`() {
        for (key in listOf("sidewalk", "sidewalk:both")) {
            val before = mapOf(
                "cycleway:both" to "track", "$key:bicycle" to "yes",
                "$key:bicycle:signed" to "yes", "$key:oneway:bicycle" to "no",
                "$key:bicycle:conditional" to "no @ (Mo-Fr)",
            )
            val answer = Sides(CyclewayAndDirection(TRACK, BACKWARD), CyclewayAndDirection(TRACK, FORWARD))
            val expected = before + ("check_date:cycleway" to nowAsCheckDateString())
            val after = apply(answer, before)
            assertEquals(expected, after)
            assertEquals(after, apply(answer, after))
        }
    }

    @Test fun `changing shared sidewalk to track still clears former designation`() {
        val before = mapOf("cycleway:right" to "track", "cycleway:right:segregated" to "no",
            "sidewalk:right:bicycle" to "designated", "sidewalk:right:oneway:bicycle" to "-1")
        assertEquals(mapOf("cycleway:right" to "track", "cycleway:right:segregated" to "yes"),
            apply(answer("right", FORWARD), before))
    }

    @Test fun `real cycleway resurvey preserves sidewalk and closes after confirmation`() {
        Prefs.preferences = inMemoryPrefs()
        val quest = AddCycleway { CountryInfo(null, listOf(
            IncompleteCountryInfo(isLeftHandTraffic = false, hasAdvisoryCycleLane = false))) }
        val before = mapOf(
            "highway" to "secondary", "cycleway:both" to "track",
            "sidewalk:right:bicycle" to "designated", "sidewalk:right:oneway:bicycle" to "-1",
            "check_date:cycleway" to "2001-01-01",
        )
        fun applicable(tags: Map<String, String>) = quest.getApplicableElements(
            TestMapDataWithGeometry(listOf(way(tags = tags)))).toList().isNotEmpty()
        assertTrue(applicable(before))
        val answer = assertNotNull(parseCyclewaySides(before, false))
        assertEquals(FORWARD, answer.right?.direction)
        val after = quest.answeredTags(answer, before)
        assertEquals(before + ("check_date:cycleway" to nowAsCheckDateString()), after)
        assertFalse(applicable(after))
        assertEquals(after, quest.answeredTags(answer, after))
    }

    private fun answer(side: String, direction: Direction) = Sides(
        if (side == "left") CyclewayAndDirection(TRACK, direction) else null,
        if (side == "right") CyclewayAndDirection(TRACK, direction) else null,
    )

    private fun apply(answer: Sides<CyclewayAndDirection>, before: Map<String, String>, lht: Boolean = false): Map<String, String> {
        val builder = StringMapChangesBuilder(before)
        answer.applyTo(builder, lht)
        return before.toMutableMap().also { builder.create().applyTo(it) }
    }

    private fun Direction.tagValue() = when (this) {
        FORWARD -> "yes"
        BACKWARD -> "-1"
        BOTH -> "no"
    }
}
