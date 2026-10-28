package de.westnordost.streetcomplete.quests.cycleway_separation

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparation
import de.westnordost.streetcomplete.osm.cycleway_separate.CyclewaySeparationAnswer
import de.westnordost.streetcomplete.osm.cycleway_separate.isCyclewaySeparationEligible
import de.westnordost.streetcomplete.osm.cycleway_separate.tagSideForUiSide
import de.westnordost.streetcomplete.osm.cycleway_separate.uiSideForTagSide
import de.westnordost.streetcomplete.osm.cycleway_separate.parseCyclewaySeparationState
import de.westnordost.streetcomplete.osm.Sides
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.is_sidepath.AddIsSidepath
import de.westnordost.streetcomplete.quests.segregated.AddCyclewaySegregation
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.ui.common.street_side_select.Side
import de.westnordost.streetcomplete.testutils.way
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddCyclewaySeparationTest {

    private lateinit var questType: AddCyclewaySeparation

    private val baseTags = mapOf("highway" to "cycleway")

    @BeforeTest fun setUp() {
        Prefs.preferences = inMemoryPrefs()
        questType = AddCyclewaySeparation()
    }

    @Test fun `quest is registered with unique EE id 210`() {
        val registrySource = readSource("quests/QuestTypesRegistry.kt")
        val questId = "EE_QUEST_OFFSET + 210"
        assertEquals(1, registrySource.split(questId).size - 1)
        assertEquals(1, registrySource.split("AddCyclewaySeparation()").size - 1)
    }

    private fun readSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/commonMain/kotlin/de/westnordost/streetcomplete/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Could not find source: $relativePath")
    }

    @Test fun `eligible exclusive cycleway without separation tags`() {
        assertTrue(questType.isApplicableTo(way(tags = baseTags)))
    }

    @Test fun `not eligible for primary road`() {
        assertFalse(questType.isApplicableTo(way(tags = mapOf("highway" to "primary"))))
    }

    @Test fun `not eligible for segregated combined cycleway`() {
        assertFalse(questType.isApplicableTo(way(tags = baseTags + ("segregated" to "yes"))))
    }

    @Test fun `not eligible when both sides tagged`() {
        assertFalse(questType.isApplicableTo(way(tags = baseTags + mapOf(
            "separation:left" to "kerb",
            "separation:right" to "bollard",
        ))))
    }

    @Test fun `not eligible when separation both present`() {
        assertFalse(questType.isApplicableTo(way(tags = baseTags + ("separation:both" to "kerb"))))
    }

    @Test fun `eligible with only left tagged`() {
        assertTrue(questType.isApplicableTo(way(tags = baseTags + ("separation:left" to "kerb"))))
    }

    @Test fun `eligible with only right tagged`() {
        assertTrue(questType.isApplicableTo(way(tags = baseTags + ("separation:right" to "fence"))))
    }

    @Test fun `explicit no counts as answered`() {
        assertTrue(questType.isApplicableTo(way(tags = baseTags + ("separation:left" to "no"))))
        assertFalse(questType.isApplicableTo(way(tags = baseTags + mapOf(
            "separation:left" to "no",
            "separation:right" to "kerb",
        ))))
    }

    @Test fun `legacy separation shorthand excludes quest`() {
        assertFalse(questType.isApplicableTo(way(tags = baseTags + ("separation" to "kerb"))))
    }

    @Test fun `conflicting both and side-specific tags exclude quest`() {
        assertFalse(questType.isApplicableTo(way(tags = baseTags + mapOf(
            "separation:both" to "kerb",
            "separation:left" to "bollard",
        ))))
    }

    @Test fun `writes different separation on opposite sides`() {
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.KERB, CyclewaySeparation.BOLLARD),
            baseTags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("separation:left", "kerb"),
                StringMapEntryAdd("separation:right", "bollard"),
            ),
            changes,
        )
    }

    @Test fun `writes only missing right side`() {
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(null, CyclewaySeparation.FENCE),
            baseTags + ("separation:left" to "kerb"),
        )
        assertEquals(setOf(StringMapEntryAdd("separation:right", "fence")), changes)
    }

    @Test fun `preserves existing left when answering right`() {
        val tags = baseTags + mapOf("separation:left" to "kerb", "buffer:left" to "1")
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(null, CyclewaySeparation.NO),
            tags,
        )
        assertEquals(setOf(StringMapEntryAdd("separation:right", "no")), changes)
        assertTrue(changes.none { it.key == "buffer:left" })
    }

    @Test fun `does not overwrite existing separation values`() {
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.BOLLARD, null),
            baseTags + ("separation:left" to "kerb"),
        )
        assertTrue(changes.isEmpty())
    }

    @Test fun `does not touch is_sidepath or cycleway lane tags`() {
        val tags = baseTags + mapOf(
            "is_sidepath" to "yes",
            "is_sidepath:of" to "primary",
            "cycleway:left" to "lane",
        )
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.KERB, CyclewaySeparation.NO),
            tags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("separation:left", "kerb"),
                StringMapEntryAdd("separation:right", "no"),
            ),
            changes,
        )
        assertTrue(changes.none { it.key.startsWith("is_sidepath") })
        assertTrue(changes.none { it.key.startsWith("cycleway:") })
    }

    @Test fun `not eligible after both sides completed`() {
        val tags = baseTags + mapOf(
            "separation:left" to "kerb",
            "separation:right" to "no",
        )
        assertFalse(isCyclewaySeparationEligible(tags))
    }

    @Test fun `oneway minus one maps UI left to separation right`() {
        val tags = baseTags + ("oneway" to "-1")
        assertEquals(Side.RIGHT, tagSideForUiSide(Side.LEFT, tags))
        assertEquals(Side.LEFT, tagSideForUiSide(Side.RIGHT, tags))
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.NO, CyclewaySeparation.KERB),
            tags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("separation:left", "no"),
                StringMapEntryAdd("separation:right", "kerb"),
            ),
            changes,
        )
    }

    @Test fun `identical values write side keys not separation both`() {
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.KERB, CyclewaySeparation.KERB),
            baseTags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("separation:left", "kerb"),
                StringMapEntryAdd("separation:right", "kerb"),
            ),
            changes,
        )
        assertTrue(changes.none { it.key == "separation:both" })
    }

    @Test fun `bidirectional cycleway uses way direction without swap`() {
        val tags = baseTags + ("oneway:bicycle" to "no")
        assertEquals(Side.LEFT, tagSideForUiSide(Side.LEFT, tags))
        val changes = questType.answerAppliedTo(
            CyclewaySeparationAnswer(CyclewaySeparation.HEDGE, CyclewaySeparation.DITCH),
            tags,
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("separation:left", "hedge"),
                StringMapEntryAdd("separation:right", "ditch"),
            ),
            changes,
        )
    }

    @Test fun `ui and tag side mapping is inverse`() {
        val tags = baseTags + ("oneway" to "yes")
        for (side in Side.entries) {
            assertEquals(side, uiSideForTagSide(tagSideForUiSide(side, tags), tags))
        }
    }

    @Test fun `existing sidepath and segregation quests unchanged`() {
        assertTrue(AddIsSidepath().isApplicableTo(way(tags = mapOf("highway" to "footway"))))
        assertTrue(AddCyclewaySegregation().isApplicableTo(way(tags = mapOf(
            "highway" to "footway",
            "bicycle" to "designated",
            "foot" to "designated",
            "surface" to "asphalt",
        ))))
    }

    @Test fun `form selection writes documented side for each cycling direction`() {
        // Proposal:Separation, Directions of left and right: travel for oneways, geometry otherwise.
        val cases = listOf(
            emptyMap<String, String>() to "separation:left",
            mapOf("oneway" to "no") to "separation:left",
            mapOf("oneway" to "yes") to "separation:left",
            mapOf("oneway" to "-1") to "separation:right",
            mapOf("oneway:bicycle" to "yes") to "separation:left",
            mapOf("oneway:bicycle" to "-1") to "separation:right",
            mapOf("oneway" to "-1", "oneway:bicycle" to "yes") to "separation:left",
            mapOf("oneway" to "yes", "oneway:bicycle" to "-1") to "separation:right",
            mapOf("oneway" to "-1", "oneway:bicycle" to "no") to "separation:left",
        )
        for ((direction, expectedKerbKey) in cases) {
            val tags = baseTags + direction
            val answer = buildAnswer(parseCyclewaySeparationState(tags)!!, tags,
                Sides(CyclewaySeparation.KERB, CyclewaySeparation.NO))
            val otherKey = if (expectedKerbKey == "separation:left") "separation:right" else "separation:left"
            assertEquals(setOf(StringMapEntryAdd(expectedKerbKey, "kerb"), StringMapEntryAdd(otherKey, "no")),
                questType.answerAppliedTo(answer, tags), direction.toString())
        }
    }

    @Test fun `reversing geometry and oneway preserves physical separation tag`() {
        // Same west-side kerb: left of A->B, right of B->A. Cycling remains A->B.
        val a = p(0.0, 0.0)
        val b = p(0.001, 0.0)
        for (bidirectional in listOf(false, true)) {
            for (reversed in listOf(false, true)) {
                val geometry = ElementPolylinesGeometry(listOf(if (reversed) listOf(b, a) else listOf(a, b)), a)
                assertEquals(if (reversed) 180f else 0f, geometry.getOrientationOrZero())
                val tags = baseTags + ("oneway" to if (bidirectional) "no" else if (reversed) "-1" else "yes")
                val selections: Sides<CyclewaySeparation?> = if (reversed) Sides(CyclewaySeparation.NO, CyclewaySeparation.KERB)
                    else Sides(CyclewaySeparation.KERB, CyclewaySeparation.NO)
                val expectedLeft = if (bidirectional && reversed) "no" else "kerb"
                val expectedRight = if (bidirectional && reversed) "kerb" else "no"
                assertEquals(setOf(StringMapEntryAdd("separation:left", expectedLeft), StringMapEntryAdd("separation:right", expectedRight)),
                    questType.answerAppliedTo(buildAnswer(parseCyclewaySeparationState(tags)!!, tags, selections), tags))
            }
        }
    }

    @Test fun `form preserves unknown compound and explicit no on either existing side`() {
        for (key in listOf("separation:left", "separation:right")) {
            for (value in listOf("experimental", "kerb;hedge", "no")) {
                val tags = baseTags + mapOf("oneway" to "-1", key to value)
                val missingKey = if (key == "separation:left") "separation:right" else "separation:left"
                val answer = buildAnswer(parseCyclewaySeparationState(tags)!!, tags,
                    Sides(CyclewaySeparation.FENCE, CyclewaySeparation.FENCE))
                assertEquals(setOf(StringMapEntryAdd(missingKey, "fence")), questType.answerAppliedTo(answer, tags))
            }
        }
    }

    @Test fun `writer preserves shorthand both and conflicts even for stale answers`() {
        for (existing in listOf(
            mapOf("separation" to "kerb;hedge"),
            mapOf("separation:both" to "experimental"),
            mapOf("separation:both" to "kerb", "separation:right" to "no"),
        )) {
            assertTrue(questType.answerAppliedTo(
                CyclewaySeparationAnswer(CyclewaySeparation.NO, CyclewaySeparation.NO), baseTags + existing).isEmpty())
        }
    }

    @Test fun `eligibility keeps the exclusive cycleway scope`() {
        for ((extra, eligible) in listOf(
            mapOf("foot" to "yes") to false,
            mapOf("foot" to "designated") to false,
            mapOf("foot" to "no") to true,
            mapOf("segregated" to "yes") to false,
            mapOf("segregated" to "no") to true,
            mapOf("is_sidepath" to "yes") to true,
        )) {
            assertEquals(eligible, questType.isApplicableTo(way(tags = baseTags + extra)), extra.toString())
        }
    }

    @Test fun `presentation distinguishes compound existing side from unanswered side`() {
        val tags = baseTags + mapOf("separation:left" to "kerb;hedge", "oneway" to "-1")
        val state = parseCyclewaySeparationState(tags)!!
        val sides = presentCyclewaySeparationSides(state, tags, Sides(null, null))
        // separation:left with oneway=-1 is the geographically right side of the way
        assertEquals(CyclewaySeparationSidePresentation.LockedOther("kerb;hedge"), sides.right)
        assertEquals(CyclewaySeparationSidePresentation.Surveyable(null), sides.left)
    }

    @Test fun `catalog uses documented non deprecated physical values`() {
        assertEquals(setOf("no", "kerb", "bollard", "flex_post", "vertical_panel", "bump", "studs",
            "planter", "greenery", "hedge", "tree_row", "fence", "guard_rail", "jersey_barrier", "ditch"),
            CyclewaySeparation.entries.map { it.osmValue }.toSet())
    }
}
