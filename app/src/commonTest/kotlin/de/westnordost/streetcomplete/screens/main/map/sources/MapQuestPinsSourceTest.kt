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

    @Test fun `multi-pin quest inside the bbox is removed when the reload omits it`() {
        val multiKey: QuestKey = OsmQuestKey(ElementType.WAY, 10, QUEST_TYPE.name, "")
        val other = OsmQuestKey(ElementType.NODE, 3, QUEST_TYPE.name, "")
        val keptPin = pinAt(p(0.2, 0.2))
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            multiKey to listOf(pinAt(p(0.1, 0.5)), pinAt(p(0.9, 0.5))),
            other to listOf(keptPin),
        )
        val kept = osmQuest(elementType = ElementType.NODE, elementId = 3, geometry = ElementPointGeometry(p(0.2, 0.2)))
        mergeQuestPinReload(pinsByQuest, viewBbox, listOf(kept)) { listOf(keptPin) }
        assertEquals(setOf<QuestKey>(other), pinsByQuest.keys)
        assertEquals(listOf(keptPin), pinsByQuest[other])
    }

    @Test fun `empty reload drops a multi-pin quest whose pins are all in view`() {
        val multiKey: QuestKey = OsmQuestKey(ElementType.WAY, 11, QUEST_TYPE.name, "")
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(
            multiKey to listOf(pinAt(p(0.1, 0.5)), pinAt(p(0.9, 0.5))),
        )
        mergeQuestPinReload(pinsByQuest, viewBbox, emptyList()) { emptyList() }
        assertTrue(pinsByQuest.isEmpty())
    }

    @Test fun `multi-pin quest crossing the bbox is kept when getAll omits it`() {
        val multiKey: QuestKey = OsmQuestKey(ElementType.WAY, 12, QUEST_TYPE.name, "")
        val pins = listOf(pinAt(p(0.5, 0.5)), pinAt(p(2.0, 2.0)))
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(multiKey to pins)
        mergeQuestPinReload(pinsByQuest, viewBbox, emptyList()) { emptyList() }
        assertEquals(pins, pinsByQuest[multiKey])
    }

    @Test fun `a returning quest replaces its pins without duplicating them`() {
        val multiKey: QuestKey = OsmQuestKey(ElementType.WAY, 13, QUEST_TYPE.name, "")
        val quest = osmQuest(elementType = ElementType.WAY, elementId = 13, geometry = ElementPointGeometry(p(0.4, 0.4)))
        val first = listOf(pinAt(p(0.4, 0.4)), pinAt(p(0.6, 0.6)))
        val second = listOf(pinAt(p(0.3, 0.3)))
        val pinsByQuest: MutableMap<QuestKey, List<Pin>> = mutableMapOf(multiKey to first)
        mergeQuestPinReload(pinsByQuest, viewBbox, listOf(quest)) { first }
        mergeQuestPinReload(pinsByQuest, viewBbox, listOf(quest)) { second }
        assertEquals(second, pinsByQuest[quest.key])
        assertEquals(1, pinsByQuest.size)
    }

    private fun pinAt(position: LatLon) = Pin(position, QUEST_TYPE.icon, emptyList(), 0, null, null)
}
