package de.westnordost.streetcomplete.quests.is_sidepath

sealed interface IsSidepathAnswer {
    data object Yes : IsSidepathAnswer
    data object No : IsSidepathAnswer
    data object IsSidewalk : IsSidepathAnswer
    data object IsCrossing : IsSidepathAnswer
}
