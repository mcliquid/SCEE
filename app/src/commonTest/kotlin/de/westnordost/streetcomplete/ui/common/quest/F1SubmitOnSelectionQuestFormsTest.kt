package de.westnordost.streetcomplete.ui.common.quest

import de.westnordost.streetcomplete.data.osm.osmquests.Answer
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.quests.smoothness.Smoothness
import de.westnordost.streetcomplete.quests.smoothness.SmoothnessValueAnswer
import de.westnordost.streetcomplete.quests.surface.SurfaceAnswer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression guard for F1 auto-confirm on simple single-choice quest forms. */
class F1SubmitOnSelectionQuestFormsTest {

    @Test fun `null selection does not auto-submit`() {
        val answers = mutableListOf<String>()
        var intermediate: String? = "unchanged"

        handleSingleChoiceSelection(
            selection = null,
            submitOnSelection = true,
            onIntermediateSelection = { intermediate = it },
            onTerminalAnswer = answers::add,
        )

        assertEquals(null, intermediate)
        assertEquals(emptyList(), answers)
    }

    @Test fun `smoothness form answer mapping works with immediate submit`() {
        val actions = mutableListOf<Any>()
        val onSmoothness: (Answer<Smoothness>) -> Unit = { actions += Answer(SmoothnessValueAnswer(it.value)) }

        handleSingleChoiceSelection(
            selection = Smoothness.EXCELLENT,
            submitOnSelection = true,
            onIntermediateSelection = {},
            onTerminalAnswer = { onSmoothness(Answer(it)) },
        )

        assertEquals(SmoothnessValueAnswer(Smoothness.EXCELLENT), (actions.single() as Answer<*>).value)
    }

    @Test fun `path surface form answer mapping works with immediate submit`() {
        val actions = mutableListOf<Any>()
        val onSurface: (Answer<Surface>) -> Unit = { actions += Answer(SurfaceAnswer(it.value)) }

        handleSingleChoiceSelection(
            selection = Surface.ASPHALT,
            submitOnSelection = true,
            onIntermediateSelection = {},
            onTerminalAnswer = { onSurface(Answer(it)) },
        )

        assertEquals(SurfaceAnswer(Surface.ASPHALT), (actions.single() as Answer<*>).value)
    }

    @Test fun `F1 quest forms wire submitOnSelection`() {
        val withSubmitOnSelection = listOf(
            "de/westnordost/streetcomplete/quests/surface/AddRoadSurface.kt",
            "de/westnordost/streetcomplete/quests/surface/AddPathSurfaceForm.kt",
            "de/westnordost/streetcomplete/quests/surface/AddPathPartSurfaceForm.kt",
            "de/westnordost/streetcomplete/quests/smoothness/AddSmoothnessForm.kt",
            "de/westnordost/streetcomplete/quests/tracktype/AddTracktype.kt",
            "de/westnordost/streetcomplete/quests/building_type/AddBuildingTypeForm.kt",
            "de/westnordost/streetcomplete/quests/roof_shape/AddRoofShape.kt",
        )
        for (relativePath in withSubmitOnSelection) {
            val source = readQuestSource(relativePath)
            assertTrue(source.contains("submitOnSelection = true"), relativePath)
        }
    }

    @Test fun `multi-step surface quests keep manual confirm`() {
        val sidewalkForm = readQuestSource(
            "de/westnordost/streetcomplete/quests/surface/AddSidewalkSurfaceForm.kt",
        )
        assertFalse(sidewalkForm.contains("submitOnSelection = true"))
    }

    private fun readQuestSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/commonMain/kotlin/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Could not find quest source: $relativePath")
    }
}
