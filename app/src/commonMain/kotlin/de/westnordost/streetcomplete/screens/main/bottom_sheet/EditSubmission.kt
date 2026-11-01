package de.westnordost.streetcomplete.screens.main.bottom_sheet

/** One persisted edit per displayed form; call only from UI callbacks. */
internal class EditSubmission {
    private var submitted = false

    fun run(submit: () -> Unit) {
        if (submitted) return
        submitted = true
        try {
            submit()
        } catch (e: Throwable) {
            submitted = false
            throw e
        }
    }
}
