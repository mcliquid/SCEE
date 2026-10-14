package de.westnordost.streetcomplete.screens.main.map.sources

import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.testutils.QUEST_TYPE
import de.westnordost.streetcomplete.screens.main.map.layers.Pin
import de.westnordost.streetcomplete.testutils.bbox
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.p
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapQuestPinsSourceTest {
    private val viewBbox = bbox(0.0, 0.0, 1.0, 1.0)
    private val keyInside: QuestKey = OsmQuestKey(ElementType.NODE, 1, QUEST_TYPE.name, "")
    private val keyOutside: QuestKey = OsmQuestKey(ElementType.NODE, 2, QUEST_TYPE.name, "")

    @Test fun `pinsByQuest unchanged when reload stops before merge`() {
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            keyInside to listOf(pinAt(p(0.5, 0.5))),
            keyOutside to listOf(pinAt(p(2.0, 2.0))),
        )
        val snapshot = pinsByQuest.toMap()
        assertEquals(snapshot, pinsByQuest)
    }

    @Test fun `mergeQuestPinReload removes single-pin quests and applies bbox results`() {
        val inside = p(0.5, 0.5)
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            keyInside to listOf(pinAt(inside)),
            keyOutside to listOf(pinAt(p(2.0, 2.0))),
        )
        val refreshed = pinAt(p(0.6, 0.6))
        val quest = osmQuest(elementType = ElementType.NODE, elementId = 1, geometry = ElementPointGeometry(inside))
        mergeQuestPinReload(pinsByQuest, viewBbox, listOf(quest)) { listOf(refreshed) }
        assertEquals(1, pinsByQuest.size)
        assertEquals(listOf(refreshed), pinsByQuest[keyInside])
    }

    @Test fun `empty getAll after filter or viewport change removes visible pins`() {
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            keyInside to listOf(pinAt(p(0.5, 0.5))),
        )
        mergeQuestPinReload(pinsByQuest, viewBbox, emptyList()) { emptyList() }
        assertTrue(pinsByQuest.isEmpty())
    }

    @Test fun `reload drops a deleted quest and keeps the remaining one`() {
        val kept = osmQuest(elementType = ElementType.NODE, elementId = 1, geometry = ElementPointGeometry(p(0.5, 0.5)))
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            keyInside to listOf(pinAt(p(0.5, 0.5))),
            keyOutside to listOf(pinAt(p(0.2, 0.2))),
        )
        mergeQuestPinReload(pinsByQuest, viewBbox, listOf(kept)) { listOf(pinAt(it.position)) }
        assertEquals(setOf(keyInside), pinsByQuest.keys)
    }

    @Test fun `questPinKeysToEvictOnReload keeps multi-pin quest when a pin remains in bbox`() {
        val multiKey: QuestKey = OsmQuestKey(ElementType.WAY, 10, QUEST_TYPE.name, "")
        val pinsByQuest: Map<QuestKey, List<Pin>> = mapOf(
            multiKey to listOf(pinAt(p(0.1, 0.5)), pinAt(p(0.9, 0.5))),
        )
        assertTrue(questPinKeysToEvictOnReload(pinsByQuest, viewBbox).isEmpty())
    }

    private fun pinAt(position: LatLon) = Pin(position, QUEST_TYPE.icon, emptyList(), 0, null, null)
}
