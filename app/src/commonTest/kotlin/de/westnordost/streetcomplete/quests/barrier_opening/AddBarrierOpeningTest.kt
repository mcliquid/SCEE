package de.westnordost.streetcomplete.quests.barrier_opening

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.length.Length
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.width.WidthAnswer
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AddBarrierOpeningTest {

    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }
    private val quest = AddBarrierOpening(arSupportChecker)

    private fun bollardOnFootway(extraTags: Map<String, String> = emptyMap()): Pair<de.westnordost.streetcomplete.data.osm.mapdata.Node, TestMapDataWithGeometry> {
        val barrierNode = node(1, tags = mapOf("barrier" to "bollard") + extraTags)
        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            way(11, listOf(1, 2), mapOf("highway" to "footway")),
        ))
        return barrierNode to mapData
    }

    @Test fun `eligible when maxwidth physical is missing`() {
        val (barrierNode, mapData) = bollardOnFootway()
        assertNull(quest.isApplicableTo(barrierNode))
        assertEquals(listOf(barrierNode), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `eligible when canonical physical width source is estimate`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf(
            "maxwidth:physical" to "1",
            "source:maxwidth:physical" to "estimate",
        ))
        assertNull(quest.isApplicableTo(barrierNode))
        assertEquals(listOf(barrierNode), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `not eligible when canonical physical width source is measured`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf(
            "maxwidth:physical" to "1",
            "source:maxwidth:physical" to "ARCore",
        ))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `not eligible when maxwidth physical is set without estimated source`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf("maxwidth:physical" to "1"))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `eligible when only legacy physical width source is estimate`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf(
            "maxwidth:physical" to "1",
            "source:maxwidth_physical" to "estimate",
        ))
        assertNull(quest.isApplicableTo(barrierNode))
        assertEquals(listOf(barrierNode), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `not eligible when legacy source is estimate but canonical source is measured`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf(
            "maxwidth:physical" to "1",
            "source:maxwidth:physical" to "ARCore",
            "source:maxwidth_physical" to "estimate",
        ))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `apply manual width with canonical source key`() {
        assertEquals(
            setOf(StringMapEntryAdd("maxwidth:physical", "1.2")),
            quest.answerApplied(BarrierWidth(WidthAnswer(Length.Meters(1.2), false))),
        )
    }

    @Test fun `apply AR width with canonical source key`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("maxwidth:physical", "1.2"),
                StringMapEntryAdd("source:maxwidth:physical", "ARCore"),
            ),
            quest.answerApplied(BarrierWidth(WidthAnswer(Length.Meters(1.2), true))),
        )
    }

    @Test fun `apply manual width removes previous canonical estimate source`() {
        assertEquals(
            setOf(
                StringMapEntryModify("maxwidth:physical", "1", "1.2"),
                StringMapEntryDelete("source:maxwidth:physical", "estimate"),
            ),
            quest.answerAppliedTo(
                BarrierWidth(WidthAnswer(Length.Meters(1.2), false)),
                mapOf(
                    "maxwidth:physical" to "1",
                    "source:maxwidth:physical" to "estimate",
                ),
            ),
        )
    }

    @Test fun `width guard unchanged when width is measured without estimate source`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf("width" to "2"))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `maxwidth guard unchanged when maxwidth is measured without estimate source`() {
        val (barrierNode, mapData) = bollardOnFootway(mapOf("maxwidth" to "2"))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `barrier type guard unchanged for unsupported barrier`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "fence"))
        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            way(11, listOf(1, 2), mapOf("highway" to "footway")),
        ))
        assertFalse(quest.isApplicableTo(barrierNode) == true)
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `parent path guard unchanged when barrier is not on eligible way`() {
        val barrierNode = node(1, tags = mapOf("barrier" to "bollard"))
        val mapData = TestMapDataWithGeometry(listOf(
            barrierNode,
            node(2),
            way(11, listOf(1, 2), mapOf("highway" to "motorway")),
        ))
        assertNull(quest.isApplicableTo(barrierNode))
        assertEquals(emptyList(), quest.getApplicableElements(mapData).toList())
    }

    @Test fun `not eligible after manual answer with measured width`() {
        val tagsAfterAnswer = mapOf(
            "barrier" to "bollard",
            "maxwidth:physical" to "1.2",
        )
        val barrierNode = node(1, tags = tagsAfterAnswer)
        assertFalse(quest.isApplicableTo(barrierNode) == true)
    }

    @Test fun `not eligible after AR answer`() {
        val tagsAfterAnswer = mapOf(
            "barrier" to "bollard",
            "maxwidth:physical" to "1.2",
            "source:maxwidth:physical" to "ARCore",
        )
        val barrierNode = node(1, tags = tagsAfterAnswer)
        assertFalse(quest.isApplicableTo(barrierNode) == true)
    }
}
