package de.westnordost.streetcomplete.ui.common.quest

@PublishedApi
internal inline fun <I> handleSingleChoiceSelection(
    selection: I?,
    submitOnSelection: Boolean,
    onIntermediateSelection: (I?) -> Unit,
    onTerminalAnswer: (I) -> Unit,
) {
    if (selection != null && submitOnSelection) {
        onTerminalAnswer(selection)
    } else {
        onIntermediateSelection(selection)
    }
}
