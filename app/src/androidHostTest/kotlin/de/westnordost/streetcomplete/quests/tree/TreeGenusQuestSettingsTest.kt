package de.westnordost.streetcomplete.quests.tree

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
class TreeGenusQuestSettingsTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val preferences = inMemoryPrefs()

    @Before fun initFileKit() {
        FileKitInitializer().create(compose.activity)
    }

    private fun show(prefilled: () -> Unit = {}) {
        Prefs.preferences = preferences
        prefilled()
        compose.setContent {
            AppTheme {
                AddTreeGenus().QuestSettings {}
            }
        }
    }

    @Test fun `dialog opens with visual mode selected by default`() {
        show()
        compose.onNodeWithText("Image-based selection").assertIsSelected()
        compose.onNodeWithText("Expert text-based selection").assertIsNotSelected()
    }

    @Test fun `dialog opens with expert mode selected when saved`() {
        show { setTreeGenusUiMode(preferences, TreeGenusUiMode.ExpertText) }
        compose.onNodeWithText("Expert text-based selection").assertIsSelected()
        compose.onNodeWithText("Image-based selection").assertIsNotSelected()
    }

    @Test fun `cancel discards pending ui mode change`() {
        show()
        compose.onNodeWithText("Expert text-based selection").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(TreeGenusUiMode.Visual, treeGenusUiMode(preferences))
    }

    @Test fun `ok persists ui mode change`() {
        show()
        compose.onNodeWithText("Expert text-based selection").performClick()
        compose.onNodeWithText("OK").performClick()
        assertEquals(TreeGenusUiMode.ExpertText, treeGenusUiMode(preferences))
    }

    @Test fun `reopened dialog shows persisted selection`() {
        Prefs.preferences = preferences
        setTreeGenusUiMode(preferences, TreeGenusUiMode.ExpertText)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            AppTheme {
                key(generation) {
                    AddTreeGenus().QuestSettings {}
                }
            }
        }
        compose.onNodeWithText("Image-based selection").performClick()
        compose.onNodeWithText("Cancel").performClick()
        generation++
        compose.waitForIdle()
        compose.onNodeWithText("Expert text-based selection").assertIsSelected()
    }
}