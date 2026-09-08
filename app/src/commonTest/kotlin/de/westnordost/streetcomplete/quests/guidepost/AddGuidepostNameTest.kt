package de.westnordost.streetcomplete.quests.guidepost

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.answerApplied
import kotlin.test.Test
import kotlin.test.assertEquals

class AddGuidepostNameTest {

    private val questType = AddGuidepostName()

    @Test fun `apply no name answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("name:signed", "no")),
            questType.answerApplied(NoVisibleGuidepostName)
        )
    }

    @Test fun `apply name answer`() {
        assertEquals(
            setOf(StringMapEntryAdd("name", "Summit")),
            questType.answerApplied(GuidepostName("Summit"))
        )
    }
}
