package de.westnordost.streetcomplete.ui.common

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WheelPickerInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `arrow keys change selected item`() {
        val state = WheelPickerState(0)
        compose.setContent {
            MaterialTheme {
                WheelPicker(
                    items = listOf(1, 2, 3, 4, 5),
                    state = state,
                    requestFocus = true,
                ) { Text(it.toString()) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(1, state.selectedItemIndex)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionUp) }
        compose.waitForIdle()
        assertEquals(0, state.selectedItemIndex)
    }

    @Test fun `tap on neighbor scrolls selection`() {
        val state = WheelPickerState(0)
        compose.setContent {
            MaterialTheme {
                WheelPicker(
                    items = listOf(1, 2, 3, 4, 5),
                    state = state,
                ) { Text(it.toString()) }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("2").performClick()
        compose.waitForIdle()
        assertEquals(1, state.selectedItemIndex)
    }

    @Test fun `touch drag scrolls selection`() {
        val state = WheelPickerState(0)
        compose.setContent {
            MaterialTheme {
                WheelPicker(
                    items = listOf(1, 2, 3, 4, 5),
                    state = state,
                ) { Text(it.toString()) }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performTouchInput {
            swipeUp(startY = centerY, endY = centerY - 80f)
        }
        compose.waitForIdle()
        assertEquals(1, state.selectedItemIndex)
    }
}
