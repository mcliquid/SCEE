package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.quests.answerApplied
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.math.translate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tag-edit safety for Sidepath road picker answers (mirrors [OsmQuestFormContainer] behaviour). */
class SidepathQuestTagEditTest {

    private lateinit var questType: AddIsSidepath

    @BeforeTest
    fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddIsSidepath()
    }

    @Test fun `classification cant say on road step still writes is_sidepath yes`() {
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath", "yes")),
            questType.answerApplied(IsSidepathAnswer.Yes(ofName = null, ofHighway = null)),
        )
    }

    @Test fun `classification road selection writes is_sidepath yes and road metadata`() {
        val element = way(tags = mapOf("highway" to "cycleway"))
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = "Ring", ofHighway = "secondary"),
            element.tags,
        )
        assertTrue(changes.isNotEmpty())
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata not sidepath produces valid nonempty tag edit`() {
        val element = way(tags = mapOf("highway" to "cycleway", "is_sidepath" to "yes"))
        val changes = questType.answerAppliedTo(IsSidepathAnswer.No, element.tags)
        assertEquals(setOf(StringMapEntryModify("is_sidepath", "yes", "no")), changes)
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata not sidepath with association removes contradictory keys`() {
        val element = way(
            tags = mapOf(
                "highway" to "cycleway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "secondary",
                "is_sidepath:of:name" to "Example Street",
            ),
        )
        val changes = questType.answerAppliedTo(IsSidepathAnswer.No, element.tags)
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata named road selection produces valid tag edit`() {
        val element = way(tags = mapOf("highway" to "footway", "is_sidepath" to "yes"))
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes("Ring", "secondary"),
            element.tags,
        )
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `road selection with existing highway replaces association atomically`() {
        val element = way(
            tags = mapOf(
                "highway" to "cycleway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "primary",
            ),
        )
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes("Hauptstraße", "secondary"),
            element.tags,
        )
        assertTrue(changes.contains(StringMapEntryModify("is_sidepath:of", "primary", "secondary")))
        assertTrue(changes.contains(StringMapEntryAdd("is_sidepath:of:name", "Hauptstraße")))
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata unnamed road selection produces valid tag edit`() {
        val element = way(tags = mapOf("highway" to "cycleway", "is_sidepath" to "yes"))
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(null, "tertiary"),
            element.tags,
        )
        assertEquals(setOf(StringMapEntryAdd("is_sidepath:of", "tertiary")), changes)
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata road already matching is_sidepath of produces no tag edit`() {
        val element = way(
            tags = mapOf(
                "highway" to "cycleway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "residential",
            ),
        )
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(null, "residential"),
            element.tags,
        )
        assertTrue(changes.isEmpty())
        assertFalse(sidepathRoadMetadataAnswerProducesChanges(element.tags, null, "residential", null))
        assertTrue(sidepathAssociationTagsMatchSelection(element.tags, null, "residential", null))
    }

    @Test fun `metadata ref road selection produces valid tag edit`() {
        val element = way(tags = mapOf("highway" to "footway", "is_sidepath" to "yes"))
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(null, "primary", "B 33"),
            element.tags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("is_sidepath:of", "primary"),
                StringMapEntryAdd("is_sidepath:of:ref", "B 33"),
            ),
            changes,
        )
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `metadata named road fills missing name only`() {
        val element = way(
            tags = mapOf(
                "highway" to "footway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "residential",
            ),
        )
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes("Ring", "residential"),
            element.tags,
        )
        assertEquals(setOf(StringMapEntryAdd("is_sidepath:of:name", "Ring")), changes)
        UpdateElementTagsAction(element, StringMapChanges(changes))
    }

    @Test fun `ref on road is shown via getNameLabel but not written as is_sidepath of name`() {
        val roadStart = p(0.0, 0.0)
        val roadEnd = roadStart.translate(40.0, 0.0)
        val pathStart = roadStart.translate(5.0, 90.0)
        val pathEnd = pathStart.translate(40.0, 0.0)
        val road = way(1, listOf(1, 2), mapOf("highway" to "secondary", "ref" to "B 471"))
        val path = way(2, listOf(3, 4), mapOf("highway" to "cycleway", "is_sidepath" to "yes"))
        val mapData = createMapData(
            mapOf(
                road to ElementPolylinesGeometry(listOf(listOf(roadStart, roadEnd)), roadStart),
                path to ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart),
            ),
        )
        val candidate = findCandidateRoads(mapData.getWayGeometry(path.id)!!, mapData).single()
        assertEquals(null, candidate.name)
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(
                candidate.name,
                candidate.highway,
                candidate.ref,
            ),
            path.tags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("is_sidepath:of", "secondary"),
                StringMapEntryAdd("is_sidepath:of:ref", "B 471"),
            ),
            changes,
        )
        UpdateElementTagsAction(path, StringMapChanges(changes))
    }

    @Test fun `no nearby road yields empty candidates`() {
        val pathStart = p(0.0, 0.0)
        val pathEnd = pathStart.translate(40.0, 0.0)
        val path = way(1, listOf(1, 2), mapOf("highway" to "footway", "is_sidepath" to "yes"))
        val mapData = createMapData(
            mapOf(path to ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart)),
        )
        assertTrue(findCandidateRoads(mapData.getWayGeometry(path.id)!!, mapData).isEmpty())
        val changes = questType.answerAppliedTo(IsSidepathAnswer.No, path.tags)
        UpdateElementTagsAction(path, StringMapChanges(changes))
    }
}
