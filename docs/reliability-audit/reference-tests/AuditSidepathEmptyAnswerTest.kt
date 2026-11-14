package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** AUDIT regression tests: a FAILING test confirms the defect in its name. */
class AuditSidepathEmptyAnswerTest {
    private lateinit var questType: AddIsSidepath

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddIsSidepath()
    }

    /** Same steps as OsmQuestFormContainer.onAction for an Answer. */
    private fun submit(answer: IsSidepathAnswer, tags: Map<String, String>): UpdateElementTagsAction {
        val element = way(2, listOf(3, 4), tags)
        val builder = StringMapChangesBuilder(element.tags)
        questType.applyAnswerTo(answer, builder, ElementPointGeometry(p()), 0)
        return UpdateElementTagsAction(element, builder.create())
    }

    private val completionTags = mapOf(
        "highway" to "path", "foot" to "yes", "is_sidepath" to "yes", "is_sidepath:of" to "unclassified"
    )

    @Test fun `the UI allows re-selecting the road that is already tagged`() {
        assertTrue(sidepathAssociationTagsMatchSelection(completionTags, ofName = null, ofHighway = "unclassified", ofRef = null))
    }

    @Test fun `confirming the already tagged road does not throw when the edit is built`() {
        submit(IsSidepathAnswer.Yes(ofName = null, ofHighway = "unclassified", ofRef = null), completionTags)
    }
}
