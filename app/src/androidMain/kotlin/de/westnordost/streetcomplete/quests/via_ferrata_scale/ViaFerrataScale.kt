package de.westnordost.streetcomplete.quests.via_ferrata_scale

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.via_ferrata_scale.ViaFerrataScale.*

enum class ViaFerrataScale(val osmValue: String) {
    ZERO("0"),
    ONE("1"),
    TWO("2"),
    THREE("3"),
    FOUR("4"),
    FIVE("5"),
    SIX("6")
}

val ViaFerrataScale.imageResId get() = when (this) {
    ZERO -> R.drawable.via_ferrata_scale_0
    ONE -> R.drawable.via_ferrata_scale_1
    TWO -> R.drawable.via_ferrata_scale_2
    THREE -> R.drawable.via_ferrata_scale_3
    FOUR -> R.drawable.via_ferrata_scale_4
    FIVE -> R.drawable.via_ferrata_scale_5
    SIX -> R.drawable.via_ferrata_scale_6
}

val ViaFerrataScale.titleResId get() = when (this) {
    ZERO -> R.string.quest_viaFerrataScale_zero
    ONE -> R.string.quest_viaFerrataScale_one
    TWO -> R.string.quest_viaFerrataScale_two
    THREE -> R.string.quest_viaFerrataScale_three
    FOUR -> R.string.quest_viaFerrataScale_four
    FIVE -> R.string.quest_viaFerrataScale_five
    SIX -> R.string.quest_viaFerrataScale_six
}

val ViaFerrataScale.descriptionResId get() = when (this) {
    ZERO -> R.string.quest_viaFerrataScale_zero_description
    ONE -> R.string.quest_viaFerrataScale_one_description
    TWO -> R.string.quest_viaFerrataScale_two_description
    THREE -> R.string.quest_viaFerrataScale_three_description
    FOUR -> R.string.quest_viaFerrataScale_four_description
    FIVE -> R.string.quest_viaFerrataScale_five_description
    SIX -> R.string.quest_viaFerrataScale_six_description
}
