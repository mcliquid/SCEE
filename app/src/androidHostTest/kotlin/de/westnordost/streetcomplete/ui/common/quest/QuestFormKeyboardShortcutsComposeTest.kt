package de.westnordost.streetcomplete.ui.common.quest

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import de.westnordost.streetcomplete.ui.common.bottom_sheet.BottomSheetFormScaffold
import de.westnordost.streetcomplete.ui.common.bottom_sheet.LocalPersistentQuestFormCloseButton
import de.westnordost.streetcomplete.ui.common.bottom_sheet.LocalQuestFormEscapeDismiss
import de.westnordost.streetcomplete.ui.common.quest.LocalQuestFormKeyboardShortcutsEnabled
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
@OptIn(ExperimentalTestApi::class)
class QuestFormKeyboardShortcutsComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `answer bar digits and enter use same handlers as buttons`() {
        var answer: String? = null
        var submitted = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPersistentQuestFormCloseButton provides true,
                    LocalQuestFormEscapeDismiss provides true,
                    LocalQuestFormKeyboardShortcutsEnabled provides true,
                ) {
                    BottomSheetFormScaffold(
                        onDismissRequest = {},
                        content = {
                            QuestFormKeyboardLayer(
                                onSubmit = { submitted = true },
                                canSubmit = { answer != null },
                            )
                            QuestAnswerButtonBar(
                                answers = listOf(
                                    AnswerItem("No") { answer = "no" },
                                    AnswerItem("Yes") { answer = "yes" },
                                ),
                            )
                        },
                        fab = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.Two) }
        assertEquals("yes", answer)
        compose.onRoot().performKeyInput { keyDown(Key.Enter) }
        assertTrue(submitted)
    }

    @Test fun `binary Y and N invoke yes and no handlers`() {
        var yes = false
        var no = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPersistentQuestFormCloseButton provides true,
                    LocalQuestFormEscapeDismiss provides true,
                    LocalQuestFormKeyboardShortcutsEnabled provides true,
                ) {
                    BottomSheetFormScaffold(
                        onDismissRequest = {},
                        content = {
                            QuestFormKeyboardLayer(
                                binaryYesNo = QuestFormBinaryYesNo(onYes = { yes = true }, onNo = { no = true }),
                            )
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.Y) }
        assertTrue(yes)
        compose.onRoot().performKeyInput { keyDown(Key.N) }
        assertTrue(no)
    }

    @Test fun `escape still dismisses when shortcuts enabled`() {
        var dismissed = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPersistentQuestFormCloseButton provides true,
                    LocalQuestFormEscapeDismiss provides true,
                ) {
                    BottomSheetFormScaffold(
                        onDismissRequest = { dismissed = true },
                        content = { QuestAnswerButtonBar(answers = listOf(AnswerItem("Yes") {})) },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.Escape) }
        assertTrue(dismissed)
    }

    @Test fun `dialog keyboard host routes digit keys`() {
        var selected: String? = null
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalPersistentQuestFormCloseButton provides true,
                    LocalQuestFormEscapeDismiss provides true,
                    LocalQuestFormKeyboardShortcutsEnabled provides true,
                ) {
                    BottomSheetFormScaffold(
                        onDismissRequest = {},
                        content = {
                            QuestFormKeyboardLayer(
                                numberedOptions = {
                                    listOf(
                                        { selected = "A" },
                                        { selected = "B" },
                                        { selected = "C" },
                                    )
                                },
                            )
                            QuestFormDialogKeyboardHost { }
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.Three) }
        assertEquals("C", selected)
    }

    @Test fun `shortcuts inactive on android platform gate`() {
        var answer = false
        compose.setContent {
            MaterialTheme {
                BottomSheetFormScaffold(
                    onDismissRequest = {},
                    content = {
                        QuestAnswerButtonBar(answers = listOf(AnswerItem("Yes") { answer = true }))
                    },
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { keyDown(Key.One) }
        assertFalse(answer)
    }
}