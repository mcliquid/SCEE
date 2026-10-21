package de.westnordost.streetcomplete.quests.elevator_bicycle_access

import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.elevator_bicycle_access.ElevatorBicycleAccessAnswer.*
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddElevatorBicycleAccessTest {
    private lateinit var questType: AddElevatorBicycleAccess

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddElevatorBicycleAccess()
    }

    @Test fun `applicable to highway elevator node`() {
        val mapData = TestMapDataWithGeometry(listOf(node(1, tags = mapOf("highway" to "elevator"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `applicable to highway elevator way`() {
        val mapData = TestMapDataWithGeometry(listOf(way(1, tags = mapOf("highway" to "elevator"))))
        assertEquals(1, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable with access private`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("highway" to "elevator", "access" to "private")),
                way(2, tags = mapOf("highway" to "elevator", "access" to "private")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable with access no`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("highway" to "elevator", "access" to "no")),
                way(2, tags = mapOf("highway" to "elevator", "access" to "no")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable when bicycle is already set`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("highway" to "elevator", "bicycle" to "yes")),
                way(2, tags = mapOf("highway" to "elevator", "bicycle" to "no")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `not applicable to other highway values`() {
        val mapData = TestMapDataWithGeometry(
            listOf(
                node(1, tags = mapOf("highway" to "steps")),
                way(2, tags = mapOf("highway" to "footway")),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `yes answer sets bicycle yes only`() {
        assertEquals(
            setOf(StringMapEntryAdd("bicycle", "yes")),
            questType.answerAppliedTo(YES, mapOf("highway" to "elevator")),
        )
    }

    @Test fun `no answer sets bicycle no only`() {
        assertEquals(
            setOf(StringMapEntryAdd("bicycle", "no")),
            questType.answerAppliedTo(NO, mapOf("highway" to "elevator")),
        )
    }

    @Test fun `private answer sets access private only`() {
        assertEquals(
            setOf(StringMapEntryAdd("access", "private")),
            questType.answerAppliedTo(PRIVATE, mapOf("highway" to "elevator", "incline" to "up")),
        )
    }

    @Test fun `form uses one-click answers without ok button`() {
        val questSource = readSource("quests/elevator_bicycle_access/AddElevatorBicycleAccess.kt")
        assertTrue(questSource.contains("on(Answer(NO))"))
        assertTrue(questSource.contains("on(Answer(YES))"))
        assertTrue(questSource.contains("on(Answer(PRIVATE))"))
        assertFalse(questSource.contains("onClickOk"))
        assertFalse(questSource.contains("FloatingOkButton"))

        val yesNoFormSource = readSource("ui/common/quest/YesNoQuestForm.kt")
        assertTrue(yesNoFormSource.contains("on(Answer(false))"))
        assertTrue(yesNoFormSource.contains("on(Answer(true))"))
        assertFalse(yesNoFormSource.contains("onClickOk"))
        assertFalse(yesNoFormSource.contains("FloatingOkButton"))
    }

    @Test fun `quest id is unique in registry`() {
        val registrySource = readSource("quests/QuestTypesRegistry.kt")
        val questId = "EE_QUEST_OFFSET + 205"
        assertEquals(1, registrySource.split(questId).size - 1)
        assertEquals(1, registrySource.split("AddElevatorBicycleAccess()").size - 1)
        assertEquals(EE_QUEST_OFFSET + 205, EXPECTED_QUEST_ORDINAL)
    }

    private fun readSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/commonMain/kotlin/de/westnordost/streetcomplete/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Could not find source: $relativePath")
    }

    companion object {
        const val EXPECTED_QUEST_ORDINAL = EE_QUEST_OFFSET + 205
    }
}
