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

class AddIsSidepathRoadMetadataTest {

    private lateinit var questType: AddIsSidepathRoadMetadata

    @BeforeTest
    fun setUp() {
        Prefs.preferences = mockPrefs3()
        questType = AddIsSidepathRoadMetadata()
    }

    // region applyAnswerTo

    @Test fun `fills missing highway and name on classified cycleway`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Hauptstraße", ofHighway = "primary"),
            mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("is_sidepath:of", "primary"),
                StringMapEntryAdd("is_sidepath:of:name", "Hauptstraße"),
            ),
            changes,
        )
    }

    @Test fun `fills missing metadata on classified footway`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Ring", ofHighway = "secondary"),
            mapOf("highway" to "footway", "is_sidepath" to "yes"),
        )
        assertTrue(changes.contains(StringMapEntryAdd("is_sidepath:of", "secondary")))
        assertTrue(changes.contains(StringMapEntryAdd("is_sidepath:of:name", "Ring")))
    }

    @Test fun `writes only missing is_sidepath of when name already present`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Other", ofHighway = "primary"),
            mapOf(
                "highway" to "footway",
                "is_sidepath" to "yes",
                "is_sidepath:of:name" to "Hauptstraße",
            ),
        )
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath:of", "primary")),
            changes,
        )
    }

    @Test fun `writes only missing name when highway already present`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Hauptstraße", ofHighway = "secondary"),
            mapOf(
                "highway" to "cycleway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "primary",
            ),
        )
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath:of:name", "Hauptstraße")),
            changes,
        )
    }

    @Test fun `does not overwrite conflicting highway`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Hauptstraße", ofHighway = "secondary"),
            mapOf(
                "highway" to "cycleway",
                "is_sidepath" to "yes",
                "is_sidepath:of" to "primary",
            ),
        )
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath:of:name", "Hauptstraße")),
            changes,
        )
        assertTrue(changes.none { it.key == "is_sidepath:of" && (it as StringMapEntryAdd).value == "secondary" })
    }

    @Test fun `does not overwrite conflicting name`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Neue Straße", ofHighway = "primary"),
            mapOf(
                "highway" to "footway",
                "is_sidepath" to "yes",
                "is_sidepath:of:name" to "Hauptstraße",
            ),
        )
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath:of", "primary")),
            changes,
        )
        assertTrue(changes.none { it.key == "is_sidepath:of:name" })
    }

    @Test fun `preserves is_sidepath of ref and unrelated tags`() {
        val original = mapOf(
            "highway" to "path",
            "foot" to "yes",
            "is_sidepath" to "yes",
            "is_sidepath:of:ref" to "B 1",
            "surface" to "asphalt",
            "lit" to "yes",
        )
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "Altstraße", ofHighway = "primary"),
            original,
        )
        assertTrue(changes.none { it.key in setOf("is_sidepath", "is_sidepath:of:ref", "surface", "lit", "foot") })
    }

    @Test fun `does not write empty road name`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = "", ofHighway = "residential"),
            mapOf("highway" to "footway", "is_sidepath" to "yes"),
        )
        assertTrue(changes.none { it.key == "is_sidepath:of:name" })
        assertTrue(changes.contains(StringMapEntryAdd("is_sidepath:of", "residential")))
    }

    @Test fun `not sidepath sets is_sidepath no without road metadata`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.NotSidepath,
            mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
        )
        assertEquals(setOf(StringMapEntryModify("is_sidepath", "yes", "no")), changes)
    }

    @Test fun `not sidepath removes is_sidepath of and name`() {
        val original = mapOf(
            "highway" to "cycleway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "secondary",
            "is_sidepath:of:name" to "Example Street",
        )
        val changes = questType.answerAppliedTo(SidepathRoadMetadataAnswer.NotSidepath, original)
        assertEquals(setOf(StringMapEntryModify("is_sidepath", "yes", "no")), changes.filterIsInstance<StringMapEntryModify>().toSet())
        assertEquals(
            setOf("is_sidepath:of", "is_sidepath:of:name"),
            changes.filterIsInstance<StringMapEntryDelete>().map { it.key }.toSet(),
        )
    }

    @Test fun `not sidepath preserves unrelated tags`() {
        val original = mapOf(
            "highway" to "path",
            "foot" to "yes",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "primary",
            "surface" to "asphalt",
            "lit" to "yes",
        )
        val changes = questType.answerAppliedTo(SidepathRoadMetadataAnswer.NotSidepath, original)
        assertTrue(changes.none { it.key in setOf("highway", "foot", "surface", "lit") })
    }

    @Test fun `not sidepath quest no longer applicable`() {
        val tags = mapOf("highway" to "cycleway", "is_sidepath" to "yes")
        val changes = questType.answerAppliedTo(SidepathRoadMetadataAnswer.NotSidepath, tags)
        val updated = tags.toMutableMap()
        changes.forEach { change ->
            when (change) {
                is StringMapEntryAdd -> updated[change.key] = change.value
                is StringMapEntryModify -> updated[change.key] = change.value
                is StringMapEntryDelete -> updated.remove(change.key)
            }
        }
        assertFalse(questType.isApplicableTo(way(tags = updated)))
    }

    @Test fun `road association with nulls writes nothing`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = null, ofHighway = null),
            mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
        )
        assertTrue(changes.isEmpty())
    }

    @Test fun `produces changes helper matches applyAnswerTo`() {
        val tags = mapOf(
            "highway" to "cycleway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "residential",
        )
        assertFalse(
            sidepathRoadMetadataAnswerProducesChanges(tags, ofName = null, ofHighway = "residential"),
        )
        assertTrue(
            sidepathRoadMetadataAnswerProducesChanges(tags, ofName = "Ring", ofHighway = "residential"),
        )
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = null, ofHighway = "residential"),
            tags,
        )
        assertTrue(changes.isEmpty())
    }

    @Test fun `matching named road when name already tagged produces no changes`() {
        val tags = mapOf(
            "highway" to "footway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "primary",
            "is_sidepath:of:name" to "Hauptstraße",
        )
        assertFalse(
            sidepathRoadMetadataAnswerProducesChanges(tags, ofName = "Hauptstraße", ofHighway = "primary"),
        )
    }

    @Test fun `unnamed road writes highway only`() {
        val changes = questType.answerAppliedTo(
            SidepathRoadMetadataAnswer.RoadAssociation(ofName = null, ofHighway = "tertiary"),
            mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
        )
        assertEquals(
            setOf(StringMapEntryAdd("is_sidepath:of", "tertiary")),
            changes,
        )
    }

    // endregion

    // region isApplicableTo

    @Test fun `not applicable when both association tags present`() {
        assertFalse(
            questType.isApplicableTo(
                way(
                    tags = mapOf(
                        "highway" to "cycleway",
                        "is_sidepath" to "yes",
                        "is_sidepath:of" to "primary",
                        "is_sidepath:of:name" to "Hauptstraße",
                    ),
                ),
            ),
        )
    }

    @Test fun `not applicable for is_sidepath no`() {
        assertFalse(
            questType.isApplicableTo(
                way(tags = mapOf("highway" to "cycleway", "is_sidepath" to "no")),
            ),
        )
    }

    @Test fun `not applicable for unclassified cycleway`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf("highway" to "cycleway"))))
    }

    @Test fun `not applicable for unclassified footway`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf("highway" to "footway"))))
    }

    @Test fun `applicable tag filter for sidepath yes missing of`() {
        assertTrue(
            questType.isApplicableTo(
                way(tags = mapOf("highway" to "cycleway", "is_sidepath" to "yes")),
            ),
        )
    }

    @Test fun `applicable tag filter for sidepath yes missing name only`() {
        assertTrue(
            questType.isApplicableTo(
                way(
                    tags = mapOf(
                        "highway" to "footway",
                        "is_sidepath" to "yes",
                        "is_sidepath:of" to "residential",
                    ),
                ),
            ),
        )
    }

    @Test fun `applicable tag filter for footway sidewalk with is_sidepath yes`() {
        assertTrue(
            questType.isApplicableTo(
                way(
                    tags = mapOf(
                        "highway" to "footway",
                        "footway" to "sidewalk",
                        "is_sidepath" to "yes",
                    ),
                ),
            ),
        )
    }

    // endregion

    // region geometry gating

    @Test fun `classified cycleway missing metadata with nearby road is applicable`() {
        assertEquals(
            1,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
                    roadHighway = "primary",
                    roadName = "Hauptstraße",
                ),
            ),
        )
    }

    @Test fun `classified footway missing metadata with nearby road is applicable`() {
        assertEquals(
            1,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf("highway" to "footway", "is_sidepath" to "yes"),
                ),
            ),
        )
    }

    @Test fun `not applicable without nearby road`() {
        assertEquals(
            0,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
                    includeRoad = false,
                ),
            ),
        )
    }

    @Test fun `name only missing not applicable when no candidate has a name`() {
        assertEquals(
            0,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf(
                        "highway" to "cycleway",
                        "is_sidepath" to "yes",
                        "is_sidepath:of" to "primary",
                    ),
                    roadName = null,
                ),
            ),
        )
    }

    @Test fun `not applicable when only nearby roads would not add metadata`() {
        assertEquals(
            0,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf(
                        "highway" to "cycleway",
                        "is_sidepath" to "yes",
                        "is_sidepath:of" to "residential",
                    ),
                    roadHighway = "residential",
                    roadName = null,
                ),
            ),
        )
    }

    @Test fun `name only missing applicable when a named candidate exists`() {
        assertEquals(
            1,
            applicableCount(
                sidepathAndRoad(
                    pathTags = mapOf(
                        "highway" to "cycleway",
                        "is_sidepath" to "yes",
                        "is_sidepath:of" to "primary",
                    ),
                    roadName = "Hauptstraße",
                ),
            ),
        )
    }

    @Test fun `multiple candidate roads still applicable`() {
        val roadStart = p(0.0, 0.0)
        val roadEnd = roadStart.translate(50.0, 45.0)
        val road2Start = roadStart.translate(8.0, 135.0)
        val road2End = road2Start.translate(50.0, 45.0)
        val pathStart = roadStart.translate(4.0, 135.0)
        val pathEnd = pathStart.translate(50.0, 45.0)

        val road1 = way(1, listOf(1, 2), mapOf("highway" to "primary", "name" to "A"))
        val road2 = way(2, listOf(3, 4), mapOf("highway" to "secondary", "name" to "B"))
        val path = way(
            3,
            listOf(5, 6),
            mapOf("highway" to "cycleway", "is_sidepath" to "yes"),
        )
        val mapData = createMapData(
            mapOf(
                road1 to ElementPolylinesGeometry(listOf(listOf(roadStart, roadEnd)), roadStart),
                road2 to ElementPolylinesGeometry(listOf(listOf(road2Start, road2End)), road2Start),
                path to ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart),
            ),
        )
        assertEquals(1, questType.getApplicableElements(mapData).toList().size)
    }

    @Test fun `after filling both tags element is not applicable`() {
        val tags = mapOf(
            "highway" to "cycleway",
            "is_sidepath" to "yes",
            "is_sidepath:of" to "primary",
            "is_sidepath:of:name" to "Hauptstraße",
        )
        assertFalse(questType.isApplicableTo(way(tags = tags)))
    }

    @Test fun `AddIsSidepath still excludes already tagged sidepath for classification`() {
        val classification = AddIsSidepath()
        assertFalse(
            classification.isApplicableTo(
                way(tags = mapOf("highway" to "footway", "is_sidepath" to "yes")),
            ),
        )
    }

    // endregion

    private fun applicableCount(
        mapData: Map<de.westnordost.streetcomplete.data.osm.mapdata.Element, ElementPolylinesGeometry?>,
    ): Int = questType.getApplicableElements(createMapData(mapData)).toList().size

    private fun sidepathAndRoad(
        pathOffsetMeters: Double = 5.0,
        pathTags: Map<String, String>,
        roadHighway: String = "residential",
        roadName: String? = "Hauptstraße",
        includeRoad: Boolean = true,
        roadLengthMeters: Double = 50.0,
        bearing: Double = 45.0,
    ): Map<de.westnordost.streetcomplete.data.osm.mapdata.Element, ElementPolylinesGeometry?> {
        val roadStart = p(0.0, 0.0)
        val roadEnd = roadStart.translate(roadLengthMeters, bearing)
        val pathStart = roadStart.translate(pathOffsetMeters, bearing + 90.0)
        val pathEnd = pathStart.translate(roadLengthMeters, bearing)

        val roadTags = buildMap {
            put("highway", roadHighway)
            if (roadName != null) put("name", roadName)
        }
        val road = way(1, listOf(1, 2), roadTags)
        val path = way(2, listOf(3, 4), pathTags)
        return buildMap {
            if (includeRoad) {
                put(road, ElementPolylinesGeometry(listOf(listOf(roadStart, roadEnd)), roadStart))
            }
            put(path, ElementPolylinesGeometry(listOf(listOf(pathStart, pathEnd)), pathStart))
        }
    }
}
