package de.westnordost.streetcomplete.quests.valves

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.valves.Valves.*
import de.westnordost.streetcomplete.view.image_select.DisplayItem
import de.westnordost.streetcomplete.view.image_select.Item

fun Valves.asItem(): DisplayItem<Valves>? {
val iconResId = iconResId ?: return null
val titleResId = titleResId ?: return null
return Item(this, iconResId, titleResId)
}

val Valves.titleResId: Int get() = when (this) {
    SCLAVERAND ->   R.string.quest_valves_sclaverand
    DUNLOP ->       R.string.quest_valves_dunlop
    SCHRADER ->     R.string.quest_valves_schrader
    REGINA ->       R.string.quest_valves_regina
}

val Valves.iconResId: Int get() = when (this) {
    SCLAVERAND ->   R.drawable.valves_presta
    DUNLOP ->       R.drawable.valves_dunlop
    SCHRADER ->     R.drawable.valves_schrader
    REGINA ->       R.drawable.valves_regina
}
