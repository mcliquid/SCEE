package de.westnordost.streetcomplete.util.platform

/** Short system-level message for situations where no in-window popup can be shown. */
interface PlatformToaster {
    /** whether a message shown through [show] is visible on top of dialogs */
    val canShowOverDialogs: Boolean

    /** Safe to call from any thread. Never throws; platforms without a display only log the text. */
    fun show(text: String)
}
