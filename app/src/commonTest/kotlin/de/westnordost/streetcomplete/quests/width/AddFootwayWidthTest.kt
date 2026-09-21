package de.westnordost.streetcomplete.quests.width

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.osm.length.Length
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddFootwayWidthTest {
    private val arSupportChecker = object : ArSupportChecker {
        override fun invoke(): Boolean = false
    }
    private lateinit var quest: AddFootwayWidth

    @BeforeTest fun setUp() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = mockPrefs3()
        quest = AddFootwayWidth(arSupportChecker)
    }

    @Test fun `applicable to footway with bicycle yes`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
        ))))
    }

    @Test fun `apply width to footway with bicycle yes`() {
        assertEquals(
            setOf(StringMapEntryAdd("width", "2")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.0), false),
                mapOf(
                    "highway" to "footway",
                    "bicycle" to "yes",
                )
            )
        )
    }

    @Test fun `applicable to footway with bicycle yes and segregated no`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
            "segregated" to "no",
        ))))
    }

    @Test fun `apply width to footway with bicycle yes and segregated no`() {
        assertEquals(
            setOf(StringMapEntryAdd("width", "2")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.0), false),
                mapOf(
                    "highway" to "footway",
                    "bicycle" to "yes",
                    "segregated" to "no",
                )
            )
        )
    }

    @Test fun `apply source width ARCore to footway with bicycle yes`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("width", "2"),
                StringMapEntryAdd("source:width", "ARCore"),
            ),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(2.0), true),
                mapOf(
                    "highway" to "footway",
                    "bicycle" to "yes",
                )
            )
        )
    }

    @Test fun `applicable to ordinary footways`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
        ))))
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "no",
        ))))
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "dismount",
        ))))
    }

    @Test fun `apply width to ordinary footway`() {
        assertEquals(
            setOf(StringMapEntryAdd("width", "1.5")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(1.5), false),
                mapOf("highway" to "footway")
            )
        )
    }

    @Test fun `not applicable to footway with bicycle designated`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "designated",
        ))))
    }

    @Test fun `not applicable to footway with bicycle designated and segregated no`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "designated",
            "segregated" to "no",
        ))))
    }

    @Test fun `applicable to segregated footway with bicycle yes`() {
        assertTrue(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
            "segregated" to "yes",
        ))))
    }

    @Test fun `apply footway width to segregated footway with bicycle yes`() {
        assertEquals(
            setOf(StringMapEntryAdd("footway:width", "1.2")),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(1.2), false),
                mapOf(
                    "highway" to "footway",
                    "bicycle" to "yes",
                    "segregated" to "yes",
                )
            )
        )
    }

    @Test fun `not applicable to segregated bicycle yes footway when footway width is set`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "yes",
            "segregated" to "yes",
            "footway:width" to "1.2",
        ))))
    }

    @Test fun `apply source footway width ARCore to segregated footway`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("footway:width", "1.2"),
                StringMapEntryAdd("source:footway:width", "ARCore"),
            ),
            quest.answerAppliedTo(
                WidthAnswer(Length.Meters(1.2), true),
                mapOf(
                    "highway" to "footway",
                    "bicycle" to "yes",
                    "segregated" to "yes",
                )
            )
        )
    }

    @Test fun `segregated footway with bicycle no keeps ordinary width`() {
        val tags = mapOf(
            "highway" to "footway",
            "segregated" to "yes",
            "bicycle" to "no",
        )
        assertTrue(quest.isApplicableTo(way(tags = tags)))
        assertEquals(
            setOf(StringMapEntryAdd("width", "1.5")),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), false), tags)
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("width", "1.5"),
                StringMapEntryAdd("source:width", "ARCore"),
            ),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), true), tags)
        )
    }

    @Test fun `segregated footway with bicycle missing keeps ordinary width`() {
        val tags = mapOf(
            "highway" to "footway",
            "segregated" to "yes",
        )
        assertTrue(quest.isApplicableTo(way(tags = tags)))
        assertEquals(
            setOf(StringMapEntryAdd("width", "1.5")),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), false), tags)
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("width", "1.5"),
                StringMapEntryAdd("source:width", "ARCore"),
            ),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), true), tags)
        )
    }

    @Test fun `segregated footway with bicycle dismount keeps ordinary width`() {
        val tags = mapOf(
            "highway" to "footway",
            "segregated" to "yes",
            "bicycle" to "dismount",
        )
        assertTrue(quest.isApplicableTo(way(tags = tags)))
        assertEquals(
            setOf(StringMapEntryAdd("width", "1.5")),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), false), tags)
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("width", "1.5"),
                StringMapEntryAdd("source:width", "ARCore"),
            ),
            quest.answerAppliedTo(WidthAnswer(Length.Meters(1.5), true), tags)
        )
    }

    @Test fun `not applicable to footway crossing or link`() {
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "footway" to "crossing",
        ))))
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "footway" to "link",
        ))))
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "footway" to "crossing",
            "bicycle" to "yes",
        ))))
        assertFalse(quest.isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "footway" to "link",
            "bicycle" to "yes",
        ))))
    }
}
