package de.westnordost.streetcomplete.screens.settings

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.screens.main.findNearbyQuests
import de.westnordost.streetcomplete.screens.main.NearbyQuests
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ShowNearbyQuestsSettingsTest {

    @Test fun `default mode is dont show nearby quests`() {
        val prefs = inMemoryPrefs()
        assertEquals(0, defaultShowNearbyQuestsMode(prefs))
    }

    @Test fun `apply persists each supported mode`() {
        val prefs = inMemoryPrefs()
        for (mode in 0..3) {
            applyShowNearbyQuestsSettings(prefs, mode, "3.5")
            assertEquals(mode, prefs.getInt(Prefs.SHOW_NEARBY_QUESTS, -1))
            assertEquals(3.5f, prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, -1f))
        }
    }

    @Test fun `apply clamps invalid distance`() {
        val prefs = inMemoryPrefs()
        applyShowNearbyQuestsSettings(prefs, 1, "not-a-number")
        assertEquals(0f, prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, -1f))
        applyShowNearbyQuestsSettings(prefs, 1, "42")
        assertEquals(10f, prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, -1f))
        applyShowNearbyQuestsSettings(prefs, 1, "-1")
        assertEquals(0f, prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, -1f))
    }

    @Test fun `reinitialised prefs read persisted mode`() {
        val prefs = inMemoryPrefs()
        applyShowNearbyQuestsSettings(prefs, 1, "2")
        val again = inMemoryPrefs().also {
            it.putInt(Prefs.SHOW_NEARBY_QUESTS, prefs.getInt(Prefs.SHOW_NEARBY_QUESTS, 0))
            it.putFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, prefs.getFloat(Prefs.SHOW_NEARBY_QUESTS_DISTANCE, 0f))
        }
        assertEquals(1, defaultShowNearbyQuestsMode(again))
    }

    @Test fun `stored mode 1 is used by findNearbyQuests`() {
        val prefs = inMemoryPrefs()
        applyShowNearbyQuestsSettings(prefs, 1, "0")
        val current = osmQuest(TestQuestTypeA(), ElementType.NODE, 1, pGeom(52.0, 13.0))
        var queried = false
        val result = findNearbyQuests(current, prefs) { _, _ -> queried = true; emptyList() }
        assertEquals(true, queried)
        assertEquals(NearbyQuests.EMPTY, result)
    }

    @Test fun `switching back to mode 0 disables nearby quest lookup`() {
        val prefs = inMemoryPrefs()
        applyShowNearbyQuestsSettings(prefs, 1, "0")
        applyShowNearbyQuestsSettings(prefs, 0, "0")
        val current = osmQuest(TestQuestTypeA(), ElementType.NODE, 1, pGeom(52.0, 13.0))
        var queried = false
        findNearbyQuests(current, prefs) { _, _ -> queried = true; emptyList() }
        assertEquals(false, queried)
    }

    @Test fun `expert options list excludes modes 2 and 3 when expert mode off`() {
        assertEquals(listOf(0, 1), nearbyQuestModeOptions(expertMode = false).map { it.first })
        assertEquals(listOf(0, 1, 2, 3), nearbyQuestModeOptions(expertMode = true).map { it.first })
    }

    @Test fun `legacy stored expert mode survives in prefs when not expert`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, 2)
        assertEquals(2, defaultShowNearbyQuestsMode(prefs))
        applyShowNearbyQuestsSettings(prefs, 0, "1")
        assertEquals(0, defaultShowNearbyQuestsMode(prefs))
    }

    @Test fun `expert mode 3 remains in prefs when expert ui is off`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, 3)
        assertEquals(listOf(0, 1), nearbyQuestModeOptions(expertMode = false).map { it.first })
        assertEquals(3, defaultShowNearbyQuestsMode(prefs))
    }

    @Test fun `regression gh35 distance only ok does not persist mode`() {
        val prefs = inMemoryPrefs()
        applyShowNearbyQuestsDistance(prefs, "4")
        assertEquals(0, defaultShowNearbyQuestsMode(prefs))
    }

    @Test fun `regression gh35 mode persist on radio selection matches legacy`() {
        val prefs = inMemoryPrefs()
        prefs.putInt(Prefs.SHOW_NEARBY_QUESTS, 1)
        assertEquals(1, defaultShowNearbyQuestsMode(prefs))
    }
}