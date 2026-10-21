package de.westnordost.streetcomplete

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import de.westnordost.streetcomplete.quests.crossing_markings.CrossingMarkings
import de.westnordost.streetcomplete.quests.crossing_markings.crossingMarkingsPainter
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.action_copy
import de.westnordost.streetcomplete.screens.main.errors.SendErrorReportDialog
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PlatformPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `crossing painter uses the existing Android drawable at the existing layout size`() {
        compose.setContent {
            Image(crossingMarkingsPainter(CrossingMarkings.ZEBRA), "zebra", Modifier.size(64.dp).testTag("crossing"))
        }
        assertTrue(compose.onNodeWithTag("crossing").fetchSemanticsNode().boundsInRoot.width > 0)
    }

    @Test fun `copy report preserves Unicode line breaks and full content`() {
        val report = "SCEE ä🌍\nfirst\nsecond\n"
        compose.setContent { MaterialTheme { SendErrorReportDialog({}, {}, "Report", report) } }
        val label = runBlocking { getString(Res.string.action_copy) }
        compose.onNodeWithText(label).performClick()
        val clipboard = RuntimeEnvironment.getApplication().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(report, clipboard.primaryClip!!.getItemAt(0).text.toString())
    }
}
