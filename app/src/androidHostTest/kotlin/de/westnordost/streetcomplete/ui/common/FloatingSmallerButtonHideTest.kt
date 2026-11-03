package de.westnordost.streetcomplete.ui.common

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FloatingSmallerButtonHideTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `short press triggers temp hide only`() {
        var tempHideCount = 0
        var permanentHideCount = 0
        compose.setContent {
            MaterialTheme {
                FloatingSmallerButton(
                    onClick = { tempHideCount++ },
                    onLongClick = { permanentHideCount++ },
                    modifier = Modifier.testTag("hide"),
                ) {
                    Text("H")
                }
            }
        }
        compose.onNodeWithTag("hide").performClick()
        compose.waitForIdle()
        assertEquals(1, tempHideCount)
        assertEquals(0, permanentHideCount)
    }

    @Test
    fun `long press triggers permanent hide only`() {
        var tempHideCount = 0
        var permanentHideCount = 0
        compose.setContent {
            MaterialTheme {
                FloatingSmallerButton(
                    onClick = { tempHideCount++ },
                    onLongClick = { permanentHideCount++ },
                    modifier = Modifier.testTag("hide"),
                ) {
                    Text("H")
                }
            }
        }
        compose.onNodeWithTag("hide").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals(0, tempHideCount)
        assertEquals(1, permanentHideCount)
    }
}
