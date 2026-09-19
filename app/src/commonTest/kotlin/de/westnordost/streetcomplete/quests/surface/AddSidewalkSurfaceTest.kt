package de.westnordost.streetcomplete.quests.surface

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddSidewalkSurfaceTest {
    private lateinit var questType: AddSidewalkSurface
    private var previousPreferences: Preferences? = null

    @BeforeTest fun setUp() {
        // isApplicableTo reads Prefs.preferences via OsmFilterQuestType.filter
        previousPreferences = runCatching { Prefs.preferences }.getOrNull()
        Prefs.preferences = inMemoryPrefs()
        questType = AddSidewalkSurface()
    }

    @AfterTest fun tearDown() {
        previousPreferences?.let { Prefs.preferences = it }
    }

    @Test fun `not applicable to road with separate sidewalks`() {
        assertIsNotApplicable("sidewalk" to "separate")
    }

    @Test fun `not applicable to road with no sidewalks`() {
        assertIsNotApplicable("sidewalk" to "no")
    }

    @Test fun `applicable to road with sidewalk on both sides`() {
        assertIsApplicable("highway" to "residential", "sidewalk" to "both")
    }

    @Test fun `applicable to road with sidewalk on only one side`() {
        assertIsApplicable("highway" to "residential", "sidewalk" to "left")
        assertIsApplicable("highway" to "residential", "sidewalk" to "right")
    }

    @Test fun `applicable to road with sidewalk on one side and separate sidewalk on the other`() {
        assertIsApplicable("highway" to "residential", "sidewalk:left" to "yes", "sidewalk:right" to "separate")
        assertIsApplicable("highway" to "residential", "sidewalk:left" to "separate", "sidewalk:right" to "yes")
    }

    @Test fun `applicable to road with sidewalk on one side and no sidewalk on the other`() {
        assertIsApplicable("highway" to "residential", "sidewalk:left" to "yes", "sidewalk:right" to "no")
        assertIsApplicable("highway" to "residential", "sidewalk:left" to "no", "sidewalk:right" to "yes")
    }

    @Test fun `does not extra-highlight the road for tag-mapped sidewalks`() {
        assertHighlightedIds(
            sidewalkRoad(id = 1, "sidewalk" to "both"),
            expectedIds = emptySet()
        )
        assertHighlightedIds(
            sidewalkRoad(id = 1, "sidewalk" to "left"),
            expectedIds = emptySet()
        )
        assertHighlightedIds(
            sidewalkRoad(id = 1, "sidewalk" to "right"),
            expectedIds = emptySet()
        )
    }

    @Test fun `does not highlight unrelated exclusive cycleways`() {
        val road = sidewalkRoad(id = 1, "sidewalk" to "both")
        val cycleway = way(2, listOf(4, 5), mapOf("highway" to "cycleway"))
        assertHighlightedIds(road, cycleway, expectedIds = emptySet())
    }

    @Test fun `highlights separately mapped sidewalks and footways`() {
        val road = sidewalkRoad(id = 1, "sidewalk" to "both")
        val sidewalk = way(2, listOf(4, 5), mapOf("highway" to "footway", "footway" to "sidewalk"))
        val footway = way(3, listOf(6, 7), mapOf("highway" to "footway"))
        val path = way(4, listOf(8, 9), mapOf("highway" to "path"))
        val steps = way(5, listOf(10, 11), mapOf("highway" to "steps"))
        assertHighlightedIds(road, sidewalk, footway, path, steps, expectedIds = setOf(2, 3, 4, 5))
    }

    @Test fun `highlights pedestrian ways for one-sided sidewalks`() {
        val leftOnly = sidewalkRoad(id = 1, "sidewalk" to "left")
        val rightOnly = sidewalkRoad(id = 2, "sidewalk" to "right")
        val mixed = way(3, listOf(1, 2, 3), mapOf(
            "highway" to "residential",
            "sidewalk:left" to "yes",
            "sidewalk:right" to "separate"
        ))
        val sidewalk = way(4, listOf(4, 5), mapOf("highway" to "footway", "footway" to "sidewalk"))
        val exclusiveCycleway = way(5, listOf(6, 7), mapOf("highway" to "cycleway"))

        assertHighlightedIds(leftOnly, sidewalk, exclusiveCycleway, expectedIds = setOf(4))
        assertHighlightedIds(rightOnly, sidewalk, exclusiveCycleway, expectedIds = setOf(4))
        assertHighlightedIds(mixed, sidewalk, exclusiveCycleway, expectedIds = setOf(4))
    }

    @Test fun `highlights cycleways only when pedestrians can use them`() {
        val road = sidewalkRoad(id = 1, "sidewalk" to "both")
        val exclusiveCycleway = way(2, listOf(4, 5), mapOf("highway" to "cycleway"))
        val sharedCycleway = way(3, listOf(6, 7), mapOf("highway" to "cycleway", "foot" to "yes"))
        val bicycleFootway = way(4, listOf(8, 9), mapOf("highway" to "footway", "bicycle" to "yes"))
        assertHighlightedIds(
            road,
            exclusiveCycleway,
            sharedCycleway,
            bicycleFootway,
            expectedIds = setOf(3, 4)
        )
    }

    @Test fun `remove all sidewalk information`() {
        assertEquals(
            setOf(
                StringMapEntryDelete("sidewalk:left:surface", "asphalt"),
                StringMapEntryDelete("sidewalk:right:surface", "concrete"),
                StringMapEntryDelete("sidewalk:left:smoothness", "excellent"),
                StringMapEntryDelete("sidewalk:right:smoothness", "good"),
                StringMapEntryDelete("sidewalk:left", "yes"),
                StringMapEntryDelete("sidewalk:right", "yes")
            ),
            questType.answerAppliedTo(
                SidewalkSurfaceAnswer.SidewalkIsDifferent,
                mapOf("sidewalk:left:surface" to "asphalt",
                    "sidewalk:right:surface" to "concrete",
                    "sidewalk:left:smoothness" to "excellent",
                    "sidewalk:right:smoothness" to "good",
                    "sidewalk:left" to "yes",
                    "sidewalk:right" to "yes",
                )
            )
        )
    }

    private fun assertIsApplicable(vararg pairs: Pair<String, String>) {
        assertTrue(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }

    private fun assertIsNotApplicable(vararg pairs: Pair<String, String>) {
        assertFalse(questType.isApplicableTo(way(nodes = listOf(1, 2, 3), tags = mapOf(*pairs))))
    }

    private fun sidewalkRoad(id: Long, vararg tags: Pair<String, String>) =
        way(id, listOf(1, 2, 3), mapOf("highway" to "residential", *tags))

    private fun assertHighlightedIds(
        element: Way,
        vararg nearby: Way,
        expectedIds: Set<Long>,
    ) {
        val mapData = TestMapDataWithGeometry(listOf(element) + nearby)
        assertEquals(
            expectedIds,
            questType.getHighlightedElements(element, mapData).map { it.id }.toSet()
        )
    }
}
