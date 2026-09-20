package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AddCyclewayPartSmoothnessTest {
    private lateinit var questType: AddCyclewayPartSmoothness

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddCyclewayPartSmoothness()
    }

    @Test fun `applicable to canonical segregated path with cycleway surface`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `applicable to another supported cycleway surface`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "paving_stones",
        )
    }

    @Test fun `applicable to explicit combined cycleway and footway`() {
        assertIsApplicable(
            "highway" to "cycleway",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
        assertIsApplicable(
            "highway" to "footway",
            "bicycle" to "yes",
            "segregated" to "yes",
            "cycleway:surface" to "compacted",
        )
    }

    @Test fun `not applicable without segregated`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when segregated is no`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "no",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when bicycle is no`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "no",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when foot is no`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "no",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when foot is private on path`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "private",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when foot is private on cycleway`() {
        assertIsNotApplicable(
            "highway" to "cycleway",
            "foot" to "private",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable when access is private and foot is private even if bicycle is designated`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "private",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "access" to "private",
        )
    }

    @Test fun `not applicable when access is no and foot is private even if bicycle is yes`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "yes",
            "foot" to "private",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "access" to "no",
        )
    }

    @Test fun `applicable to explicit foot yes`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "yes",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable without cycleway surface`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
        )
    }

    @Test fun `not applicable to unsupported cycleway surface`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "grass",
        )
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "paved",
        )
    }

    @Test fun `not applicable when cycleway smoothness is already current`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "cycleway:smoothness" to "good",
        )
    }

    @Test fun `applicable when check date for cycleway smoothness is older than four years`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "cycleway:smoothness" to "good",
            "check_date:cycleway:smoothness" to "2001-01-01",
        )
    }

    @Test fun `not applicable when check date for cycleway smoothness is recent`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "cycleway:smoothness" to "good",
            "check_date:cycleway:smoothness" to nowAsCheckDateString(),
        )
    }

    @Test fun `applicable when legacy cycleway smoothness date is older than four years`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "cycleway:smoothness" to "good",
            "cycleway:smoothness:date" to "2014-10-10",
        )
    }

    @Test fun `not applicable when legacy cycleway smoothness date is recent`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "cycleway:smoothness" to "good",
            "cycleway:smoothness:date" to nowAsCheckDateString(),
        )
    }

    @Test fun `not applicable when bicycle is private`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "private",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable to private access without bicycle or foot override`() {
        assertIsNotApplicable(
            "highway" to "cycleway",
            "foot" to "private",
            "bicycle" to "private",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "access" to "private",
        )
    }

    @Test fun `not applicable to generic surface without cycleway surface`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "surface" to "asphalt",
        )
    }

    @Test fun `not applicable to ordinary non-segregated cycleway`() {
        assertIsNotApplicable(
            "highway" to "cycleway",
            "surface" to "asphalt",
        )
        assertIsNotApplicable(
            "highway" to "cycleway",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable to cycleway without explicit pedestrian use`() {
        assertIsNotApplicable(
            "highway" to "cycleway",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    @Test fun `not applicable to footway without explicit bicycle use`() {
        assertIsNotApplicable(
            "highway" to "footway",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
    }

    // D4.1 does not exclude area=yes; same as the existing part-surface filters.
    @Test fun `D4 1 policy currently applies on area=yes combined paths`() {
        assertIsApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "area" to "yes",
        )
    }

    @Test fun `not applicable when sidewalk is tagged`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "sidewalk" to "yes",
        )
    }

    @Test fun `not applicable when indoor`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "indoor" to "yes",
        )
    }

    @Test fun `not applicable when conveying`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "conveying" to "yes",
        )
    }

    @Test fun `not applicable to path link`() {
        assertIsNotApplicable(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
            "path" to "link",
        )
    }

    @Test fun `answer writes cycleway smoothness only`() {
        assertEquals(
            setOf(StringMapEntryAdd("cycleway:smoothness", "excellent")),
            questType.answerApplied(SmoothnessValueAnswer(Smoothness.EXCELLENT))
        )
    }

    @Test fun `answer does not write generic or footway smoothness and preserves existing values`() {
        assertEquals(
            setOf(StringMapEntryAdd("cycleway:smoothness", "good")),
            questType.answerAppliedTo(
                SmoothnessValueAnswer(Smoothness.GOOD),
                mapOf(
                    "smoothness" to "intermediate",
                    "footway:smoothness" to "excellent",
                    "footway:surface" to "paving_stones",
                    "cycleway:surface" to "asphalt",
                    "surface" to "paved",
                )
            )
        )
    }

    @Test fun `answer updates namespaced dates and cycleway surface check date`() {
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "excellent", "excellent"),
                StringMapEntryDelete("cycleway:smoothness:date", "2000-10-10"),
                StringMapEntryAdd("check_date:cycleway:smoothness", nowAsCheckDateString()),
                StringMapEntryModify("check_date:cycleway:surface", "2000-10-10", nowAsCheckDateString()),
            ),
            questType.answerAppliedTo(
                SmoothnessValueAnswer(Smoothness.EXCELLENT),
                mapOf(
                    "cycleway:smoothness" to "excellent",
                    "cycleway:smoothness:date" to "2000-10-10",
                    "cycleway:surface" to "asphalt",
                    "check_date:cycleway:surface" to "2000-10-10",
                    "smoothness" to "bad",
                    "smoothness:date" to "2000-10-10",
                    "check_date:smoothness" to "2000-10-10",
                    "check_date:surface" to "2000-10-10",
                    "footway:smoothness" to "excellent",
                    "check_date:footway:smoothness" to "2000-10-10",
                    "source:smoothness" to "survey",
                )
            )
        )
    }

    @Test fun `wrong surface refers to cycleway part only`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("cycleway:surface", "asphalt"),
                StringMapEntryDelete("cycleway:smoothness", "good"),
                StringMapEntryDelete("cycleway:smoothness:date", "2000-10-10"),
                StringMapEntryDelete("cycleway:surface:grade", "2"),
                StringMapEntryDelete("check_date:cycleway:smoothness", "2000-10-10"),
                StringMapEntryDelete("check_date:cycleway:surface", "2000-10-10"),
                StringMapEntryDelete("source:cycleway:surface", "survey"),
                StringMapEntryDelete("source:cycleway:smoothness", "survey"),
            ),
            questType.answerAppliedTo(
                WrongSurfaceAnswer,
                mapOf(
                    "cycleway:surface" to "asphalt",
                    "cycleway:smoothness" to "good",
                    "cycleway:smoothness:date" to "2000-10-10",
                    "cycleway:surface:grade" to "2",
                    "check_date:cycleway:smoothness" to "2000-10-10",
                    "check_date:cycleway:surface" to "2000-10-10",
                    "source:cycleway:surface" to "survey",
                    "source:cycleway:smoothness" to "survey",
                    "surface" to "asphalt",
                    "smoothness" to "intermediate",
                    "check_date:surface" to "2000-10-10",
                    "check_date:smoothness" to "2000-10-10",
                    "source:surface" to "bing",
                    "source:smoothness" to "estimate",
                    "footway:surface" to "paving_stones",
                    "footway:smoothness" to "excellent",
                    "check_date:footway:surface" to "2000-10-10",
                    "check_date:footway:smoothness" to "2000-10-10",
                    "source:footway:surface" to "survey",
                    "source:footway:smoothness" to "survey",
                )
            )
        )
    }

    @Test fun `answering smoothness makes the quest no longer applicable`() {
        val tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:surface" to "asphalt",
        )
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = tags)))

        val result = tags.toMutableMap()
        questType.answerAppliedTo(SmoothnessValueAnswer(Smoothness.GOOD), tags).forEach { it.applyTo(result) }
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = result)))
    }

    @Test fun `is actually steps is not applied`() {
        assertFailsWith<IllegalStateException> {
            questType.answerApplied(IsActuallyStepsAnswer)
        }
    }

    @Test fun `available smoothness choices follow cycleway surface not generic or footway surface`() {
        val tags = mapOf(
            "cycleway:surface" to "asphalt",
            "surface" to "gravel",
            "footway:surface" to "sett",
        )
        val answers = smoothnessAnswersForSurfaceKey(tags, CYCLEWAY_PART_SURFACE_KEY)
        assertEquals(smoothnessAnswersForSurface("asphalt"), answers)
        assertNotEquals(smoothnessAnswersForSurface("gravel"), answers)
        assertNotEquals(smoothnessAnswersForSurface("sett"), answers)
        assertTrue(Smoothness.EXCELLENT in answers)
        assertFalse(Smoothness.EXCELLENT in smoothnessAnswersForSurface("gravel"))
        assertFalse(Smoothness.EXCELLENT in smoothnessAnswersForSurface("sett"))
    }

    private fun assertIsApplicable(vararg pairs: Pair<String, String>) {
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }

    private fun assertIsNotApplicable(vararg pairs: Pair<String, String>) {
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }
}
