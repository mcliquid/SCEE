package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
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

/** Regression tests from EE+201 consolidation closure audit. */
class SidepathQuestClosureAuditTest {

    private lateinit var questType: AddIsSidepath

    @BeforeTest
    fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddIsSidepath()
    }

    @Test fun `unnamed unclassified association complete does not stay applicable`() {
        val pathStart = p(0.0, 0.0)
        val pathEnd = pathStart.translate(40.0, 0.0)
        val roadStart = pathStart.translate(5.0, 90.0)
        val roadEnd = roadStart.translate(40.0, 0.0)
        val road = way(1, listOf(1, 2), mapOf("highway" to "unclassified"))
        val path = way(
            2,
            listOf(3, 4),
            mapOf(
                "highway" to "path",
                "foot" to "yes",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "unclassified",
            ),
        )
        val mapData = createMapData(
            mapOf(
                road to ElementPolylinesGeometry(listOf(listOf(roadStart, roadEnd)), roadStart),
                path to ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart),
            ),
        )
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `road switch replaces stale name and ref atomically`() {
        val original = mapOf(
            "highway" to "footway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "primary",
            "is_sidepath:of:name" to "Altstraße",
            "is_sidepath:of:ref" to "B 1",
        )
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = "Neue Straße", ofHighway = "secondary", ofRef = "L 201b"),
            original,
        )
        assertTrue(changes.contains(StringMapEntryModify("is_sidepath:of", "primary", "secondary")))
        assertTrue(changes.contains(StringMapEntryModify("is_sidepath:of:name", "Altstraße", "Neue Straße")))
        assertTrue(changes.contains(StringMapEntryModify("is_sidepath:of:ref", "B 1", "L 201b")))
    }

    @Test fun `road switch to unnamed road clears name and ref`() {
        val original = mapOf(
            "highway" to "cycleway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "residential",
            "is_sidepath:of:name" to "Ring",
            "is_sidepath:of:ref" to "B 33",
        )
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = null, ofHighway = "unclassified", ofRef = null),
            original,
        )
        assertEquals(
            setOf("is_sidepath:of:name", "is_sidepath:of:ref"),
            changes.filterIsInstance<StringMapEntryDelete>().map { it.key }.toSet(),
        )
        assertTrue(changes.contains(StringMapEntryModify("is_sidepath:of", "residential", "unclassified")))
    }

    @Test fun `matching selection is submittable even when fill-only would not change tags`() {
        assertTrue(
            sidepathAssociationTagsMatchSelection(
                mapOf("is_sidepath:of" to "unclassified"),
                ofName = null,
                ofHighway = "unclassified",
                ofRef = null,
            ),
        )
        assertFalse(
            sidepathRoadMetadataAnswerProducesChanges(
                mapOf("is_sidepath:of" to "unclassified"),
                ofName = null,
                ofHighway = "unclassified",
                ofRef = null,
            ),
        )
    }

    @Test fun `after ref only association quest disappears`() {
        val original = mapOf("highway" to "footway")
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = null, ofHighway = "secondary", ofRef = "L 201b"),
            original,
        )
        val updated = original.toMutableMap()
        changes.filterIsInstance<StringMapEntryAdd>().forEach { updated[it.key] = it.value }
        changes.filterIsInstance<StringMapEntryModify>().forEach { updated[it.key] = it.value }
        assertEquals(false, questType.isApplicableTo(way(tags = updated)))
    }

    @Test fun `after named association quest disappears`() {
        val original = mapOf("highway" to "footway")
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = "Hauptstraße", ofHighway = "residential"),
            original,
        )
        val updated = original.toMutableMap()
        changes.filterIsInstance<StringMapEntryAdd>().forEach { updated[it.key] = it.value }
        assertEquals(false, questType.isApplicableTo(way(tags = updated)))
    }

    @Test fun `dedupe keeps distinct names on same ref`() {
        val pathStart = p(0.0, 0.0)
        val pathEnd = pathStart.translate(40.0, 0.0)
        val path = way(1, listOf(1, 2), mapOf("highway" to "footway"))
        val aStart = pathStart.translate(5.0, 90.0)
        val aEnd = aStart.translate(40.0, 0.0)
        val bStart = pathStart.translate(6.0, 270.0)
        val bEnd = bStart.translate(40.0, 0.0)
        val a = way(2, listOf(3, 4), mapOf("highway" to "secondary", "ref" to "L 201b", "name" to "Nord"))
        val b = way(3, listOf(5, 6), mapOf("highway" to "secondary", "ref" to "L 201b", "name" to "Süd"))
        val mapData = createMapData(
            mapOf(
                path to ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart),
                a to ElementPolylinesGeometry(listOf(listOf(aStart, aEnd)), aStart),
                b to ElementPolylinesGeometry(listOf(listOf(bStart, bEnd)), bStart),
            ),
        )
        val candidates = findCandidateRoads(mapData.getWayGeometry(path.id)!!, mapData)
        assertEquals(2, candidates.size)
        assertEquals(setOf("Nord", "Süd"), candidates.mapNotNull { it.name }.toSet())
    }
}
