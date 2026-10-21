package de.westnordost.streetcomplete.quests

import android.app.Application
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class QuestSettingsHtmlTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `Android renderer preserves entities unicode line breaks and bold spans`() {
        compose.setContent { MaterialTheme { QuestSettingsHtml("<b>A &amp;&lt;&gt;&quot; ä🌍</b><br>B") } }
        val text = compose.onNodeWithText("A &<>\" ä🌍\nB").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        assertEquals("A &<>\" ä🌍\nB", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
    }

    @Test fun `generated diff displays user tags literally and decodes existing entities once`() {
        val diff = questSettingsDiffHtml("", "<script> &lt;")
        compose.setContent { MaterialTheme { QuestSettingsHtml(diff) } }
        val text = compose.onNodeWithText("<script> <").fetchSemanticsNode()
            .config[SemanticsProperties.Text].single()
        assertEquals("<script> <", text.text)
    }
}
