package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import kotlin.test.Test
import kotlin.test.assertEquals

class SmoothnessOverlayAnswerTest {

    private val today = nowAsCheckDateString()

    private fun SmoothnessOverlayAnswer.appliedTo(tags: Map<String, String>): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyTo(builder)
        return builder.toMap()
    }

    /* ----------------------------------------- single ----------------------------------------- */

    @Test fun `T1 add smoothness`() {
        assertEquals(
            mapOf("highway" to "residential", "surface" to "asphalt", "smoothness" to "good"),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("highway" to "residential", "surface" to "asphalt"))
        )
    }

    @Test fun `T1 adding smoothness refreshes existing surface check date`() {
        assertEquals(
            mapOf("surface" to "asphalt", "check_date:surface" to today, "smoothness" to "good"),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("surface" to "asphalt", "check_date:surface" to "2001-01-01"))
        )
    }

    @Test fun `T2 confirm unchanged smoothness sets check date`() {
        assertEquals(
            mapOf("surface" to "asphalt", "smoothness" to "good", "check_date:smoothness" to today),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("surface" to "asphalt", "smoothness" to "good"))
        )
    }

    @Test fun `T2 confirm also updates element check date like other StreetComplete answers`() {
        assertEquals(
            mapOf("smoothness" to "good", "check_date:smoothness" to today, "check_date" to today),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("smoothness" to "good", "check_date" to "2001-01-01"))
        )
    }

    @Test fun `T3 change smoothness updates existing check date`() {
        assertEquals(
            mapOf("smoothness" to "intermediate", "check_date:smoothness" to today),
            SingleSmoothnessAnswer(INTERMEDIATE).appliedTo(mapOf("smoothness" to "good", "check_date:smoothness" to "2019-01-01"))
        )
    }

    @Test fun `T4 legacy smoothness date and surface grade are removed`() {
        assertEquals(
            mapOf("smoothness" to "bad"),
            SingleSmoothnessAnswer(BAD).appliedTo(mapOf("smoothness" to "good", "smoothness:date" to "2018", "surface:grade" to "2"))
        )
    }

    @Test fun `T5 alias check date keys are replaced by check_date`() {
        assertEquals(
            mapOf("smoothness" to "good", "check_date:smoothness" to today),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("smoothness" to "good", "lastcheck:smoothness" to "2020-01-01"))
        )
    }

    @Test fun `T7 invalid value is replaced`() {
        assertEquals(
            mapOf("smoothness" to "good"),
            SingleSmoothnessAnswer(GOOD).appliedTo(mapOf("smoothness" to "very_good"))
        )
    }

    @Test fun `T8 smoothness without surface does not add a surface`() {
        assertEquals(
            mapOf("highway" to "track", "smoothness" to "bad"),
            SingleSmoothnessAnswer(BAD).appliedTo(mapOf("highway" to "track"))
        )
    }

    @Test fun `T9 set surface first then smoothness`() {
        assertEquals(
            mapOf("highway" to "track", "surface" to "gravel", "smoothness" to "bad"),
            SingleSmoothnessAnswer(BAD, Surface.GRAVEL).appliedTo(mapOf("highway" to "track"))
        )
    }

    @Test fun `T10 changing the surface replaces the old smoothness and its dates`() {
        assertEquals(
            mapOf("surface" to "sett", "smoothness" to "bad"),
            SingleSmoothnessAnswer(BAD, Surface.SETT).appliedTo(mapOf(
                "surface" to "asphalt",
                "smoothness" to "excellent",
                "smoothness:date" to "2020",
                "source:smoothness" to "survey",
                "check_date:smoothness" to "2020-01-01",
            ))
        )
    }

    @Test fun `T11 invalid surface is left untouched`() {
        assertEquals(
            mapOf("surface" to "cobblestone", "smoothness" to "bad"),
            SingleSmoothnessAnswer(BAD).appliedTo(mapOf("surface" to "cobblestone"))
        )
    }

    @Test fun `single answer does not touch part or sidewalk keys`() {
        val tags = mapOf(
            "highway" to "primary",
            "cycleway:right" to "track",
            "cycleway:right:smoothness" to "good",
            "sidewalk:left:smoothness" to "bad",
        )
        assertEquals(tags + ("smoothness" to "good"), SingleSmoothnessAnswer(GOOD).appliedTo(tags))
    }

    /* ------------------------------------------ parts ----------------------------------------- */

    @Test fun `T12 footway part only, no segregated tag written`() {
        val tags = mapOf("highway" to "path", "segregated" to "yes", "surface" to "asphalt")
        assertEquals(
            tags + mapOf("footway:smoothness" to "good", "check_date:footway:smoothness" to today),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    @Test fun `T13 both parts`() {
        val tags = mapOf("highway" to "path", "footway:surface" to "paving_stones", "cycleway:surface" to "asphalt")
        assertEquals(
            tags + mapOf(
                "footway:smoothness" to "intermediate",
                "cycleway:smoothness" to "excellent",
                "check_date:footway:smoothness" to today,
                "check_date:cycleway:smoothness" to today,
            ),
            PartsSmoothnessAnswer(footway = INTERMEDIATE, cycleway = EXCELLENT).appliedTo(tags)
        )
    }

    @Test fun `T14 no segregated tag is added for paths with separate surfaces`() {
        val result = PartsSmoothnessAnswer(cycleway = GOOD).appliedTo(mapOf("highway" to "path", "cycleway:surface" to "asphalt"))
        assertEquals(null, result["segregated"])
    }

    @Test fun `T15 untouched part and its check date stay unchanged`() {
        val tags = mapOf(
            "footway:smoothness" to "good",
            "check_date:footway:smoothness" to "2019-01-01",
            "cycleway:smoothness" to "good",
            "check_date:cycleway:smoothness" to "2019-01-01",
        )
        assertEquals(
            tags + mapOf("cycleway:smoothness" to "bad", "check_date:cycleway:smoothness" to today),
            PartsSmoothnessAnswer(cycleway = BAD).appliedTo(tags)
        )
    }

    @Test fun `part answer never updates element check date`() {
        val tags = mapOf("check_date" to "2019-01-01", "footway:smoothness" to "good")
        assertEquals(
            tags + ("check_date:footway:smoothness" to today),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    @Test fun `part answer keeps part metadata like the part smoothness quest`() {
        val tags = mapOf(
            "footway:smoothness" to "good",
            "footway:smoothness:date" to "2015",
            "footway:smoothness:check_date" to "2016-01-01",
            "footway:surface:grade" to "1",
            "cycleway:smoothness:date" to "2015",
        )
        // only the other spelling of the very same check date is replaced
        assertEquals(
            mapOf(
                "footway:smoothness" to "good",
                "footway:smoothness:date" to "2015",
                "footway:surface:grade" to "1",
                "check_date:footway:smoothness" to today,
                "cycleway:smoothness:date" to "2015",
            ),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    /* ----------------------------------- explicit segregation --------------------------------- */

    private val notSegregated = mapOf(
        "highway" to "path",
        "segregated" to "no",
        "surface" to "asphalt",
        "smoothness" to "good",
        "footway:smoothness" to "bad",
        "cycleway:smoothness" to "good",
    )

    @Test fun `segregated=no - confirming the common smoothness keeps the part tags`() {
        val changes = changesOf(SingleSmoothnessAnswer(GOOD), notSegregated)
        assertEquals(
            setOf(
                StringMapEntryModify("smoothness", "good", "good"),
                StringMapEntryAdd("check_date:smoothness", today),
            ),
            changes.changes
        )
    }

    @Test fun `segregated=no - changing the common smoothness keeps the part tags`() {
        val changes = changesOf(SingleSmoothnessAnswer(INTERMEDIATE), notSegregated)
        assertEquals(setOf(StringMapEntryModify("smoothness", "good", "intermediate")), changes.changes)
    }

    @Test fun `segregated=no - common smoothness is never removed by part values`() {
        val changes = changesOf(PartsSmoothnessAnswer(cycleway = GOOD), notSegregated)
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "good", "good"),
                StringMapEntryAdd("check_date:cycleway:smoothness", today),
            ),
            changes.changes
        )
    }

    @Test fun `segregated=yes - contradicting common smoothness is removed`() {
        val tags = notSegregated + ("segregated" to "yes")
        val changes = changesOf(PartsSmoothnessAnswer(cycleway = GOOD), tags)
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "good", "good"),
                StringMapEntryAdd("check_date:cycleway:smoothness", today),
                StringMapEntryDelete("smoothness", "good"),
            ),
            changes.changes
        )
    }

    @Test fun `no segregated tag - contradicting common smoothness is removed`() {
        val tags = notSegregated - "segregated"
        val changes = changesOf(PartsSmoothnessAnswer(cycleway = GOOD), tags)
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "good", "good"),
                StringMapEntryAdd("check_date:cycleway:smoothness", today),
                StringMapEntryDelete("smoothness", "good"),
            ),
            changes.changes
        )
    }

    @Test fun `inconsistent prefixed tags - invalid part value prevents reconciliation`() {
        val tags = notSegregated + ("segregated" to "yes") + ("footway:smoothness" to "very_good")
        val changes = changesOf(PartsSmoothnessAnswer(cycleway = GOOD), tags)
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "good", "good"),
                StringMapEntryAdd("check_date:cycleway:smoothness", today),
            ),
            changes.changes
        )
    }

    /* --------------------------------------- save and undo ------------------------------------ */

    @Test fun `save and undo of common smoothness reconciliation`() {
        val original = mapOf(
            "highway" to "path",
            "segregated" to "yes",
            "smoothness" to "intermediate",
            "smoothness:date" to "2015",
            "check_date:smoothness" to "2016-01-01",
            "source:smoothness" to "survey",
            "cycleway:smoothness" to "good",
            "check_date:cycleway:smoothness" to "2020-01-01",
            "footway:smoothness:check_date" to "2019-01-01",
        )
        val changes = changesOf(PartsSmoothnessAnswer(footway = BAD), original)

        val saved = original.toMutableMap()
        changes.applyTo(saved)
        assertEquals(
            mapOf(
                "highway" to "path",
                "segregated" to "yes",
                "cycleway:smoothness" to "good",
                "check_date:cycleway:smoothness" to "2020-01-01",
                "footway:smoothness" to "bad",
                "check_date:footway:smoothness" to today,
            ),
            saved
        )

        val undone = saved.toMutableMap()
        changes.reversed().applyTo(undone)
        assertEquals(original, undone)
    }

    private fun changesOf(answer: SmoothnessOverlayAnswer, tags: Map<String, String>): StringMapChanges {
        val builder = StringMapChangesBuilder(tags)
        answer.applyTo(builder)
        return builder.create()
    }

    @Test fun `T18 common smoothness kept while only one part is known`() {
        val tags = mapOf("highway" to "path", "segregated" to "yes", "smoothness" to "good")
        assertEquals(
            tags + mapOf("footway:smoothness" to "good", "check_date:footway:smoothness" to today),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    @Test fun `T19 stale common smoothness removed once both parts are known`() {
        val tags = mapOf(
            "smoothness" to "intermediate",
            "smoothness:date" to "2015",
            "check_date:smoothness" to "2016-01-01",
            "source:smoothness" to "survey",
            "cycleway:smoothness" to "good",
        )
        assertEquals(
            mapOf(
                "cycleway:smoothness" to "good",
                "footway:smoothness" to "bad",
                "check_date:footway:smoothness" to today,
            ),
            PartsSmoothnessAnswer(footway = BAD).appliedTo(tags)
        )
    }

    @Test fun `T19 invalid common smoothness removed once both parts are known`() {
        val tags = mapOf("smoothness" to "very_good", "cycleway:smoothness" to "good")
        assertEquals(
            mapOf("cycleway:smoothness" to "good", "footway:smoothness" to "good", "check_date:footway:smoothness" to today),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    @Test fun `T20 common smoothness equal to both parts is kept`() {
        val tags = mapOf("smoothness" to "good", "cycleway:smoothness" to "good")
        assertEquals(
            tags + mapOf("footway:smoothness" to "good", "check_date:footway:smoothness" to today),
            PartsSmoothnessAnswer(footway = GOOD).appliedTo(tags)
        )
    }

    @Test fun `T21 no common smoothness is created for equal parts`() {
        val result = PartsSmoothnessAnswer(footway = GOOD, cycleway = GOOD).appliedTo(mapOf("highway" to "path"))
        assertEquals(null, result["smoothness"])
    }

    @Test fun `T24 part answer leaves sidewalk keys alone`() {
        val tags = mapOf("highway" to "cycleway", "segregated" to "yes", "sidewalk:left:surface" to "paving_stones", "sidewalk:left:smoothness" to "bad")
        assertEquals(
            tags + mapOf("cycleway:smoothness" to "good", "check_date:cycleway:smoothness" to today),
            PartsSmoothnessAnswer(cycleway = GOOD).appliedTo(tags)
        )
    }
}
