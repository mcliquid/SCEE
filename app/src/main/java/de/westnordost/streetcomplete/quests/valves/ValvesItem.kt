package de.westnordost.streetcomplete.quests.valves

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.valves.*
import de.westnordost.streetcomplete.view.image_select.Item

fun ValvesType.asItem() = Item(this, iconResId, titleResId)

private val ValvesType.titleResId: Int get() = when (this) {
    ValvesType.SCLAVERAND ->   R.string.quest_valves_sclaverand
    ValvesType.DUNLOP ->       R.string.quest_valves_dunlop
    ValvesType.SCHRADER ->     R.string.quest_valves_schrader
    ValvesType.REGINA ->       R.string.quest_valves_regina
}

private val ValvesType.iconResId: Int get() = when (this) {
    ValvesType.SCLAVERAND ->   R.drawable.valves_presta
    ValvesType.DUNLOP ->       R.drawable.valves_dunlop
    ValvesType.SCHRADER ->     R.drawable.valves_schrader
    ValvesType.REGINA ->       R.drawable.valves_regina
}
