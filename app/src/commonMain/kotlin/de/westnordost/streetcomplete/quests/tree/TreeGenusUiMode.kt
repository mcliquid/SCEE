package de.westnordost.streetcomplete.quests.tree

import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.quests.questPrefix

enum class TreeGenusUiMode { Visual, ExpertText }

const val PREF_TREE_GENUS_EXPERT_TEXT = "qs_AddTreeGenus_expert_text"

fun treeGenusUiMode(prefs: Preferences): TreeGenusUiMode =
    if (prefs.getBoolean(questPrefix(prefs) + PREF_TREE_GENUS_EXPERT_TEXT, false)) {
        TreeGenusUiMode.ExpertText
    } else {
        TreeGenusUiMode.Visual
    }

fun setTreeGenusExpertTextUi(prefs: Preferences, expert: Boolean) {
    val key = questPrefix(prefs) + PREF_TREE_GENUS_EXPERT_TEXT
    if (expert) prefs.putBoolean(key, true) else prefs.remove(key)
}

fun setTreeGenusUiMode(prefs: Preferences, mode: TreeGenusUiMode) {
    setTreeGenusExpertTextUi(prefs, expert = mode == TreeGenusUiMode.ExpertText)
}