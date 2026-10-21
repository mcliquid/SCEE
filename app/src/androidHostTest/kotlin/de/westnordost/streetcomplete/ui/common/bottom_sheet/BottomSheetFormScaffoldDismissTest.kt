package de.westnordost.streetcomplete.ui.common.bottom_sheet

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class BottomSheetFormScaffoldDismissTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `close button calls dismiss without saving`() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalPersistentQuestFormCloseButton provides true,
                        LocalQuestFormEscapeDismiss provides false,
                    ) {
                        BottomSheetFormScaffold(
                            onDismissRequest = { dismissed = true },
                            header = { Text("Quest title") },
                            content = { Text("Answers") },
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test fun `escape dismiss disabled while keyboardDismissEnabled is false`() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    CompositionLocalProvider(
                        LocalPersistentQuestFormCloseButton provides true,
                        LocalQuestFormEscapeDismiss provides true,
                    ) {
                        BottomSheetFormScaffold(
                            onDismissRequest = { dismissed = true },
                            keyboardDismissEnabled = false,
                            header = { Text("Quest title") },
                            content = { Text("Answers") },
                        )
                    }
                }
            }
        }
        assertFalse(dismissed)
    }
}
