package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartSmoothnessLastPickedTest {
    private val preferences = inMemoryPrefs()
    private val tags = mapOf(
        "highway" to "residential", "cycleway:left" to "track", "cycleway:right" to "track",
        "cycleway:left:surface" to "asphalt", "cycleway:right:surface" to "asphalt",
    )
    private val answer = PartSmoothnessAnswer(mapOf(
        "cycleway:left:smoothness" to Smoothness.GOOD,
        "cycleway:right:smoothness" to Smoothness.EXCELLENT,
    ))
    private fun save(value: PartSmoothnessAnswer = answer, context: Map<String, String> = tags) =
        savePartSmoothnessLastPicked(preferences, value, resolveSmoothnessParts(context, 0), context)
    private fun load(context: Map<String, String> = tags) =
        loadPartSmoothnessLastPicked(preferences, resolveSmoothnessParts(context, 0), context)

    @Test fun `empty history offers no shortcut`() { assertNull(load()) }

    @Test fun `restored asymmetric answer produces identical tags without swapping sides`() {
        save()
        assertEquals(answer, load())
        Prefs.preferences = preferences
        val quest = AddCyclewayPartSmoothness()
        assertEquals(quest.answerAppliedTo(answer, tags), quest.answerAppliedTo(load()!!, tags))
    }

    @Test fun `opposite side is incompatible`() {
        val left = tags - setOf("cycleway:right", "cycleway:right:surface")
        val right = tags - setOf("cycleway:left", "cycleway:left:surface")
        save(PartSmoothnessAnswer(mapOf("cycleway:left:smoothness" to Smoothness.GOOD)), left)
        assertNull(load(right))
    }

    @Test fun `surface or infrastructure changes are incompatible`() {
        save()
        assertNull(load(tags + ("cycleway:left:surface" to "gravel")))
        assertNull(load(tags + ("cycleway:left" to "lane")))
    }

    @Test fun `segregated path preserves cycle and foot values and rejects shared path`() {
        val path = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated",
            "segregated" to "yes", "cycleway:surface" to "asphalt", "footway:surface" to "asphalt")
        val value = PartSmoothnessAnswer(mapOf("cycleway:smoothness" to Smoothness.GOOD,
            "footway:smoothness" to Smoothness.EXCELLENT))
        save(value, path)
        assertEquals(value, load(path))
        assertNull(load(path + ("segregated" to "no")))
        assertNull(load())
    }

    @Test fun `both scope and explicit split decision survive storage`() {
        val both = tags + (BOTH_SMOOTHNESS_KEY to "intermediate")
        val split = answer.copy(splitBoth = true)
        save(split, both)
        assertEquals(split, load(both))
        assertNull(load())
        Prefs.preferences = preferences
        val quest = AddCyclewayPartSmoothness()
        assertEquals(quest.answerAppliedTo(split, both), quest.answerAppliedTo(load(both)!!, both))
    }

    @Test fun `invalid empty or incomplete both answer does not replace previous answer`() {
        save()
        save(PartSmoothnessAnswer(emptyMap()))
        save(PartSmoothnessAnswer(mapOf("cycleway:left:smoothness" to Smoothness.GOOD)),
            tags + (BOTH_SMOOTHNESS_KEY to "intermediate"))
        assertEquals(answer, load())
    }

    @Test fun `last valid answer replaces previous one`() {
        save()
        val latest = answer.copy(values = answer.values.mapValues { Smoothness.INTERMEDIATE })
        save(latest)
        assertEquals(latest, load())
    }
}
