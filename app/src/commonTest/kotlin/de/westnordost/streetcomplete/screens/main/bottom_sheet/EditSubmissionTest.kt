package de.westnordost.streetcomplete.screens.main.bottom_sheet

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EditSubmissionTest {
    @Test fun `rapid and reentrant calls create only one edit`() {
        val submission = EditSubmission()
        var edits = 0
        submission.run {
            edits++
            submission.run { edits++ }
        }
        repeat(10) { submission.run { edits++ } }
        assertEquals(1, edits)
    }

    @Test fun `synchronous save failure propagates and permits retry`() {
        val submission = EditSubmission()
        assertFailsWith<IllegalArgumentException> { submission.run { errorArgument() } }
        var edits = 0
        submission.run { edits++ }
        assertEquals(1, edits)
    }

    @Test fun `a new form can submit independently`() {
        var edits = 0
        EditSubmission().run { edits++ }
        EditSubmission().run { edits++ }
        assertEquals(2, edits)
    }

    private fun errorArgument(): Unit = throw IllegalArgumentException("save failed")
}
