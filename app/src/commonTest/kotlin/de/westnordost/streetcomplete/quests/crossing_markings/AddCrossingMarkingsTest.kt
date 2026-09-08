package de.westnordost.streetcomplete.quests.crossing_markings

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.node
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddCrossingMarkingsTest {

    @Test fun `default filter is applicable to crossings without crossing tag`() {
        val questType = questType(extended = false)
        val crossing = node(tags = mapOf("highway" to "crossing"))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertNull(questType.isApplicableTo(crossing))
    }

    @Test fun `default filter is not applicable to marked crossings`() {
        val questType = questType(extended = false)
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "crossing" to "marked"
        ))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(crossing))
    }

    @Test fun `default filter is not applicable to unmarked crossings`() {
        val questType = questType(extended = false)
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "crossing" to "unmarked"
        ))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(crossing))
    }

    @Test fun `extended filter is not applicable to unmarked crossings`() {
        val questType = questType(extended = true)
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "crossing" to "unmarked"
        ))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(0, questType.getApplicableElements(mapData).toList().size)
        assertEquals(false, questType.isApplicableTo(crossing))
    }

    @Test fun `extended filter is applicable to crossings without crossing tag`() {
        val questType = questType(extended = true)
        val crossing = node(tags = mapOf("highway" to "crossing"))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertNull(questType.isApplicableTo(crossing))
    }

    @Test fun `extended filter is applicable to marked crossings without marking type`() {
        val questType = questType(extended = true)
        val crossing = node(tags = mapOf(
            "highway" to "crossing",
            "crossing" to "marked"
        ))
        val mapData = TestMapDataWithGeometry(listOf(crossing))
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
        assertNull(questType.isApplicableTo(crossing))
    }

    @Test fun `enabling extended setting applies to already constructed quest type`() {
        var extended = false
        val questType = questType { extended }
        val marked = node(tags = mapOf(
            "highway" to "crossing",
            "crossing" to "marked"
        ))

        assertEquals(false, questType.isApplicableTo(marked))
        extended = true
        assertNull(questType.isApplicableTo(marked))
    }

    @Test fun `apply no markings answer`() {
        val questType = questType()
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings", "no")),
            questType.answerApplied(setOf(CrossingMarkings.NO))
        )
    }

    @Test fun `apply no markings replaces conflicting crossing tags with unmarked`() {
        val questType = questType()
        for (value in listOf("marked", "zebra", "uncontrolled")) {
            assertEquals(
                setOf(
                    StringMapEntryAdd("crossing:markings", "no"),
                    StringMapEntryModify("crossing", value, "unmarked")
                ),
                questType.answerAppliedTo(
                    setOf(CrossingMarkings.NO),
                    mapOf("highway" to "crossing", "crossing" to value)
                )
            )
        }
    }

    @Test fun `apply no markings keeps traffic_signals crossing tag`() {
        val questType = questType()
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings", "no")),
            questType.answerAppliedTo(
                setOf(CrossingMarkings.NO),
                mapOf("highway" to "crossing", "crossing" to "traffic_signals")
            )
        )
    }

    @Test fun `apply zebra markings keeps marked crossing tag`() {
        val questType = questType()
        assertEquals(
            setOf(StringMapEntryAdd("crossing:markings", "zebra")),
            questType.answerAppliedTo(
                setOf(CrossingMarkings.ZEBRA),
                mapOf("highway" to "crossing", "crossing" to "marked")
            )
        )
    }

    @Test fun `apply non-zebra markings replaces crossing=zebra with marked`() {
        val questType = questType()
        assertEquals(
            setOf(
                StringMapEntryAdd("crossing:markings", "lines"),
                StringMapEntryModify("crossing", "zebra", "marked")
            ),
            questType.answerAppliedTo(
                setOf(CrossingMarkings.LINES),
                mapOf("highway" to "crossing", "crossing" to "zebra")
            )
        )
    }
}

private fun questType(extended: Boolean = false): AddCrossingMarkings =
    questType { extended }

private fun questType(extended: () -> Boolean): AddCrossingMarkings {
    val settings: ObservableSettings = mock()
    every { settings.getBoolean(any(), true) } returns true
    every { settings.getBoolean(any(), false) } returns false
    every { settings.getBoolean("qs_AddCrossingMarkings_extended", false) } calls { extended() }
    Prefs.sharedPreferences = mockPrefs()
    Prefs.preferences = Preferences(settings)
    return AddCrossingMarkings()
}
