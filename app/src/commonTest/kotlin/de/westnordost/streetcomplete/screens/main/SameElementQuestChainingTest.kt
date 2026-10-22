package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.edits.move.MoveNodeAction
import de.westnordost.streetcomplete.data.osm.edits.tagEdit
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.TestQuestTypeA
import de.westnordost.streetcomplete.data.quest.TestQuestTypeB
import de.westnordost.streetcomplete.data.quest.TestQuestTypeC
import de.westnordost.streetcomplete.data.quest.TestQuestTypeD
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.pGeom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SameElementQuestChainingTest {

    private val surface = TestQuestTypeB()
    private val smoothness = TestQuestTypeC()
    private val sidewalk = TestQuestTypeD()
    private val geometry = pGeom(52.0, 13.0)
    private val element = node(id = 10, pos = p(52.0, 13.0))
    private val elementKey = ElementKey(ElementType.NODE, 10)
    private val otherElementKey = ElementKey(ElementType.NODE, 99)

    @Test fun `preference on with successor auto-opens next quest`() {
        assertTrue(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = true,
                showNextImmediately = true,
                savedSuccessfully = true,
            ),
        )
        assertEquals(
            elementKey,
            elementKeyForImmediateSameElementQuest(surface, tagUpdate(), showNextImmediately = true),
        )
    }

    @Test fun `preference off keeps previous no-chaining behaviour`() {
        assertFalse(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = true,
                showNextImmediately = false,
                savedSuccessfully = true,
            ),
        )
        assertNull(
            elementKeyForImmediateSameElementQuest(surface, tagUpdate(), showNextImmediately = false),
        )
    }

    @Test fun `no successor returns to map`() {
        assertFalse(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = false,
                showNextImmediately = true,
                savedSuccessfully = true,
            ),
        )
        assertNull(
            immediateSameElementQuestSheet(
                listOf(osmQuest(surface, elementId = 11, geometry = geometry)),
                elementKey,
                listOf(surface),
            ) { _, _ -> element },
        )
    }

    @Test fun `pending chained open is cancelled on user dismiss`() {
        val pending = PendingChainedQuestOpen()
        val saveId = pending.beginSave()
        assertTrue(pending.shouldOpenSuccessor(saveId))
        pending.cancel()
        assertFalse(pending.shouldOpenSuccessor(saveId))
    }

    @Test fun `manual sheet open cancels pending chained open`() {
        val pending = PendingChainedQuestOpen()
        val saveId = pending.beginSave()
        pending.cancel()
        assertFalse(pending.shouldOpenSuccessor(saveId))
    }

    @Test fun `successful save keeps pending open until complete`() {
        val pending = PendingChainedQuestOpen()
        val saveId = pending.beginSave()
        assertTrue(pending.shouldOpenSuccessor(saveId))
        pending.complete(saveId)
        assertFalse(pending.shouldOpenSuccessor(saveId))
    }

    @Test fun `closing a quest without successful save does not chain`() {
        // Dismiss/close never calls submitEdit; even if callers evaluated the gate, no open.
        assertFalse(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = true,
                showNextImmediately = true,
                savedSuccessfully = false,
            ),
        )
    }

    @Test fun `F1 auto-submit yields exactly one transition decision per successful answer`() {
        val smoothnessQuest = osmQuest(smoothness, elementId = 10, geometry = geometry)
        val successors = listOf(smoothnessQuest)
        // One answer → one successor selection (not duplicated for the same candidate set).
        val first = selectSameElementQuestSuccessor(successors, elementKey, listOf(surface, smoothness))
        val second = selectSameElementQuestSuccessor(successors, elementKey, listOf(surface, smoothness))
        assertSame(smoothnessQuest, first)
        assertSame(first, second)
        assertTrue(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = first != null,
                showNextImmediately = true,
                savedSuccessfully = true,
            ),
        )
    }

    @Test fun `failed submit does not open a successor`() {
        assertFalse(
            shouldAutoOpenSameElementQuestSuccessor(
                elementKey,
                hasSuccessor = true,
                showNextImmediately = true,
                savedSuccessfully = false,
            ),
        )
    }

    @Test fun `two or more open quests on the same way pick the configured successor`() {
        val surfaceQuest = osmQuest(surface, elementId = 10, geometry = geometry)
        val smoothnessQuest = osmQuest(smoothness, elementId = 10, geometry = geometry)
        val sidewalkQuest = osmQuest(sidewalk, elementId = 10, geometry = geometry)
        val unordered = listOf(sidewalkQuest, surfaceQuest, smoothnessQuest)
        val configured = questTypesInChainingOrder(
            listOf(sidewalk, surface, smoothness),
            reverseOrder(),
        )

        assertEquals(listOf(smoothness, surface, sidewalk), configured)
        assertSame(
            smoothnessQuest,
            selectSameElementQuestSuccessor(unordered, elementKey, configured),
        )
    }

    @Test fun `answer can make another quest newly applicable via updated candidates`() {
        val smoothnessQuest = osmQuest(smoothness, elementId = 10, geometry = geometry)
        assertSame(
            smoothnessQuest,
            selectSameElementQuestSuccessor(
                listOf(smoothnessQuest),
                elementKey,
                listOf(surface, smoothness),
            ),
        )
    }

    @Test fun `answer can remove a no longer applicable quest from candidates`() {
        assertNull(
            selectSameElementQuestSuccessor(
                emptyList(),
                elementKey,
                listOf(smoothness, surface),
            ),
        )
    }

    @Test fun `disabled hidden or invisible quests absent from candidates are not offered`() {
        val visible = osmQuest(surface, elementId = 10, geometry = geometry)

        assertSame(
            visible,
            selectSameElementQuestSuccessor(
                listOf(visible),
                elementKey,
                listOf(smoothness, surface),
            ),
        )
        assertNull(
            selectSameElementQuestSuccessor(
                emptyList(),
                elementKey,
                listOf(smoothness),
            ),
        )
    }

    @Test fun `colored dot quests are not chained`() {
        val dot = DotQuestType()
        val dotQuest = osmQuest(dot, elementId = 10, geometry = geometry)
        val normal = osmQuest(surface, elementId = 10, geometry = geometry)

        assertSame(
            normal,
            selectSameElementQuestSuccessor(listOf(dotQuest, normal), elementKey, listOf(dot, surface)),
        )
        assertNull(
            selectSameElementQuestSuccessor(listOf(dotQuest), elementKey, listOf(dot)),
        )
    }

    @Test fun `undo and structural edits are not chainable`() {
        assertNull(elementKeyForSameElementQuestChain(surface, MoveNodeAction(element, p(52.1, 13.1))))
        assertNull(
            elementKeyForImmediateSameElementQuest(
                surface,
                MoveNodeAction(element, p(52.1, 13.1)),
                showNextImmediately = true,
            ),
        )
    }

    @Test fun `switching OSM object does not offer the previous element's successor`() {
        val onOther = osmQuest(smoothness, elementId = 99, geometry = geometry)
        assertNull(
            selectSameElementQuestSuccessor(
                listOf(onOther),
                elementKey,
                listOf(smoothness),
            ),
        )
        assertSame(
            onOther,
            selectSameElementQuestSuccessor(
                listOf(onOther),
                otherElementKey,
                listOf(smoothness),
            ),
        )
    }

    @Test fun `successor must be the same element`() {
        val onElement = osmQuest(surface, elementId = 10, geometry = geometry)
        val elsewhere = osmQuest(smoothness, elementId = 99, geometry = pGeom(53.0, 14.0))

        assertSame(
            onElement,
            selectSameElementQuestSuccessor(
                listOf(elsewhere, onElement),
                elementKey,
                listOf(smoothness, surface),
            ),
        )
    }

    @Test fun `identical geometry on another element does not match`() {
        val onElement = osmQuest(surface, elementId = 10, geometry = geometry)
        val sameGeometryOtherElement = osmQuest(smoothness, elementId = 11, geometry = geometry)

        assertSame(
            onElement,
            selectSameElementQuestSuccessor(
                listOf(sameGeometryOtherElement, onElement),
                elementKey,
                listOf(smoothness, surface),
            ),
        )
    }

    @Test fun `same element matches even when geometry objects differ`() {
        val movedGeometry = osmQuest(surface, elementId = 10, geometry = pGeom(52.1, 13.1))

        assertSame(
            movedGeometry,
            selectSameElementQuestSuccessor(listOf(movedGeometry), elementKey, listOf(surface)),
        )
    }

    @Test fun `types missing from configured order are selected deterministically by name`() {
        val surfaceQuest = osmQuest(surface, elementId = 10, geometry = geometry)
        val smoothnessQuest = osmQuest(smoothness, elementId = 10, geometry = geometry)
        val candidates = listOf(surfaceQuest, smoothnessQuest)
        val expected = candidates.minBy { it.type.name }

        assertSame(
            expected,
            selectSameElementQuestSuccessor(candidates.reversed(), elementKey, emptyList()),
        )
    }

    @Test fun `missing element does not open a successor`() {
        val quest = osmQuest(surface, elementId = 10, geometry = geometry)

        assertNull(
            immediateSameElementQuestSheet(
                listOf(quest),
                elementKey,
                listOf(surface),
            ) { _, _ -> null },
        )
    }

    @Test fun `loaded element opens that quest`() {
        val quest = osmQuest(surface, elementId = 10, geometry = geometry)

        val sheet = immediateSameElementQuestSheet(
            listOf(quest),
            elementKey,
            listOf(surface),
        ) { type, id ->
            assertEquals(ElementType.NODE, type)
            assertEquals(10L, id)
            element
        }

        assertEquals(ShownBottomSheet.OsmQuest(quest, element), sheet)
    }

    @Test fun `ordinary tag update on an osm quest is chainable for that element`() {
        assertEquals(elementKey, elementKeyForSameElementQuestChain(surface, tagUpdate()))
        assertEquals(
            elementKey,
            elementKeyForImmediateSameElementQuest(surface, tagUpdate(), showNextImmediately = true),
        )
    }

    @Test fun `tag editor edit does not chain`() {
        assertNull(elementKeyForSameElementQuestChain(tagEdit, tagUpdate()))
        assertNull(
            elementKeyForImmediateSameElementQuest(tagEdit, tagUpdate(), showNextImmediately = true),
        )
    }

    private fun tagUpdate() = UpdateElementTagsAction(
        element,
        StringMapChanges(listOf(StringMapEntryAdd("surface", "asphalt"))),
    )
}

private class DotQuestType : TestQuestTypeA() {
    override val dotColor = "#ff0000"
}

private fun reverseOrder() = object : QuestTypeOrderSource {
    override fun sort(questTypes: MutableList<QuestType>, presetId: Long?) {
        questTypes.reverse()
    }

    override fun getOrders(presetId: Long?): List<Pair<QuestType, QuestType>> = emptyList()
    override fun addListener(listener: QuestTypeOrderSource.Listener) {}
    override fun removeListener(listener: QuestTypeOrderSource.Listener) {}
}
