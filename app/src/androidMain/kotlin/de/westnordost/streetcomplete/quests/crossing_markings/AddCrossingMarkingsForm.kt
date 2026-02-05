package de.westnordost.streetcomplete.quests.crossing_markings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.westnordost.streetcomplete.quests.AItemsSelectQuestForm
import de.westnordost.streetcomplete.ui.common.item_select.ImageWithLabel
import de.westnordost.streetcomplete.util.image.compatPainterResource
import kotlinx.serialization.serializer

class AddCrossingMarkingsForm : AItemsSelectQuestForm<CrossingMarkings, Set<CrossingMarkings>>() {

    override val items = CrossingMarkings.entries.filter { it != CrossingMarkings.YES }
    override val serializer = serializer<CrossingMarkings>()

    override val itemsPerRow = 3

    @Composable override fun ItemContent(item: CrossingMarkings) {
        ImageWithLabel(compatPainterResource(item.imageResId), stringResource(item.titleResId))
    }

    override fun onClickOk(selectedItems: Set<CrossingMarkings>) {
        applyAnswer(selectedItems)
    }

    override fun onSelect(item: CrossingMarkings, selected: Boolean) {
        if (item == CrossingMarkings.NO) {
            selectedItems.value = if (selected) setOf(item) else emptySet()
        } else {
            selectedItems.value =
                if (selected) { selectedItems.value + item - CrossingMarkings.NO }
                else { selectedItems.value - item }
        }
        checkIsFormComplete()
    }
}
