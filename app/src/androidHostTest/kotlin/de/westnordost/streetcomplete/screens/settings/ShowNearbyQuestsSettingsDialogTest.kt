package de.westnordost.streetcomplete.screens.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.ui.theme.AppTheme
import io.github.vinceglb.filekit.initializer.FileKitInitializer
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class ShowNearbyQuestsSettingsDialogTest {

    // strings_ee.xml uses U+2019, not ASCII apostrophe
    private val dontShow = "Don\u2019t show nearby quests"
    private val showEnabled = "Show only enabled quests types"

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val preferences = inMemoryPrefs()

    @Before fun initFileKit() {
        FileKitInitializer().create(compose.activity)
    }

    private fun showDialog(prefilled: () -> Unit = {}) {
        Prefs.preferences = preferences
        prefilled()
        var open by mutableStateOf(true)
        compose.setContent {
            AppTheme {
                if (open) {
                    ShowNearbyQuestsDialog(preferences) { open = false }
                }
            }
        }
    }

    @Test fun `dialog opens with dont show selected by default`() {
        showDialog()
        compose.onNodeWithText(dontShow).assertIsSelected()
        compose.onNodeWithText(showEnabled).assertIsNotSelected()
    }

    @Test fun `dialog opens with saved mode selected`() {
        showDialog { preferences.putInt(Prefs.SHOW_NEARBY_QUESTS, 1) }
        compose.onNodeWithText(showEnabled).assertIsSelected()
        compose.onNodeWithText(dontShow).assertIsNotSelected()
    }

    @Test fun `cancel leaves mode saved on radio change like legacy scee`() {
        showDialog()
        compose.onNodeWithText(showEnabled).performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, preferences.getInt(Prefs.SHOW_NEARBY_QUESTS, 0))
    }

    @Test fun `dismiss request does not apply distance like cancel`() {
        Prefs.preferences = preferences
        preferences.putFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 2f)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                key(generation) {
                    ShowNearbyQuestsDialog(preferences) { generation++ }
                }
            }
        }
        compose.onNodeWithText(showEnabled).performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, preferences.getInt(Prefs.SHOW_NEARBY_QUESTS, 0))
        assertEquals(2f, preferences.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 0f))
    }

    @Test fun `ok persists mode and distance`() {
        showDialog()
        compose.onNodeWithText(showEnabled).performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, preferences.getInt(Prefs.SHOW_NEARBY_QUESTS, 0))
        assertEquals(0f, preferences.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, -1f))
    }

    @Test fun `regression ok after selection persists mode for next open`() {
        Prefs.preferences = preferences
        var generation by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                key(generation) {
                    ShowNearbyQuestsDialog(preferences) { generation++ }
                }
            }
        }
        compose.onNodeWithText(showEnabled).performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(1, preferences.getInt(Prefs.SHOW_NEARBY_QUESTS, 0))
        compose.waitForIdle()
        compose.onNodeWithText(showEnabled).assertIsSelected()
    }

    @Test fun `reopened dialog shows persisted selection`() {
        Prefs.preferences = preferences
        preferences.putInt(Prefs.SHOW_NEARBY_QUESTS, 1)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                key(generation) {
                    ShowNearbyQuestsDialog(preferences) { generation++ }
                }
            }
        }
        compose.onNodeWithText(showEnabled).assertIsSelected()
        compose.onNodeWithText(dontShow).performClick()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        generation++
        compose.waitForIdle()
        compose.onNodeWithText(dontShow).assertIsSelected()
    }
}