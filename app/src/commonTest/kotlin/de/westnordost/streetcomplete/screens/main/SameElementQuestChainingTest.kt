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
import kotlin.test.assertNull
import kotlin.test.assertSame

class SameElementQuestChainingTest {

    private val surface = TestQuestTypeB()
    private val smoothness = TestQuestTypeC()
    private val sidewalk = TestQuestTypeD()
    private val geometry = pGeom(52.0, 13.0)
    private val element = node(id = 10, pos = p(52.0, 13.0))
    private val elementKey = ElementKey(ElementType.NODE, 10)

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

    @Test fun `multiple successors follow the configured quest order`() {
        val surfaceQuest = osmQuest(surface, elementId = 10, geometry = geometry)
        val smoothnessQuest = osmQuest(smoothness, elementId = 10, geometry = geometry)
        val sidewalkQuest = osmQuest(sidewalk, elementId = 10, geometry = geometry)
        // Candidate order is not the configured order (SpatialCache / HashSet).
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

    @Test fun `quest absent from the visible candidates cannot become the successor`() {
        val visible = osmQuest(surface, elementId = 10, geometry = geometry)

        // smoothness is the higher-priority type, but it is not among the visible candidates
        assertSame(
            visible,
            selectSameElementQuestSuccessor(
                listOf(visible),
                elementKey,
                listOf(smoothness, surface),
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

    @Test fun `no successor leaves the sheet closed`() {
        val otherElement = osmQuest(surface, elementId = 11, geometry = geometry)

        assertNull(
            immediateSameElementQuestSheet(
                listOf(otherElement),
                elementKey,
                listOf(surface),
            ) { _, _ -> element },
        )
    }

    @Test fun `preference off does not chain`() {
        assertNull(
            elementKeyForImmediateSameElementQuest(surface, tagUpdate(), showNextImmediately = false),
        )
    }

    @Test fun `ordinary tag update on an osm quest chains to that element`() {
        assertEquals(
            elementKey,
            elementKeyForImmediateSameElementQuest(surface, tagUpdate(), showNextImmediately = true),
        )
    }

    @Test fun `structural edit does not chain`() {
        assertNull(
            elementKeyForImmediateSameElementQuest(
                surface,
                MoveNodeAction(element, p(52.1, 13.1)),
                showNextImmediately = true,
            ),
        )
    }

    @Test fun `tag editor edit does not chain`() {
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
