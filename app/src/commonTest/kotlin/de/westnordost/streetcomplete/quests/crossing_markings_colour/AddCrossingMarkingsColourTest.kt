package de.westnordost.streetcomplete.quests.crossing_markings_colour

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddCrossingMarkingsColourTest {

    private lateinit var questType: AddCrossingMarkingsColour

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddCrossingMarkingsColour()
    }

    @Test fun `applicable to bicycle crossing node with markings`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "yes",
        ))
        assertTrue(questType.isApplicableTo(crossing))
    }

    @Test fun `applicable to bicycle designated crossing node`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "designated",
            "crossing:markings" to "lines",
        ))
        assertTrue(questType.isApplicableTo(crossing))
    }

    @Test fun `applicable to bicycle official crossing node`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "official",
            "crossing:markings" to "zebra",
        ))
        assertTrue(questType.isApplicableTo(crossing))
    }

    @Test fun `applicable to cycleway crossing way with markings`() {
        val crossing = way(tags = mapOf(
            "highway" to "cycleway",
            "cycleway" to "crossing",
            "crossing:markings" to "dots",
        ))
        assertTrue(questType.isApplicableTo(crossing))
    }

    @Test fun `not applicable without markings tag`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
        ))
        assertFalse(questType.isApplicableTo(crossing))
    }

    @Test fun `not applicable when markings are no`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "no",
        ))
        assertFalse(questType.isApplicableTo(crossing))
    }

    @Test fun `not applicable when colour is already red`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "yes",
            "crossing:markings:colour" to "red",
        ))
        assertFalse(questType.isApplicableTo(crossing))
    }

    @Test fun `not applicable when colour is already red and white`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "yes",
            "crossing:markings:colour" to "red;white",
        ))
        assertFalse(questType.isApplicableTo(crossing))
    }

    @Test fun `not applicable to pedestrian crossing without bicycle access`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "foot" to "yes",
            "crossing:markings" to "zebra",
        ))
        assertFalse(questType.isApplicableTo(crossing))
    }

    @Test fun `getApplicableElements finds bicycle crossing nodes in map data`() {
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "yes",
        ))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `multi select writes semicolon separated sorted values`() {
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings:colour", "green;red;white")),
            questType.answerApplied(
                CrossingMarkingsColourAnswer(
                    setOf(
                        CrossingMarkingsColour.GREEN,
                        CrossingMarkingsColour.WHITE,
                        CrossingMarkingsColour.RED,
                    )
                )
            )
        )
    }

    @Test fun `duplicate colours in answer produce no duplicate tag values`() {
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings:colour", "red;white")),
            questType.answerApplied(
                CrossingMarkingsColourAnswer(
                    setOf(CrossingMarkingsColour.RED, CrossingMarkingsColour.WHITE, CrossingMarkingsColour.RED)
                )
            )
        )
    }

    @Test fun `other colours are merged without duplicating standard colours`() {
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings:colour", "orange;red")),
            questType.answerApplied(
                CrossingMarkingsColourAnswer(
                    standard = setOf(CrossingMarkingsColour.RED),
                    other = setOf("orange", "red"),
                )
            )
        )
    }

    @Test fun `apply answer leaves unrelated tags unchanged`() {
        val original = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "lines",
            "surface" to "asphalt",
            "surface:colour" to "grey",
        )
        val changes = questType.answerAppliedTo(
            CrossingMarkingsColourAnswer(setOf(CrossingMarkingsColour.RED, CrossingMarkingsColour.WHITE)),
            original,
        )
        val saved = original.toMutableMap()
        changes.forEach { it.applyTo(saved) }
        assertEquals("red;white", saved["crossing:markings:colour"])
        assertEquals("asphalt", saved["surface"])
        assertEquals("grey", saved["surface:colour"])
        assertEquals("lines", saved["crossing:markings"])
    }

    @Test fun `undo restores previous tags`() {
        val original = mapOf(
            "highway" to "crossing",
            "bicycle" to "yes",
            "crossing:markings" to "lines",
            "surface" to "asphalt",
        )
        val builder = StringMapChangesBuilder(original)
        questType.applyAnswerTo(
            CrossingMarkingsColourAnswer(setOf(CrossingMarkingsColour.RED)),
            builder,
            ElementPointGeometry(LatLon(0.0, 0.0)),
            0,
        )
        val changes = builder.create()
        val saved = original.toMutableMap()
        changes.applyTo(saved)
        val undone = saved.toMutableMap()
        changes.reversed().applyTo(undone)
        assertEquals(original, undone)
    }

    @Test fun `parse existing tag value for resurvey`() {
        val parsed = parseCrossingMarkingsColourAnswer(
            mapOf("crossing:markings:colour" to "White;red;gold")
        )
        assertEquals(
            CrossingMarkingsColourAnswer(
                setOf(CrossingMarkingsColour.WHITE, CrossingMarkingsColour.RED),
                setOf("gold"),
            ),
            parsed,
        )
    }

    @Test fun `previous answer key matches wiki link`() {
        assertEquals(listOf("crossing:markings:colour"), questType.previousAnswerKeys)
    }

    @Test fun `crossing markings quest filter is unchanged for default node`() {
        val markingsQuest = de.westnordost.streetcomplete.quests.crossing_markings.AddCrossingMarkings()
        val crossing = node(tags = mapOf("highway" to "crossing"))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(1, markingsQuest.getApplicableElements(mapData).toList().size)
    }
}
