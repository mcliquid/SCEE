package de.westnordost.streetcomplete.ui.common.item_select

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheonjaeung.compose.grid.SimpleGridCells
import com.cheonjaeung.compose.grid.VerticalGrid
import de.westnordost.streetcomplete.ui.ktx.selectionFrame
import de.westnordost.streetcomplete.ui.common.quest.QuestFormShortcutDigitHint
import de.westnordost.streetcomplete.ui.common.quest.LocalQuestFormKeyboardShortcutsEnabled
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/** Vertical grid of items where one item can be selected */
@Composable
fun <I> ItemSelectGrid(
    columns: SimpleGridCells,
    items: List<I>,
    selectedItem: I?,
    onSelect: (item: I?) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: I) -> Unit,
) {
    VerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (item in items) {
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .selectionFrame(isSelected)
                    .selectable(isSelected) { onSelect(if (isSelected) null else item) },
                contentAlignment = Alignment.Center,
            ) {
                itemContent(item)
            }
        }
    }
}

/** Like [ItemSelectGrid] but shows 1–9 shortcut hints on desktop (visible row-major order). */
@Composable
fun <I> ItemSelectGridWithShortcutHints(
    columns: SimpleGridCells,
    items: List<I>,
    selectedItem: I?,
    onSelect: (item: I?) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: I) -> Unit,
) {
    VerticalGrid(
        columns = columns,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selectedItem
            Box(
                modifier = Modifier
                    .selectionFrame(isSelected)
                    .selectable(isSelected) { onSelect(if (isSelected) null else item) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (LocalQuestFormKeyboardShortcutsEnabled.current && index < 9) {
                        QuestFormShortcutDigitHint(
                            digit = index + 1,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                    itemContent(item)
                }
            }
        }
    }
}
