package de.westnordost.streetcomplete.screens.settings.quest_selection

import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.quest.TestQuestTypeDisabled
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Stand-in whose [name] is "OsmoseQuest", matching production exclusion by class simple name. */
private class OsmoseQuest : TestQuestTypeA()

class SelectAllQuestVisibilitiesTest {

    @Test fun `select all enables regular and default-disabled quests but not Osmose`() {
        val regular = TestQuestTypeA()
        val defaultDisabled = TestQuestTypeDisabled()
        val osmose = OsmoseQuest()

        val result = selectAllQuestVisibilities(listOf(regular, defaultDisabled, osmose))

        assertEquals(2, result.size)
        assertTrue(result.getValue(regular))
        assertTrue(result.getValue(defaultDisabled))
        assertFalse(result.containsKey(osmose))
    }

    @Test fun `select all with only Osmose yields empty map`() {
        assertTrue(selectAllQuestVisibilities(listOf(OsmoseQuest())).isEmpty())
    }
}
