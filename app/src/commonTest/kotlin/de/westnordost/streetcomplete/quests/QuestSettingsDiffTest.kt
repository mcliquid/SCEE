package de.westnordost.streetcomplete.quests

import de.westnordost.streetcomplete.util.html.*
import kotlin.test.*

class QuestSettingsDiffTest {
    @Test fun `identical empty and whitespace only changes stay hidden`() {
        for (text in listOf("", "a", "a\nb\na", "ä 漢字")) assertEquals("", questSettingsDiffHtml(text, text))
        assertEquals("", questSettingsDiffHtml("a b", "a  b"))
    }

    @Test fun `addition deletion and word replacement retain legacy markup`() {
        assertEquals("<b><u><ins>new</ins></u></b>", questSettingsDiffHtml("", "new"))
        assertEquals("<b><i><del>old</del></i></b>", questSettingsDiffHtml("old", ""))
        val html = questSettingsDiffHtml("nodes with name", "nodes with ref")
        assertTrue(html.contains("<del>name</del>"))
        assertTrue(html.contains("<ins>ref</ins>"))
        assertTrue(html.startsWith("nodes with "))
    }

    @Test fun `duplicates and order remain significant`() {
        assertNotEquals("", questSettingsDiffHtml("a\na\nb", "a\nb"))
        assertNotEquals("", questSettingsDiffHtml("a\nb", "b\na"))
        assertEquals("", questSettingsDiffHtml("a\na\nb", "a\na\nb"))
    }

    @Test fun `pipe word boundaries and unicode remain visible`() {
        val html = questSettingsDiffHtml("a|ä", "a|漢字")
        assertTrue(html.contains('|'))
        assertTrue(html.contains("ä"))
        assertTrue(html.contains("漢字"))
        assertTrue(html.contains("<del>"))
        assertTrue(html.contains("<ins>"))
    }

    @Test fun `user markup is escaped before renderer interprets inserted tags`() {
        val html = questSettingsDiffHtml("", "<script> & \"ä\" &lt;")
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains(" & "))
        assertFalse(html.contains("&amp;lt;"))
    }

    @Test fun `larger inputs preserve unchanged rows and repeat deterministically`() {
        val before = (0..2000).joinToString("\n") { "line $it" }
        val after = before.replace("line 1000", "line changed")
        val html = questSettingsDiffHtml(before, after)
        assertFalse(html.contains("line 999"))
        assertTrue(html.contains("1000"))
        assertTrue(html.contains("changed"))
        assertEquals(html, questSettingsDiffHtml(before, after))
    }

    @Test fun `existing HTML parser retains supported entities nesting and explicit line breaks`() {
        val nodes = parseHtml("<b>&amp;&lt;&gt;&quot;ä🌍</b><br><u><ins>new</ins></u>")
        assertEquals(listOf(HtmlTextNode("&<>\"ä🌍")), (nodes.first() as HtmlElementNode).nodes)
        assertEquals("br", (nodes[1] as HtmlElementNode).tag)
        assertEquals("u", (nodes[2] as HtmlElementNode).tag)
        assertEquals(listOf(HtmlTextNode("&lt;")), parseHtml("&amp;lt;"))
    }

    @Test fun `malformed HTML uses existing plain text fallback without executing markup`() {
        val malformed = "<b broken"
        assertEquals(listOf(HtmlTextNode(malformed)), tryParseHtml(malformed))
    }
}
