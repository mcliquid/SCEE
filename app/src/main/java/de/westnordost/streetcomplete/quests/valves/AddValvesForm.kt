package de.westnordost.streetcomplete.quests.valves

import de.westnordost.streetcomplete.quests.AImageListQuestForm

class AddValvesForm : AImageListQuestForm<ValvesType, ValvesType>() {

    override val items = ValvesType.entries.map { it.asItem() }
    override val itemsPerRow = 2

    override fun onClickOk(selectedItems: List<ValvesType>) {
        applyAnswer(selectedItems.single())
    }
}
