package de.westnordost.streetcomplete.quests.is_sidepath

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.updateElementTagsActionOrNull
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.math.translate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B3: an already-correct sidepath selection must not build an empty tag edit. */
class AddIsSidepathEmptyChangeTest {
    private lateinit var questType: AddIsSidepath

    @BeforeTest fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddIsSidepath()
    }

    private val alreadyTagged = mapOf(
        "highway" to "path",
        "foot" to "yes",
        "surface" to "asphalt",
        "is_sidepath" to "yes",
        "is_sidepath:of" to "unclassified",
    )

    @Test fun `the already tagged unnamed road still matches the selection`() {
        assertTrue(
            sidepathAssociationTagsMatchSelection(
                alreadyTagged,
                ofName = null,
                ofHighway = "unclassified",
                ofRef = null,
            )
        )
    }

    @Test fun `confirming the already tagged road does not build an edit`() {
        val element = way(2, listOf(3, 4), alreadyTagged)
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = null, ofHighway = "unclassified", ofRef = null),
            element.tags,
        )
        assertTrue(changes.isEmpty())
        assertNull(updateElementTagsActionOrNull(element, StringMapChanges(changes)))
        assertEquals(alreadyTagged, element.tags)
    }

    @Test fun `an empty change set is rejected by the tag action`() {
        val element = way(tags = alreadyTagged)
        assertFailsWith<IllegalArgumentException> {
            UpdateElementTagsAction(element, StringMapChanges(emptySet()))
        }
    }

    @Test fun `a road that still needs an association builds only that change`() {
        val tags = mapOf(
            "highway" to "path",
            "foot" to "yes",
            "surface" to "asphalt",
            "is_sidepath" to "yes",
        )
        val element = way(tags = tags)
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = null, ofHighway = "unclassified", ofRef = null),
            tags,
        )
        assertEquals(setOf(StringMapEntryAdd("is_sidepath:of", "unclassified")), changes)
        val action = updateElementTagsActionOrNull(element, StringMapChanges(changes))
        assertEquals(changes, action?.changes?.changes)
        assertEquals("asphalt", element.tags["surface"])
    }

    @Test fun `crossing on an unexpected highway is an empty answer and not an edit`() {
        val element = way(tags = mapOf("highway" to "track", "surface" to "gravel"))
        val changes = questType.answerAppliedTo(IsSidepathAnswer.IsCrossing, element.tags)
        assertTrue(changes.isEmpty())
        assertNull(updateElementTagsActionOrNull(element, StringMapChanges(changes)))
        assertEquals("gravel", element.tags["surface"])
    }

    @Test fun `not a sidepath still produces a tag edit`() {
        val element = way(tags = mapOf("highway" to "path", "foot" to "yes", "is_sidepath" to "yes"))
        val changes = questType.answerAppliedTo(IsSidepathAnswer.No, element.tags)
        assertTrue(changes.isNotEmpty())
        assertTrue(updateElementTagsActionOrNull(element, StringMapChanges(changes)) != null)
    }

    @Test fun `named road of another class does not block unnamed association closure`() {
        val path = sidepath(alreadyTagged)
        val namedOtherClass = road(1, mapOf("highway" to "residential", "name" to "Ring"))
        val mapData = createMapData(mapOf(path.first to path.second, namedOtherClass.first to namedOtherClass.second))
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    @Test fun `associating unnamed secondary closes when residential has a name`() {
        val pathStart = p(0.0, 0.0)
        val pathEnd = pathStart.translate(40.0, 0.0)
        val path = way(2, listOf(3, 4), mapOf("highway" to "cycleway"))
        val pathGeom = ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart)
        val secStart = pathStart.translate(5.0, 90.0)
        val secEnd = secStart.translate(40.0, 0.0)
        val secondary = way(1, listOf(10, 11), mapOf("highway" to "secondary"))
        val secGeom = ElementPolylinesGeometry(listOf(listOf(secStart, secEnd)), secStart)
        val resStart = pathStart.translate(6.0, 270.0)
        val resEnd = resStart.translate(40.0, 0.0)
        val residential = way(3, listOf(20, 21), mapOf("highway" to "residential", "name" to "Foo"))
        val resGeom = ElementPolylinesGeometry(listOf(listOf(resStart, resEnd)), resStart)
        val mapData = createMapData(
            mapOf(
                path to pathGeom,
                secondary to secGeom,
                residential to resGeom,
            ),
        )
        assertEquals(1, questType.getApplicableElements(mapData).count())
        val changes = questType.answerAppliedTo(
            IsSidepathAnswer.Yes(ofName = null, ofHighway = "secondary", ofRef = null),
            path.tags,
        )
        val updated = path.tags.toMutableMap()
        changes.filterIsInstance<StringMapEntryAdd>().forEach { updated[it.key] = it.value }
        val answeredPath = way(2, listOf(3, 4), updated)
        val afterAnswer = createMapData(mapOf(answeredPath to pathGeom, secondary to secGeom, residential to resGeom))
        assertEquals(0, questType.getApplicableElements(afterAnswer).count())
    }

    @Test fun `already complete unnamed association is not applicable without an edit`() {
        val path = sidepath(alreadyTagged)
        val unnamed = road(1, mapOf("highway" to "unclassified"))
        val mapData = createMapData(mapOf(path.first to path.second, unnamed.first to unnamed.second))
        assertEquals(0, questType.getApplicableElements(mapData).count())
    }

    private fun sidepath(tags: Map<String, String>): Pair<Way, ElementPolylinesGeometry> {
        val start = p(0.0, 0.0)
        val end = start.translate(40.0, 0.0)
        val element = way(2, listOf(3, 4), tags)
        return element to ElementPolylinesGeometry(listOf(listOf(start, end)), start)
    }

    private fun road(id: Long, tags: Map<String, String>): Pair<Way, ElementPolylinesGeometry> {
        val start = p(0.0, 0.0).translate(5.0, 90.0)
        val end = start.translate(40.0, 0.0)
        val element = way(id, listOf(id * 10, id * 10 + 1), tags)
        return element to ElementPolylinesGeometry(listOf(listOf(start, end)), start)
    }
}
