package de.westnordost.streetcomplete.screens.main.bottom_sheet

import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuestSaveInFlightRegistryTest {

    private val keyA = OsmQuestKey(ElementType.WAY, 1, "AddPlaceName")
    private val keyB = OsmQuestKey(ElementType.WAY, 2, "AddPlaceName")

    @Test fun `duplicate claim for same key is rejected`() {
        val registry = QuestSaveInFlightRegistry()
        assertTrue(registry.tryClaim(keyA))
        assertFalse(registry.tryClaim(keyA))
        registry.release(keyA)
        assertTrue(registry.tryClaim(keyA))
    }

    @Test fun `independent keys do not block each other`() {
        val registry = QuestSaveInFlightRegistry()
        assertTrue(registry.tryClaim(keyA))
        assertTrue(registry.tryClaim(keyB))
        assertTrue(registry.isInFlight(keyA))
        assertTrue(registry.isInFlight(keyB))
        registry.release(keyA)
        assertFalse(registry.isInFlight(keyA))
        assertTrue(registry.isInFlight(keyB))
    }
}