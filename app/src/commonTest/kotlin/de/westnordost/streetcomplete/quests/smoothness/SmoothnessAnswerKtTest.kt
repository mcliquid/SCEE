package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import kotlin.test.Test
import kotlin.test.assertEquals

class SmoothnessAnswerKtTest {

    @Test fun `apply smoothness answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("smoothness", "excellent")),
            SmoothnessValueAnswer(Smoothness.EXCELLENT).appliedTo(mapOf())
        )
    }

    @Test fun `apply smoothness answer updates check date for surface`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("smoothness", "excellent"),
                StringMapEntryModify("check_date:surface", "2000-10-10", nowAsCheckDateString()),
            ),
            SmoothnessValueAnswer(Smoothness.EXCELLENT).appliedTo(mapOf(
                "surface" to "asphalt",
                "check_date:surface" to "2000-10-10",
            ))
        )
    }

    @Test fun `deletes possibly out of date info`() {
        assertEquals(
            setOf(
                StringMapEntryModify("smoothness", "excellent", "excellent"),
                StringMapEntryDelete("smoothness:date", "2000-10-10"),
                StringMapEntryDelete("surface:grade", "1"),
                StringMapEntryAdd("check_date:smoothness", nowAsCheckDateString())
            ),
            SmoothnessValueAnswer(Smoothness.EXCELLENT).appliedTo(mapOf(
                "smoothness" to "excellent",
                "smoothness:date" to "2000-10-10",
                "surface:grade" to "1"
            ))
        )
    }

    @Test fun `apply is actually steps answer`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("highway", "steps"),
                StringMapEntryDelete("smoothness", "excellent"),
                StringMapEntryDelete("smoothness:date", "2000-10-10"),
                StringMapEntryDelete("surface:grade", "3"),
                StringMapEntryDelete("check_date:smoothness", "2000-10-10")
            ),
            IsActuallyStepsAnswer.appliedTo(mapOf(
                "smoothness" to "excellent",
                "smoothness:date" to "2000-10-10",
                "surface" to "asphalt",
                "surface:grade" to "3",
                "check_date:smoothness" to "2000-10-10",
            ))
        )
    }

    @Test fun `apply wrong surface answer`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("smoothness", "excellent"),
                StringMapEntryDelete("smoothness:date", "2000-10-10"),
                StringMapEntryDelete("surface", "asphalt"),
                StringMapEntryDelete("surface:grade", "3"),
                StringMapEntryDelete("check_date:smoothness", "2000-10-10"),
                StringMapEntryDelete("paving_stones:length", "30"),
            ),
            WrongSurfaceAnswer.appliedTo(mapOf(
                "smoothness" to "excellent",
                "smoothness:date" to "2000-10-10",
                "surface" to "asphalt",
                "surface:grade" to "3",
                "check_date:smoothness" to "2000-10-10",
                "paving_stones:length" to "30"
            ))
        )
    }

    @Test fun `apply prefixed smoothness answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("cycleway:smoothness", "excellent")),
            SmoothnessValueAnswer(Smoothness.EXCELLENT).appliedTo(mapOf(), "cycleway")
        )
    }

    @Test fun `prefixed smoothness updates cycleway surface check date only`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("cycleway:smoothness", "excellent"),
                StringMapEntryModify("check_date:cycleway:surface", "2000-10-10", nowAsCheckDateString()),
            ),
            SmoothnessValueAnswer(Smoothness.EXCELLENT).appliedTo(mapOf(
                "cycleway:surface" to "asphalt",
                "check_date:cycleway:surface" to "2000-10-10",
                "surface" to "gravel",
                "check_date:surface" to "2000-10-10",
                "footway:surface" to "sett",
                "check_date:footway:surface" to "2000-10-10",
            ), "cycleway")
        )
    }

    @Test fun `prefixed smoothness preserves generic and footway smoothness`() {
        assertEquals(
            setOf(
                StringMapEntryModify("cycleway:smoothness", "good", "good"),
                StringMapEntryDelete("cycleway:smoothness:date", "2000-10-10"),
                StringMapEntryDelete("cycleway:surface:grade", "1"),
                StringMapEntryAdd("check_date:cycleway:smoothness", nowAsCheckDateString()),
            ),
            SmoothnessValueAnswer(Smoothness.GOOD).appliedTo(mapOf(
                "cycleway:smoothness" to "good",
                "cycleway:smoothness:date" to "2000-10-10",
                "cycleway:surface:grade" to "1",
                "smoothness" to "bad",
                "footway:smoothness" to "excellent",
            ), "cycleway")
        )
    }

    @Test fun `prefixed wrong surface refers to cycleway part only`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("cycleway:surface", "asphalt"),
                StringMapEntryDelete("cycleway:smoothness", "excellent"),
                StringMapEntryDelete("cycleway:smoothness:date", "2000-10-10"),
                StringMapEntryDelete("check_date:cycleway:smoothness", "2000-10-10"),
                StringMapEntryDelete("cycleway:paving_stones:length", "30"),
            ),
            WrongSurfaceAnswer.appliedTo(mapOf(
                "cycleway:smoothness" to "excellent",
                "cycleway:smoothness:date" to "2000-10-10",
                "cycleway:surface" to "asphalt",
                "check_date:cycleway:smoothness" to "2000-10-10",
                "cycleway:paving_stones:length" to "30",
                "surface" to "asphalt",
                "smoothness" to "good",
                "footway:surface" to "paving_stones",
                "footway:smoothness" to "excellent",
            ), "cycleway")
        )
    }
}

private fun SmoothnessAnswer.appliedTo(
    tags: Map<String, String>,
    prefix: String? = null,
): Set<StringMapEntryChange> {
    val cb = StringMapChangesBuilder(tags)
    applyTo(cb, prefix)
    return cb.create().changes
}
