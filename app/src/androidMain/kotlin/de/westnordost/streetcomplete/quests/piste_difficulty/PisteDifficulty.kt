package de.westnordost.streetcomplete.quests.piste_difficulty

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.ADVANCED
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.EASY
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.EXPERT
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.EXTREME
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.FREERIDE
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.INTERMEDIATE
import de.westnordost.streetcomplete.quests.piste_difficulty.PisteDifficulty.NOVICE

enum class PisteDifficulty(val osmValue: String) {
    NOVICE("novice"),
    EASY("easy"),
    INTERMEDIATE("intermediate"),
    ADVANCED("advanced"),
    EXPERT("expert"),
    FREERIDE("freeride"),
    EXTREME("extreme")
}

fun PisteDifficulty.isAvailable(countryCode: String) = when {
    this == NOVICE && countryCode in listOf("JP", "US", "CA", "NZ", "AU") -> false
    this == EXPERT && countryCode == "JP" -> false
    this == FREERIDE && countryCode == "JP" -> false
    this == EXTREME && countryCode == "JP" -> false
    else -> true
}

val PisteDifficulty.title get() = when (this) {
    NOVICE -> R.string.quest_piste_difficulty_novice
    EASY -> R.string.quest_piste_difficulty_easy
    INTERMEDIATE -> R.string.quest_piste_difficulty_intermediate
    ADVANCED -> R.string.quest_piste_difficulty_advanced
    EXPERT -> R.string.quest_piste_difficulty_expert
    FREERIDE -> R.string.quest_piste_difficulty_freeride
    EXTREME -> R.string.quest_piste_difficulty_extreme
}

fun PisteDifficulty.getIcon(countryCode: String): Int = when (this) {
    NOVICE ->       R.drawable.ic_quest_piste_difficulty_novice
    EASY ->         if (countryCode in listOf("JP", "US", "CA", "NZ", "AU")) R.drawable.ic_quest_piste_difficulty_novice
    else R.drawable.ic_quest_piste_difficulty_easy
    INTERMEDIATE -> if (countryCode in listOf("JP", "US", "CA", "NZ", "AU")) R.drawable.ic_quest_piste_difficulty_blue_square
    else R.drawable.ic_quest_piste_difficulty_intermediate
    ADVANCED ->     if (countryCode in listOf("US", "CA", "NZ", "AU", "FI", "SE", "NO")) R.drawable.ic_quest_piste_difficulty_black_diamond
    else R.drawable.ic_quest_piste_difficulty_advanced
    EXPERT ->       if (countryCode in listOf("US", "CA", "NZ", "AU", "FI", "SE", "NO")) R.drawable.ic_quest_piste_difficulty_double_black_diamond
    else R.drawable.ic_quest_piste_difficulty_expert
    FREERIDE ->     if (countryCode in listOf("JP", "US", "CA", "NZ", "AU")) R.drawable.ic_quest_piste_difficulty_orange_oval
    else R.drawable.ic_quest_piste_difficulty_freeride
    EXTREME ->      R.drawable.ic_quest_piste_difficulty_extreme
}
