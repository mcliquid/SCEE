package de.westnordost.streetcomplete.ui.common

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import de.westnordost.streetcomplete.ui.ktx.fadingEdges
import de.westnordost.streetcomplete.ui.ktx.pxToDp
import kotlinx.coroutines.launch

@Composable
fun rememberWheelPickerState(selectedItemIndex: Int = 0) =
    remember { WheelPickerState(selectedItemIndex) }

class WheelPickerState(initialSelectedItemIndex: Int = 0) : ScrollableState {

    private val initialSelectedItemIndex = initialSelectedItemIndex.coerceAtLeast(0)

    internal val lazyListState = LazyListState(firstVisibleItemIndex = initialSelectedItemIndex)

    val selectedItemIndex: Int by derivedStateOf {
        selectedItemInfo?.index ?: initialSelectedItemIndex
    }

    internal val selectedItemInfo: LazyListItemInfo? by derivedStateOf {
        lazyListState.layoutInfo.findCenterItem()
    }

    override val canScrollBackward: Boolean
        get() = lazyListState.canScrollBackward

    override val canScrollForward: Boolean
        get() = lazyListState.canScrollForward

    override val isScrollInProgress: Boolean
        get() = lazyListState.isScrollInProgress

    suspend fun scrollToItem(index: Int) {
        lazyListState.scrollToItem(index)
    }

    suspend fun animateScrollToItem(index: Int) {
        lazyListState.animateScrollToItem(index)
    }

    override fun dispatchRawDelta(delta: Float): Float =
        lazyListState.dispatchRawDelta(delta)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) {
        lazyListState.scroll(scrollPriority, block)
    }
}

/** A WheelPicker aka NumberPicker (in Android). Presents the selectable [items] on a draggable
 *  vertical wheel, the item displayed in the center is selected.
 *  [visibleAdjacentItems] determines how many adjacent items to the one that is selected should
 *  be displayed. */
@Composable
fun <T> WheelPicker(
    items: List<T>,
    modifier: Modifier = Modifier,
    state: WheelPickerState = rememberWheelPickerState(),
    key: ((T) -> Any)? = null,
    visibleAdjacentItems: Int = 1,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable (item: T) -> Unit
) {
    val scope = rememberCoroutineScope()

    val measuredItemHeight = (state.selectedItemInfo?.size ?: 0).pxToDp()
    val selectedItemHeight = if (measuredItemHeight > 0.dp) measuredItemHeight else DefaultItemHeight

    val paddingValues = remember(visibleAdjacentItems, selectedItemHeight) {
        PaddingValues(vertical = selectedItemHeight * visibleAdjacentItems)
    }

    val visibleItemsCount = visibleAdjacentItems * 2 + 1

    // fadingEdges uses CompositingStrategy.Offscreen; on desktop that breaks pointer input on the
    // scrollable LazyColumn when applied on the same modifier chain.
    Box(
        modifier = modifier
            .height(selectedItemHeight * visibleItemsCount)
            .fadingEdges(
                top = selectedItemHeight * visibleAdjacentItems,
                bottom = selectedItemHeight * visibleAdjacentItems,
                startAlpha = 0.67f,
            )
            .pickerIndicator(selectedItemHeight, color = MaterialTheme.colors.onSurface),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = state.lazyListState,
            contentPadding = paddingValues,
            horizontalAlignment = horizontalAlignment,
            flingBehavior = rememberSnapFlingBehavior(state.lazyListState),
        ) {
            items(
                count = items.size,
                key = key?.let { { key(items[it]) } }
            ) { index ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .scale(if (index == state.selectedItemIndex) 1f else 0.8f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { scope.launch { state.animateScrollToItem(index) } },
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    content(items[index])
                }
            }
        }
    }
}

private val DefaultItemHeight = 40.dp

/** frame drawn around selected value */
private fun Modifier.pickerIndicator(
    selectedItemHeight: Dp,
    color: Color,
): Modifier =
    drawWithContent {
        drawContent()

        val strokeWidth = 2f  * density
        val inset = (size.height - selectedItemHeight.toPx()) / 2

        inset(vertical = inset.coerceAtMost(size.height / 2)) {
            drawLine(
                color = color,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = strokeWidth,
            )
            drawLine(
                color = color,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = strokeWidth,
            )
        }
    }

private fun LazyListLayoutInfo.findCenterItem(): LazyListItemInfo? =
    findWheelPickerCenterItemIndex(
        visibleItems = visibleItemsInfo.map { WheelPickerVisibleItem(it.index, it.offset, it.size) },
        viewportStartOffset = viewportStartOffset,
        viewportHeight = viewportSize.height,
    )?.let { centerIndex -> visibleItemsInfo.fastFirstOrNull { it.index == centerIndex } }

internal data class WheelPickerVisibleItem(val index: Int, val offset: Int, val size: Int)

internal fun findWheelPickerCenterItemIndex(
    visibleItems: List<WheelPickerVisibleItem>,
    viewportStartOffset: Int,
    viewportHeight: Int,
): Int? = visibleItems.firstOrNull {
    it.offset + it.size - viewportStartOffset > viewportHeight / 2
}?.index

@Preview
@Composable
private fun PreviewPicker() {
    WheelPicker(
        items = (1..100).toList(),
    ) { item ->
        Text(item.toString())
    }
}
