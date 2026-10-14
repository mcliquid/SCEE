package de.westnordost.streetcomplete.quests.is_sidepath

sealed interface IsSidepathAnswer {
    /** Sidepath of a road. [ofName] / [ofHighway] come from the selected road when available. */
    data class Yes(val ofName: String?, val ofHighway: String? = null) : IsSidepathAnswer
    data object No : IsSidepathAnswer
    data object IsSidewalk : IsSidepathAnswer
    data object IsCrossing : IsSidepathAnswer
}
