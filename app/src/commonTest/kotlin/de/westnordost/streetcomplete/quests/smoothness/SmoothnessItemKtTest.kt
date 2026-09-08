package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.quests.smoothness.Smoothness.BAD
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.EXCELLENT
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.GOOD
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.HORRIBLE
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.IMPASSABLE
import de.westnordost.streetcomplete.quests.smoothness.Smoothness.INTERMEDIATE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothnessItemKtTest {

    @Test fun `asphalt keeps dedicated photos and omits values without asphalt images`() {
        assertEquals("asphalt", EXCELLENT.getIllustrationSurface("asphalt"))
        assertNotNull(EXCELLENT.getImage("asphalt"))
        assertNull(HORRIBLE.getImage("asphalt"))
        assertEquals(
            listOf(EXCELLENT, GOOD, INTERMEDIATE, BAD, Smoothness.VERY_BAD),
            smoothnessAnswersForSurface("asphalt")
        )
    }

    @Test fun `sett keeps dedicated photos`() {
        assertEquals("sett", GOOD.getIllustrationSurface("sett"))
        assertNull(EXCELLENT.getImage("sett"))
        assertTrue(GOOD in smoothnessAnswersForSurface("sett"))
    }

    @Test fun `generic SCEE surfaces reuse asphalt and gravel illustrations`() {
        val genericSurfaces = listOf(
            "grass", "dirt", "sand", "mud", "ground", "unpaved", "woodchips",
            "pebblestone", "grass_paver", "unhewn_cobblestone", "metal", "rock",
            "laterite", "earth"
        )
        for (surface in genericSurfaces) {
            assertEquals("asphalt", EXCELLENT.getIllustrationSurface(surface), surface)
            assertEquals("asphalt", GOOD.getIllustrationSurface(surface), surface)
            assertEquals("gravel", INTERMEDIATE.getIllustrationSurface(surface), surface)
            assertEquals("gravel", IMPASSABLE.getIllustrationSurface(surface), surface)
            val answers = smoothnessAnswersForSurface(surface)
            assertEquals(Smoothness.entries, answers, surface)
            for (answer in answers) {
                assertNotNull(answer.getImage(answer.getIllustrationSurface(surface)), "$surface $answer")
            }
        }
    }
}
