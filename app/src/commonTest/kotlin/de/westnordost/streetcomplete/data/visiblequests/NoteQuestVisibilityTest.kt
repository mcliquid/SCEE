package de.westnordost.streetcomplete.data.visiblequests

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.AllEditTypes
import de.westnordost.streetcomplete.data.presets.EditTypePresetsSource
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.quests.note_comments.OsmNoteQuestType
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SCEE: the note quest can be switched off in expert mode */
class NoteQuestVisibilityTest {
    private val stored = mutableMapOf<String, Boolean>()
    private val other = TestQuestTypeA()
    private lateinit var ctrl: VisibleEditTypeController

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        stored.clear()
        val dao: VisibleEditTypeDao = mock {
            every { getAll(0) } calls { stored }
            every { put(any(), any(), any()) } calls { (_: Long, name: String, visible: Boolean) -> stored[name] = visible }
            every { putAll(any(), any()) } calls { (_: Long, all: Map<String, Boolean>) -> stored.putAll(all) }
        }
        val presets: EditTypePresetsSource = mock {
            every { addListener(any()) } returns Unit
            every { selectedId } returns 0
        }
        val all = AllEditTypes(listOf(QuestTypeRegistry({ listOf(0 to OsmNoteQuestType, 1 to other) })))
        ctrl = VisibleEditTypeController(dao, presets, all)
    }

    private fun expertMode(on: Boolean) { Prefs.preferences.expertMode = on }

    @Test fun `note quest is visible by default`() {
        assertTrue(ctrl.isVisible(OsmNoteQuestType))
    }

    @Test fun `visibility can only be edited in expert mode`() {
        expertMode(false)
        assertFalse(OsmNoteQuestType.visibilityEditable)
        ctrl.setVisibility(OsmNoteQuestType, false)
        assertTrue(ctrl.isVisible(OsmNoteQuestType))
        expertMode(true)
        assertTrue(OsmNoteQuestType.visibilityEditable)
    }

    @Test fun `note quest can be switched off and on again in expert mode`() {
        expertMode(true)
        ctrl.setVisibility(OsmNoteQuestType, false)
        assertFalse(ctrl.isVisible(OsmNoteQuestType))
        assertFalse(OsmNoteQuestType in ctrl.getVisible())
        assertTrue(other in ctrl.getVisible())
        ctrl.setVisibility(OsmNoteQuestType, true)
        assertTrue(ctrl.isVisible(OsmNoteQuestType))
        assertTrue(OsmNoteQuestType in ctrl.getVisible())
    }

    @Test fun `bulk changes never touch the note quest`() {
        expertMode(true)
        ctrl.setVisibilities(mapOf(OsmNoteQuestType to false, other to false))
        assertTrue(ctrl.isVisible(OsmNoteQuestType))
        assertFalse(ctrl.isVisible(other))
        ctrl.setVisibility(OsmNoteQuestType, false)
        ctrl.setVisibilities(mapOf(OsmNoteQuestType to true, other to true))
        assertFalse(ctrl.isVisible(OsmNoteQuestType))
        assertTrue(ctrl.isVisible(other))
    }

    @Test fun `other quest types are not affected by expert mode`() {
        expertMode(false)
        assertTrue(other.visibilityEditable)
        ctrl.setVisibility(other, false)
        assertEquals(false, ctrl.isVisible(other))
    }
}
