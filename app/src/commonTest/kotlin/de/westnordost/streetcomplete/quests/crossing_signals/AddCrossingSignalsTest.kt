package de.westnordost.streetcomplete.quests.crossing_signals

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.isCrossingWithTrafficSignals
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.crossing_island.AddCrossingIsland
import de.westnordost.streetcomplete.testutils.node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AddCrossingSignalsTest {
    private val questType = AddCrossingSignals()

    @Test fun `yes without crossing adds both tags`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("crossing:signals", "yes"),
                StringMapEntryAdd("crossing", "traffic_signals")
            ),
            questType.answerApplied(true)
        )
    }

    @Test fun `no without crossing only adds crossing signals`() {
        assertEquals(
            setOf(StringMapEntryAdd("crossing:signals", "no")),
            questType.answerApplied(false)
        )
    }

    @Test fun `yes with crossing island migrates island`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("crossing:signals", "yes"),
                StringMapEntryAdd("crossing:island", "yes"),
                StringMapEntryModify("crossing", "island", "traffic_signals")
            ),
            questType.answerAppliedTo(true, mapOf("crossing" to "island"))
        )
    }

    @Test fun `yes with crossing island keeps existing crossing island value`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("crossing:signals", "yes"),
                StringMapEntryModify("crossing", "island", "traffic_signals")
            ),
            questType.answerAppliedTo(true, mapOf("crossing" to "island", "crossing:island" to "no"))
        )
    }

    @Test fun `no with crossing island changes nothing else`() {
        assertEquals(
            setOf(StringMapEntryAdd("crossing:signals", "no")),
            questType.answerAppliedTo(false, mapOf("crossing" to "island"))
        )
    }

    @Test fun `existing crossing values are never overwritten`() {
        for (value in listOf("marked", "uncontrolled", "unmarked", "zebra", "informal", "traffic_signals")) {
            for (answer in listOf(true, false)) {
                assertEquals(
                    setOf(StringMapEntryAdd("crossing:signals", if (answer) "yes" else "no")),
                    questType.answerAppliedTo(answer, mapOf("crossing" to value)),
                    "crossing=$value answer=$answer"
                )
            }
        }
    }

    @Test fun `result is recognized as crossing with traffic signals`() {
        val tags = mapOf("highway" to "crossing", "crossing:signals" to "yes", "crossing" to "traffic_signals")
        assertTrue(node(tags = tags).isCrossingWithTrafficSignals())
    }

    @Test fun `island quest not applicable after migrating island`() {
        val tags = mapOf(
            "highway" to "crossing",
            "crossing" to "traffic_signals",
            "crossing:signals" to "yes",
            "crossing:island" to "yes"
        )
        assertEquals(false, AddCrossingIsland().isApplicableTo(node(tags = tags)))
    }

    @Test fun `signals quest is not applicable once answered`() {
        val tags = mapOf("highway" to "crossing", "crossing" to "traffic_signals", "crossing:signals" to "yes")
        assertEquals(false, questType.isApplicableTo(node(tags = tags)))
    }
}
