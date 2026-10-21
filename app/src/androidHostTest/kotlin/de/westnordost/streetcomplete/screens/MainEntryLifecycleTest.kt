package de.westnordost.streetcomplete.screens

import android.app.Application
import androidx.compose.material.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * MainNavHost shows its entries with NavDisplay (single pane, same decorators). The main screen
 * relies on this to read the GPX track and the custom geometry again when coming back from the
 * settings: its content has to leave the composition while another destination is shown.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS")
class MainEntryLifecycleTest {
    @get:Rule val compose = createComposeRule()

    private data object Main
    private data object Settings

    @Test fun `the bottom entry is composed again after returning from another destination`() {
        var launched = 0
        var disposed = 0
        var remembered = 0
        val backStack = mutableStateListOf<Any>(Main)
        compose.setContent {
            NavDisplay(
                backStack = backStack,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<Main> {
                        remember { remembered++ }
                        LaunchedEffect(Unit) { launched++ }
                        DisposableEffect(Unit) { onDispose { disposed++ } }
                        Text("main")
                    }
                    entry<Settings> { Text("settings") }
                },
            )
        }
        compose.waitForIdle()
        assertEquals(1, launched)

        backStack.add(Settings)
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertEquals(1, disposed, "main content leaves the composition while the settings are shown")

        backStack.removeLast()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        assertEquals(2, launched, "the main screen's effects run again on return")
        assertEquals(2, remembered, "plain remember state starts empty again, so it has to be loaded again")
    }
}
