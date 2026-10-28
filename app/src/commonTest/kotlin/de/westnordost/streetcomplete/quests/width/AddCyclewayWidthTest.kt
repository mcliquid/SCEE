package de.westnordost.streetcomplete.quests.width

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.osm.length.Length
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddCyclewayWidthTest {
    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }
    private lateinit var quest: AddCyclewayWidth

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        quest = AddCyclewayWidth(arSupportChecker)
    }

    @Test fun `applicable to exclusive cycleway`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "cycleway",
        ))))
    }

    @Test fun `applicable to combined segregated cycleway`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
        ))))
    }

    @Test fun `applicable to shared path with segregated no`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "no",
        ))))
    }

    @Test fun `applicable to cycleway with foot designated and segregated no`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "cycleway",
            "foot" to "designated",
            "segregated" to "no",
        ))))
    }

    @Test fun `applicable to cycleway with foot yes and segregated no`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "cycleway",
            "foot" to "yes",
            "segregated" to "no",
        ))))
    }

    @Test fun `applicable to footway with bicycle designated and segregated no`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "designated",
            "segregated" to "no",
        ))))
    }

    @Test fun `not applicable to shared path when segregated is missing`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
        ))))
    }

    @Test fun `not applicable to shared path with segregated no when width already set`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "no",
            "width" to "2",
        ))))
    }

    @Test fun `bicycle permission overrides private access`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "no",
            "access" to "private",
        ))))
    }

    @Test fun `not applicable when bicycles are explicitly prohibited`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "cycleway",
            "access" to "yes",
            "bicycle" to "no",
        ))))
    }

    @Test fun `not applicable to footway with bicycle yes and segregated no`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
            "segregated" to "no",
        ))))
    }

    @Test fun `not applicable to footway with bicycle yes when segregated is missing`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
        ))))
    }

    @Test fun `not applicable to path with bicycle yes and foot yes and segregated no`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "yes",
            "foot" to "yes",
            "segregated" to "no",
        ))))
    }

    @Test fun `not applicable to footway link with bicycle designated and segregated no`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "footway" to "link",
            "bicycle" to "designated",
            "segregated" to "no",
        ))))
    }

    @Test fun `apply width to exclusive cycleway`() {
        assertEquals(
            setOf(StringMapEntryAdd("width", "2")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.0), false),
                mapOf("highway" to "cycleway")
            )
        )
    }

    @Test fun `apply cycleway width to combined segregated way`() {
        assertEquals(
            setOf(StringMapEntryAdd("cycleway:width", "1.5")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(1.5), false),
                mapOf(
                    "highway" to "path",
                    "bicycle" to "designated",
                    "foot" to "designated",
                    "segregated" to "yes",
                )
            )
        )
    }

    @Test fun `apply total width to shared non-segregated way`() {
        assertEquals(
            setOf(StringMapEntryAdd("width", "2.5")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.5), false),
                mapOf(
                    "highway" to "path",
                    "bicycle" to "designated",
                    "foot" to "designated",
                    "segregated" to "no",
                )
            )
        )
    }

    @Test fun `apply source width ARCore to shared non-segregated way`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("width", "2.5"),
                StringMapEntryAdd("source:width", "ARCore"),
            ),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.5), true),
                mapOf(
                    "highway" to "path",
                    "bicycle" to "designated",
                    "foot" to "designated",
                    "segregated" to "no",
                )
            )
        )
    }

    @Test fun `apply source cycleway width ARCore to segregated way`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("cycleway:width", "1.5"),
                StringMapEntryAdd("source:cycleway:width", "ARCore"),
            ),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(1.5), true),
                mapOf(
                    "highway" to "path",
                    "bicycle" to "designated",
                    "foot" to "designated",
                    "segregated" to "yes",
                )
            )
        )
    }

    // R01: filter must check cycleway:width when writer uses cycleway:width (audit E1 case 1)
    @Test fun `segregated cycleway with foot no asks for cycleway width not total width`() {
        val beforeTags = mapOf(
            "highway" to "cycleway",
            "foot" to "no",
            "segregated" to "yes",
        )
        assertTrue(quest.isApplicableTo(way(tags = beforeTags)))
        val afterTags = beforeTags + mapOf("cycleway:width" to "2")
        assertFalse(quest.isApplicableTo(way(tags = afterTags)))
        assertEquals(
            setOf(StringMapEntryAdd("cycleway:width", "2")),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(2.0), false), beforeTags)
        )
    }

    @Test fun `segregated cycleway with foot no and total width only stays eligible for cycleway width`() {
        val tags = mapOf(
            "highway" to "cycleway",
            "foot" to "no",
            "segregated" to "yes",
            "width" to "1.5",
        )
        assertTrue(quest.isApplicableTo(way(tags = tags)))
    }

    @Test fun `segregated cycleway with foot no does not reappear after answering cycleway width`() {
        val beforeTags = mapOf(
            "highway" to "cycleway",
            "foot" to "no",
            "segregated" to "yes",
        )
        val answered = beforeTags + mapOf("cycleway:width" to "2")
        assertFalse(quest.isApplicableTo(way(tags = answered)))
    }

    @Test fun `estimated cycleway width on segregated cycleway remains eligible`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "cycleway",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:width" to "1.5",
            "source:cycleway:width" to "estimate",
        ))))
    }

    @Test fun `precise cycleway width on segregated way is not overwritten`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "cycleway:width" to "1.8",
        ))))
    }

    @Test fun `cycleway and total width can coexist on segregated path`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "path",
            "bicycle" to "designated",
            "foot" to "designated",
            "segregated" to "yes",
            "width" to "3",
            "cycleway:width" to "1.5",
        ))))
    }
}
