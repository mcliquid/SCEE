package de.westnordost.streetcomplete.quests.surface

import de.westnordost.streetcomplete.testutils.way
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddRoadAttachedCyclewaySurfaceTest {
    private lateinit var questType: AddRoadAttachedCyclewaySurface

    @BeforeTest fun setup() {
        questType = AddRoadAttachedCyclewaySurface()
    }

    @Test fun `quest is registered with unique EE id 211`() {
        val registrySource = readSource("quests/QuestTypesRegistry.kt")
        assertEquals(1, registrySource.split("EE_QUEST_OFFSET + 211").size - 1)
        assertEquals(1, registrySource.split("AddRoadAttachedCyclewaySurface()").size - 1)
    }

    @Test fun `filter matches track and resolver excludes lane`() {
        val track = way(tags = mapOf("highway" to "residential", "cycleway:right" to "track"))
        val lane = way(tags = mapOf("highway" to "residential", "cycleway:right" to "lane"))
        val mapped = way(tags = mapOf(
            "highway" to "residential",
            "cycleway:both" to "track",
            "cycleway:left:surface" to "asphalt",
            "cycleway:right:surface" to "paving_stones",
        ))
        assertTrue(questType.isApplicableTo(track))
        assertFalse(questType.isApplicableTo(lane))
        assertFalse(questType.isApplicableTo(mapped))
    }

    private fun readSource(relativePath: String): String {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/commonMain/kotlin/de/westnordost/streetcomplete/$relativePath")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("Could not find $relativePath")
    }
}
