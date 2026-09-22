package de.westnordost.streetcomplete.ui.common.quest

import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.data.osm.osmquests.QuestAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SingleChoiceSelectionTest {
    @Test fun `item select default stores selection and OK submits answer`() {
        var selected: String? = null
        val answers = mutableListOf<String>()

        handleSingleChoiceSelection(
            selection = "answer",
            submitOnSelection = false,
            onIntermediateSelection = { selected = it },
            onTerminalAnswer = answers::add,
        )

        assertEquals("answer", selected)
        assertEquals(emptyList(), answers)

        answers += selected!!

        assertEquals(listOf("answer"), answers)
    }

    @Test fun `item select immediate submits answer without storing selection`() {
        var selected: String? = null
        val answers = mutableListOf<String>()

        handleSingleChoiceSelection(
            selection = "answer",
            submitOnSelection = true,
            onIntermediateSelection = { selected = it },
            onTerminalAnswer = answers::add,
        )

        assertNull(selected)
        assertEquals(listOf("answer"), answers)
    }

    @Test fun `radio group default stores selection and OK submits answer`() {
        var selected: String? = null
        val answers = mutableListOf<String>()

        handleSingleChoiceSelection(
            selection = "answer",
            submitOnSelection = false,
            onIntermediateSelection = { selected = it },
            onTerminalAnswer = answers::add,
        )

        assertEquals("answer", selected)
        assertEquals(emptyList(), answers)

        answers += selected!!

        assertEquals(listOf("answer"), answers)
    }

    @Test fun `radio group immediate submits answer without storing selection`() {
        var selected: String? = null
        val answers = mutableListOf<String>()

        handleSingleChoiceSelection(
            selection = "answer",
            submitOnSelection = true,
            onIntermediateSelection = { selected = it },
            onTerminalAnswer = answers::add,
        )

        assertNull(selected)
        assertEquals(listOf("answer"), answers)
    }

    @Test fun `group header remains intermediate and broad group answer requires confirmation`() {
        var selectedGroup: String? = null
        var confirmationRequestedFor: String? = null
        val answers = mutableListOf<String>()

        selectedGroup = "broad group"

        assertEquals("broad group", selectedGroup)
        assertEquals(emptyList(), answers)

        confirmationRequestedFor = selectedGroup

        assertEquals("broad group", confirmationRequestedFor)
        assertEquals(emptyList(), answers)

        answers += confirmationRequestedFor!!

        assertEquals(listOf("broad group"), answers)
    }

    @Test fun `grouped concrete item only submits when explicitly configured`() {
        var selectedItem: String? = null
        val answers = mutableListOf<String>()

        handleSingleChoiceSelection(
            selection = "default item",
            submitOnSelection = false,
            onIntermediateSelection = { selectedItem = it },
            onTerminalAnswer = answers::add,
        )
        assertEquals("default item", selectedItem)
        assertEquals(emptyList(), answers)

        handleSingleChoiceSelection(
            selection = "immediate item",
            submitOnSelection = true,
            onIntermediateSelection = { selectedItem = it },
            onTerminalAnswer = answers::add,
        )
        assertEquals("default item", selectedItem)
        assertEquals(listOf("immediate item"), answers)
    }

    @Test fun `answer interceptor receives immediate answer normally`() {
        val interceptedActions = mutableListOf<QuestAction<String>>()
        val interceptor: (QuestAction<String>) -> Unit = interceptedActions::add

        handleSingleChoiceSelection<String>(
            selection = "answer",
            submitOnSelection = true,
            onIntermediateSelection = { _ -> },
            onTerminalAnswer = { interceptor(Answer(it)) },
        )

        assertEquals("answer", (interceptedActions.single() as Answer).value)
    }
}
