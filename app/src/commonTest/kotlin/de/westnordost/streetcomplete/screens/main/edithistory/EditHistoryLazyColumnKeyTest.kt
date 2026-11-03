package de.westnordost.streetcomplete.screens.main.edithistory

import de.westnordost.streetcomplete.testutils.questHidden
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class EditHistoryLazyColumnKeyTest {

    @Test
    fun `duplicate quest hidden keys differ by timestamp in lazy column keys`() {
        val first = questHidden(elementId = 144046620L, timestamp = 100L)
        val second = first.copy(createdTimestamp = 200L)
        assertEquals(first.key, second.key)
        assertNotEquals(
            editHistoryLazyColumnKey(first),
            editHistoryLazyColumnKey(second),
        )
    }

    @Test
    fun `same hide event produces stable lazy column key`() {
        val edit = questHidden(timestamp = 100L)
        assertEquals(editHistoryLazyColumnKey(edit), editHistoryLazyColumnKey(edit))
    }
}
