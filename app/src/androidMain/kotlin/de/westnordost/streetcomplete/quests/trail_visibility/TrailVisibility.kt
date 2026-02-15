package de.westnordost.streetcomplete.quests.trail_visibility

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.trail_visibility.TrailVisibility.*

enum class TrailVisibility(val osmValue: String) {
    EXCELLENT("excellent"),
    GOOD("good"),
    INTERMEDIATE("intermediate"),
    BAD("bad"),
    HORRIBLE("horrible"),
    NO("no")
}

val TrailVisibility.titleResId get() = when (this) {
    EXCELLENT -> R.string.quest_trail_visibility_excellent
    GOOD -> R.string.quest_trail_visibility_good
    INTERMEDIATE -> R.string.quest_trail_visibility_intermediate
    BAD -> R.string.quest_trail_visibility_bad
    HORRIBLE -> R.string.quest_trail_visibility_horrible
    NO -> R.string.quest_trail_visibility_no
}

val TrailVisibility.descriptionResId get() = when (this) {
    EXCELLENT -> R.string.quest_trail_visibility_excellent_description
    GOOD -> R.string.quest_trail_visibility_good_description
    INTERMEDIATE -> R.string.quest_trail_visibility_intermediate_description
    BAD -> R.string.quest_trail_visibility_bad_description
    HORRIBLE -> R.string.quest_trail_visibility_horrible_description
    NO -> R.string.quest_trail_visibility_no_description
}
