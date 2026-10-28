package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.quests.smoothness.Smoothness.*
import de.westnordost.streetcomplete.resources.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothnessItemKtTest {

    private val paved = listOf(EXCELLENT, GOOD, INTERMEDIATE, BAD, VERY_BAD)

    /** The values offered by the smoothness quests must not change (golden values from before
     *  the shared catalogue was introduced). */
    @Test fun `quest surfaces keep their smoothness values`() {
        val expected = mapOf(
            "asphalt" to paved,
            "chipseal" to paved,
            "concrete" to paved,
            "concrete:plates" to paved,
            "concrete:lanes" to paved,
            "sett" to listOf(GOOD, INTERMEDIATE, BAD, VERY_BAD),
            "unhewn_cobblestone" to listOf(GOOD, INTERMEDIATE, BAD, VERY_BAD),
            "paving_stones" to paved,
            "grass_paver" to paved,
            "compacted" to listOf(GOOD, INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE),
            "gravel" to listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE),
            "fine_gravel" to listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE),
            "pebblestone" to listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE),
        )
        assertEquals(SURFACES_FOR_SMOOTHNESS.toSet(), expected.keys)
        for ((surface, values) in expected) {
            assertEquals(values, smoothnessAnswersForSurface(surface), surface)
            assertTrue(hasSmoothnessProfile(surface), surface)
        }
    }

    @Test fun `upstream surfaces keep their photos`() {
        assertEquals(Res.drawable.surface_asphalt_excellent, smoothnessChoice(EXCELLENT, "asphalt").photo?.image)
        assertEquals(Res.drawable.surface_concrete_bad, smoothnessChoice(BAD, "concrete:plates").photo?.image)
        assertEquals(Res.drawable.surface_sett_good, smoothnessChoice(GOOD, "sett").photo?.image)
        assertEquals(Res.drawable.surface_paving_stones_very_bad, smoothnessChoice(VERY_BAD, "paving_stones").photo?.image)
        assertEquals(Res.drawable.surface_compacted_good, smoothnessChoice(GOOD, "compacted").photo?.image)
        assertEquals(Res.drawable.surface_gravel_bad, smoothnessChoice(BAD, "fine_gravel").photo?.image)
        assertEquals(Res.drawable.surface_unpaved_impassable, smoothnessChoice(IMPASSABLE, "gravel").photo?.image)
        for (surface in listOf("asphalt", "concrete", "concrete:plates", "sett", "paving_stones")) {
            for (choice in smoothnessChoices(surface)) {
                val photo = assertNotNull(choice.photo, "$surface ${choice.value}")
                assertNull(photo.similarSurface, "$surface ${choice.value}")
            }
        }
    }

    @Test fun `upstream descriptions are kept`() {
        assertEquals(Res.string.quest_smoothness_description_good_paved, smoothnessChoice(GOOD, "asphalt").description)
        assertEquals(Res.string.quest_smoothness_description_bad_sett, smoothnessChoice(BAD, "sett").description)
        assertEquals(Res.string.quest_smoothness_description_bad_compacted_gravel, smoothnessChoice(BAD, "fine_gravel").description)
        assertEquals(Res.string.quest_smoothness_description_horrible, smoothnessChoice(HORRIBLE, "gravel").description)
    }

    @Test fun `photos of other surfaces are marked as similar surface`() {
        assertEquals("asphalt", smoothnessChoice(GOOD, "chipseal").photo?.similarSurface)
        assertEquals("gravel", smoothnessChoice(BAD, "pebblestone").photo?.similarSurface)
        assertEquals("ground", smoothnessChoice(HORRIBLE, "compacted").photo?.similarSurface)
        // the very same photo is not marked on the surface it shows
        assertNull(smoothnessChoice(HORRIBLE, "ground").photo?.similarSurface)
        assertNull(smoothnessChoice(VERY_HORRIBLE, "dirt").photo?.similarSurface)
    }

    @Test fun `no misleading photos for surfaces that only had substitutes`() {
        for (surface in listOf("concrete:lanes")) {
            for (choice in smoothnessChoices(surface)) {
                assertNull(choice.photo, "$surface ${choice.value}")
            }
        }
        val settPhotos = Smoothness.entries.mapNotNull { smoothnessChoice(it, "sett").photo?.image }.toSet()
        val pavingPhotos = Smoothness.entries.mapNotNull { smoothnessChoice(it, "paving_stones").photo?.image }.toSet()
        val concretePhotos = Smoothness.entries.mapNotNull { smoothnessChoice(it, "concrete").photo?.image }.toSet()
        for (value in Smoothness.entries) {
            assertFalse(smoothnessChoice(value, "unhewn_cobblestone").photo?.image in settPhotos, "unhewn $value")
            assertFalse(smoothnessChoice(value, "grass_paver").photo?.image in pavingPhotos, "grass_paver $value")
            assertFalse(smoothnessChoice(value, "concrete:lanes").photo?.image in concretePhotos, "lanes $value")
        }
    }

    @Test fun `no paved photos for unpaved and natural surfaces`() {
        val pavedPhotos = listOf("asphalt", "concrete", "sett", "paving_stones").flatMap { s ->
            Smoothness.entries.mapNotNull { smoothnessChoice(it, s).photo?.image }
        }.toSet()
        val surfaces = listOf(
            "grass", "dirt", "earth", "soil", "sand", "mud", "ground", "unpaved", "woodchips",
            "metal", "metal_grid", "wood", "rock", "laterite", "stepping_stones", "clay", null, "unknown"
        )
        for (surface in surfaces) {
            for (value in Smoothness.entries) {
                assertFalse(smoothnessChoice(value, surface).photo?.image in pavedPhotos, "$surface $value")
            }
        }
    }

    @Test fun `special surfaces have dedicated profiles`() {
        assertEquals(listOf(GOOD, INTERMEDIATE, BAD), smoothnessAnswersForSurface("metal_grid"))
        assertEquals(listOf(BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE), smoothnessAnswersForSurface("rock"))
        assertEquals(listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE), smoothnessAnswersForSurface("laterite"))
        for (surface in listOf("metal_grid", "rock", "laterite")) assertTrue(hasSmoothnessProfile(surface))
    }

    @Test fun `surfaces without upstream photos use their own photos`() {
        val own = mapOf(
            "unhewn_cobblestone" to listOf(BAD, VERY_BAD),
            "grass_paver" to listOf(INTERMEDIATE, BAD, VERY_BAD),
            "wood" to listOf(GOOD, INTERMEDIATE, BAD, VERY_BAD),
            "metal" to listOf(GOOD, INTERMEDIATE),
            "ground" to listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE),
            "dirt" to listOf(INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE),
            "grass" to listOf(INTERMEDIATE, HORRIBLE),
            "sand" to listOf(BAD, VERY_BAD),
            "mud" to listOf(VERY_BAD),
            "woodchips" to listOf(INTERMEDIATE),
            "fine_gravel" to listOf(INTERMEDIATE),
        )
        for ((surface, values) in own) {
            for (value in values) {
                val photo = assertNotNull(smoothnessChoice(value, surface).photo, "$surface $value")
                assertNull(photo.similarSurface, "$surface $value")
                assertTrue(value in smoothnessAnswersForSurface(surface), "$surface $value")
            }
        }
        // unhewn cobblestone and grass paver keep their quest values, without photos of other surfaces
        assertNull(smoothnessChoice(GOOD, "unhewn_cobblestone").photo)
        assertNull(smoothnessChoice(EXCELLENT, "grass_paver").photo)
    }

    @Test fun `aliases use the profile of their surface`() {
        assertEquals(smoothnessAnswersForSurface("dirt"), smoothnessAnswersForSurface("earth"))
        assertEquals(smoothnessAnswersForSurface("dirt"), smoothnessAnswersForSurface("soil"))
        assertEquals(smoothnessAnswersForSurface("paving_stones"), smoothnessAnswersForSurface("bricks"))
        assertEquals(smoothnessChoice(GOOD, "concrete"), smoothnessChoice(GOOD, "concrete:plates"))
    }

    @Test fun `unknown and special surfaces offer a generic selection without photos`() {
        for (surface in listOf(null, "cobblestone", "stepping_stones", "artificial_turf", "clay")) {
            assertFalse(hasSmoothnessProfile(surface), "$surface")
            assertEquals(Smoothness.entries, smoothnessAnswersForSurface(surface), "$surface")
            assertTrue(smoothnessChoices(surface).all { it.photo == null }, "$surface")
        }
    }

    @Test fun `existing value outside the usual range stays selectable`() {
        assertEquals(paved, selectableSmoothnessChoices("asphalt", GOOD).map { it.value })
        assertEquals(paved + HORRIBLE, selectableSmoothnessChoices("asphalt", HORRIBLE).map { it.value })
        assertEquals(
            listOf(EXCELLENT, INTERMEDIATE, BAD, VERY_BAD, HORRIBLE, VERY_HORRIBLE, IMPASSABLE),
            selectableSmoothnessChoices("gravel", EXCELLENT).map { it.value }
        )
    }

    @Test fun `parse smoothness`() {
        assertEquals(VERY_BAD, parseSmoothness("very_bad"))
        assertNull(parseSmoothness("very_good"))
        assertNull(parseSmoothness(null))
    }

    @Test fun `surface key helper reads the requested surface tag`() {
        val tags = mapOf(
            "cycleway:surface" to "asphalt",
            "surface" to "gravel",
            "footway:surface" to "sett",
        )
        assertEquals(
            smoothnessAnswersForSurface("asphalt"),
            smoothnessAnswersForSurfaceKey(tags, "cycleway:surface")
        )
        assertEquals(
            smoothnessAnswersForSurface("gravel"),
            smoothnessAnswersForSurfaceKey(tags)
        )
    }
}
