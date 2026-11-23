package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.AllEditTypes
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceDao
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.osmquests.TestQuestType
import de.westnordost.streetcomplete.data.overlays.OverlayRegistry
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.pGeom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ElementEditAttentionTest : StreetCompleteDatabaseTestCase() {
    private val questType = TestQuestType()
    private lateinit var controller: ElementEditsControllerImpl

    override fun onDatabaseInitialized(database: Database) {
        val types = AllEditTypes(listOf(QuestTypeRegistry({ listOf(1 to questType) }), OverlayRegistry(emptyList())))
        controller = ElementEditsControllerImpl(
            ElementEditsDao(database, types, questType, questType),
            EditElementsDao(database),
            ElementIdProviderDao(database),
            database,
            ExternalSourceDao(database),
        )
    }

    @Test fun `dependent temp reference is waiting not user attention`() {
        controller.add(questType, pGeom(), "survey", CreateNodeAction(p(1.0, 2.0), mapOf("amenity" to "bench")), true, null)
        val root = controller.getAllUnsynced().single()
        val created = controller.getIdProvider(root.id).getAll().single()
        controller.add(questType, pGeom(), "survey",
            UpdateElementTagsAction(Node(created.id, p(), emptyMap(), 1),
                StringMapChanges(setOf(StringMapEntryAdd("name", "test")))), true, null)
        val child = controller.getAllUnsynced().last()
        assertFalse(controller.editNeedsUserAttention(child))
        assertTrue(child.action.elementKeys.any { it.id < 0 })
    }

    @Test fun `unowned temp reference needs user attention`() {
        controller.add(questType, pGeom(), "survey",
            UpdateElementTagsAction(Node(-999, p(), emptyMap(), 1),
                StringMapChanges(setOf(StringMapEntryAdd("name", "test")))), true, null)
        val edit = controller.getAllUnsynced().single()
        assertTrue(controller.editNeedsUserAttention(edit))
    }

    @Test fun `unsynced attention list empty for waiting chain only`() {
        controller.add(questType, pGeom(), "survey", CreateNodeAction(p(1.0, 2.0), mapOf("amenity" to "bench")), true, null)
        val created = controller.getIdProvider(controller.getAllUnsynced().single().id).getAll().single()
        controller.add(questType, pGeom(), "survey",
            UpdateElementTagsAction(Node(created.id, p(), emptyMap(), 1),
                StringMapChanges(setOf(StringMapEntryAdd("name", "test")))), true, null)
        assertEquals(emptyList(), controller.unsyncedEditIdsNeedingUserAttention())
    }
}