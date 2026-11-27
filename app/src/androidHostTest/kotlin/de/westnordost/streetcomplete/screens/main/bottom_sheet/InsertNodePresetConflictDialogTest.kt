package de.westnordost.streetcomplete.screens.main.bottom_sheet

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class InsertNodePresetConflictDialogTest {
    @get:Rule val compose = createComposeRule()

    private val conflicts = listOf(PresetTagConflict("highway", "crossing", "traffic_signals"))

    @Test fun `shows conflicting keys and values`() {
        compose.setContent {
            MaterialTheme {
                InsertNodePresetConflictDialog(conflicts, onKeepExisting = {}, onReplace = {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Existing tags differ from the preset").assertIsDisplayed()
        compose.onNodeWithText("highway: crossing → traffic_signals").assertIsDisplayed()
        compose.onNodeWithText("Keep existing").assertIsDisplayed()
        compose.onNodeWithText("Use preset values").assertIsDisplayed()
    }

    @Test fun `keep existing and dismiss do not replace`() {
        var choice: Boolean? = null
        compose.setContent {
            MaterialTheme {
                InsertNodePresetConflictDialog(
                    conflicts,
                    onKeepExisting = { choice = false },
                    onReplace = { choice = true },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Keep existing").performClick()
        assertEquals(false, choice)
    }

    @Test fun `replace applies preset values`() {
        var choice: Boolean? = null
        compose.setContent {
            MaterialTheme {
                InsertNodePresetConflictDialog(
                    conflicts,
                    onKeepExisting = { choice = false },
                    onReplace = { choice = true },
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Use preset values").performClick()
        assertEquals(true, choice)
    }

    @Test fun `no choice until user acts`() {
        var choice: Boolean? = null
        compose.setContent {
            MaterialTheme {
                InsertNodePresetConflictDialog(
                    conflicts,
                    onKeepExisting = { choice = false },
                    onReplace = { choice = true },
                )
            }
        }
        compose.waitForIdle()
        assertNull(choice)
    }
}