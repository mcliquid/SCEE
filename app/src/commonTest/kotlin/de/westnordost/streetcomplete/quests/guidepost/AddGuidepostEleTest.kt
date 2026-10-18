package de.westnordost.streetcomplete.quests.guidepost

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddGuidepostEleTest {

    private lateinit var questType: AddGuidepostEle
    private val guidepost = mapOf("information" to "guidepost", "hiking" to "yes")

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddGuidepostEle()
    }

    @Test fun `applicable to guidepost without elevation`() {
        assertTrue(questType.isApplicableTo(node(tags = guidepost)))
    }

    @Test fun `not applicable to guidepost with elevation`() {
        assertFalse(questType.isApplicableTo(node(tags = guidepost + ("ele" to "500"))))
    }

    @Test fun `not applicable to guidepost with no signed elevation`() {
        assertFalse(questType.isApplicableTo(node(tags = guidepost + ("ele:signed" to "no"))))
    }

    @Test fun `apply elevation answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("ele", "500")),
            questType.answerApplied(GuidepostEle("500"))
        )
    }

    @Test fun `apply no signed elevation answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("ele:signed", "no")),
            questType.answerApplied(NoSignedGuidepostEle)
        )
    }

    @Test fun `no signed elevation answer keeps other tags`() {
        assertEquals(
            setOf(StringMapEntryAdd("ele:signed", "no")),
            questType.answerAppliedTo(NoSignedGuidepostEle, guidepost + ("name" to "Summit"))
        )
    }
}
