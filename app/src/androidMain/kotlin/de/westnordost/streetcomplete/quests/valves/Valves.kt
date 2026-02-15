package de.westnordost.streetcomplete.quests.valves

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.valves.Valves.DUNLOP
import de.westnordost.streetcomplete.quests.valves.Valves.REGINA
import de.westnordost.streetcomplete.quests.valves.Valves.SCHRADER
import de.westnordost.streetcomplete.quests.valves.Valves.SCLAVERAND

enum class Valves(val osmValue: String) {
    SCHRADER("schrader"),
    SCLAVERAND("sclaverand"),
    DUNLOP("dunlop"),
    REGINA("regina");
}

val Valves.titleResId: Int get() = when (this) {
    SCHRADER ->     R.string.quest_valves_schrader
    SCLAVERAND ->   R.string.quest_valves_sclaverand
    DUNLOP ->       R.string.quest_valves_dunlop
    REGINA ->       R.string.quest_valves_regina
}

val Valves.iconResId: Int get() = when (this) {
    SCHRADER ->     R.drawable.valves_schrader
    SCLAVERAND ->   R.drawable.valves_presta
    DUNLOP ->       R.drawable.valves_dunlop
    REGINA ->       R.drawable.valves_regina
}
