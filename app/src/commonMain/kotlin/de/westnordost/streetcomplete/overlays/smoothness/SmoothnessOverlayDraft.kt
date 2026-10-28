package de.westnordost.streetcomplete.overlays.smoothness

import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import kotlinx.serialization.Serializable

/** Edit state of one smoothness. Choosing the already mapped value again or tapping "still the
 *  same" confirms it. */
@Serializable
data class SmoothnessSelection(
    val original: Smoothness?,
    val selected: Smoothness? = original,
    val confirmed: Boolean = false,
) {
    /** whether the user changed or explicitly confirmed this value */
    val isTouched: Boolean get() = selected != null && (selected != original || confirmed)

    fun select(value: Smoothness) = copy(selected = value, confirmed = value == original)
    fun confirm() = copy(confirmed = true)
    fun reset() = SmoothnessSelection(original)
}

/** Everything the user entered in the smoothness overlay form.
 *
 *  The way is edited either as a whole or as separate foot- and cycleway parts. The input of
 *  the respective other mode cannot be carried over (a surface or smoothness of the whole way
 *  says nothing certain about either part, and vice versa), so switching the mode with unsaved
 *  input only happens after the user confirmed discarding it ([pendingModeSwitch]). Only the
 *  input of the current mode is ever saved. */
@Serializable
data class SmoothnessOverlayDraft(
    val isSegregated: Boolean,
    /** whether the user may switch back after switching the mode */
    val canSwitchBack: Boolean = false,
    val whole: SmoothnessSelection,
    val newSurface: Surface? = null,
    val footway: SmoothnessSelection,
    val cycleway: SmoothnessSelection,
    /** a mode switch waiting for the confirmation to discard the input of the current mode */
    val pendingModeSwitch: Boolean = false,
) {
    val hasChanges: Boolean get() =
        if (isSegregated) footway.isTouched || cycleway.isTouched
        else whole.isTouched || newSurface != null

    val isComplete: Boolean get() =
        if (isSegregated) footway.isTouched || cycleway.isTouched
        else whole.selected != null

    /** Switch to the other mode, or ask for confirmation first if input would be lost */
    fun requestModeSwitch(): SmoothnessOverlayDraft =
        if (hasChanges) copy(pendingModeSwitch = true) else switchMode()

    fun confirmModeSwitch(): SmoothnessOverlayDraft = switchMode()

    fun cancelModeSwitch(): SmoothnessOverlayDraft = copy(pendingModeSwitch = false)

    private fun switchMode() = copy(
        isSegregated = !isSegregated,
        canSwitchBack = !canSwitchBack,
        whole = whole.reset(),
        newSurface = null,
        footway = footway.reset(),
        cycleway = cycleway.reset(),
        pendingModeSwitch = false,
    )

    fun selectNewSurface(surface: Surface, currentSurface: Surface?): SmoothnessOverlayDraft {
        if (surface == currentSurface) return copy(newSurface = null)
        // a smoothness assessed for another surface is not valid for the new one
        return copy(newSurface = surface, whole = SmoothnessSelection(whole.original, selected = null))
    }

    /** The answer to save, or null if there is nothing to save */
    fun toAnswer(): SmoothnessOverlayAnswer? = when {
        !isComplete || !hasChanges -> null
        isSegregated -> PartsSmoothnessAnswer(
            footway = footway.selected.takeIf { footway.isTouched },
            cycleway = cycleway.selected.takeIf { cycleway.isTouched },
        )
        else -> SingleSmoothnessAnswer(whole.selected!!, newSurface)
    }

    companion object {
        fun of(state: SmoothnessOverlayState) = SmoothnessOverlayDraft(
            isSegregated = state.isSegregated,
            whole = SmoothnessSelection(state.whole.smoothness.validValue),
            footway = SmoothnessSelection(state.footway.smoothness.validValue),
            cycleway = SmoothnessSelection(state.cycleway.smoothness.validValue),
        )
    }
}
