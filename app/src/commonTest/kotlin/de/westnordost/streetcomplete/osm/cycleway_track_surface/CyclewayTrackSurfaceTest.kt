package de.westnordost.streetcomplete.osm.cycleway_track_surface

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.osm.surface.Surface
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CyclewayTrackSurfaceTest {
    private val left = "cycleway:left:surface"
    private val right = "cycleway:right:surface"
    private val road = mapOf("highway" to "residential", "cycleway:both" to "track")

    private fun parts(tags: Map<String, String>) = resolveCyclewayTrackSurfaceParts(tags)
    private fun apply(tags: Map<String, String>, values: Map<String, Surface>, split: Boolean = false): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        applyCyclewayTrackSurfaceAnswer(builder, CyclewayTrackSurfaceAnswer(values, split))
        val result = tags.toMutableMap()
        builder.create().changes.forEach { it.applyTo(result) }
        return result
    }

    // --- eligibility ---

    @Test fun `right track is eligible when surface missing`() {
        val tags = mapOf("highway" to "residential", "cycleway:right" to "track")
        assertEquals(listOf(right), parts(tags).filter { it.needsSurvey }.map { it.key })
    }

    @Test fun `left track is eligible when surface missing`() {
        val tags = mapOf("highway" to "residential", "cycleway:left" to "track")
        assertEquals(listOf(left), parts(tags).filter { it.needsSurvey }.map { it.key })
    }

    @Test fun `both track is eligible when surface missing`() {
        assertEquals(setOf(left, right), parts(road).filter { it.needsSurvey }.map { it.key }.toSet())
    }

    @Test fun `lane right is excluded`() {
        assertTrue(parts(road + ("cycleway:both" to "lane")).isEmpty())
        assertTrue(parts(mapOf("highway" to "residential", "cycleway:right" to "lane")).isEmpty())
    }

    @Test fun `shared lane is excluded`() {
        assertTrue(parts(mapOf("highway" to "residential", "cycleway:right" to "shared_lane")).isEmpty())
    }

    @Test fun `separate is excluded`() {
        assertTrue(parts(mapOf("highway" to "residential", "cycleway:right" to "separate")).isEmpty())
    }

    @Test fun `no is excluded`() {
        assertTrue(parts(mapOf("highway" to "residential", "cycleway:right" to "no")).isEmpty())
    }

    @Test fun `highway cycleway is excluded`() {
        assertTrue(parts(mapOf("highway" to "cycleway", "cycleway:right" to "track")).isEmpty())
    }

    @Test fun `mixed lane and track surveys track side only`() {
        val tags = mapOf("highway" to "residential", "cycleway:left" to "lane", "cycleway:right" to "track")
        assertEquals(listOf(right), parts(tags).filter { it.needsSurvey }.map { it.key })
    }

    @Test fun `already mapped surfaces exclude quest`() {
        assertTrue(parts(road + mapOf(left to "asphalt", right to "paving_stones")).isEmpty())
        assertTrue(parts(road + (BOTH_CYCLEWAY_SURFACE_KEY to "asphalt")).isEmpty())
        assertTrue(parts(road + mapOf(BOTH_CYCLEWAY_SURFACE_KEY to "asphalt", left to "asphalt")).isEmpty())
        assertTrue(parts(road + mapOf(BOTH_CYCLEWAY_SURFACE_KEY to "asphalt", right to "paving_stones")).isEmpty())
    }

    @Test fun `partial left surface surveys right only`() {
        val tags = road + (left to "asphalt")
        assertEquals(listOf(right), parts(tags).filter { it.needsSurvey }.map { it.key })
    }

    @Test fun `unsupported existing surface is preserved and not surveyed`() {
        val tags = road + (left to "corrugated_steel")
        assertEquals(listOf(right), parts(tags).filter { it.needsSurvey }.map { it.key })
        assertEquals("corrugated_steel", apply(tags, mapOf(right to Surface.ASPHALT))[left])
    }

    // --- tag writing ---

    @Test fun `writes right side surface`() {
        val tags = mapOf("highway" to "residential", "cycleway:right" to "track", "surface" to "concrete")
        val result = apply(tags, mapOf(right to Surface.ASPHALT))
        assertEquals("asphalt", result[right])
        assertEquals("concrete", result["surface"])
    }

    @Test fun `writes left side surface`() {
        val tags = mapOf("highway" to "residential", "cycleway:left" to "track")
        assertEquals("paving_stones", apply(tags, mapOf(left to Surface.PAVING_STONES))[left])
    }

    @Test fun `writes two different side surfaces`() {
        val result = apply(road, mapOf(left to Surface.ASPHALT, right to Surface.PAVING_STONES))
        assertEquals("asphalt", result[left])
        assertEquals("paving_stones", result[right])
        assertNull(result[BOTH_CYCLEWAY_SURFACE_KEY])
    }

    @Test fun `equal surfaces on both sides can write both key`() {
        val result = apply(road, mapOf(left to Surface.ASPHALT, right to Surface.ASPHALT))
        assertEquals("asphalt", result[BOTH_CYCLEWAY_SURFACE_KEY])
        assertNull(result[left])
        assertNull(result[right])
    }

    @Test fun `partial answer preserves existing left surface`() {
        val tags = road + (left to "asphalt")
        val result = apply(tags, mapOf(right to Surface.PAVING_STONES))
        assertEquals("asphalt", result[left])
        assertEquals("paving_stones", result[right])
    }

    @Test fun `does not overwrite existing both surface`() {
        val tags = road + (BOTH_CYCLEWAY_SURFACE_KEY to "asphalt")
        assertTrue(parts(tags).isEmpty())
    }

    @Test fun `does not overwrite conflicting side override`() {
        assertTrue(parts(road + mapOf(BOTH_CYCLEWAY_SURFACE_KEY to "asphalt", right to "paving_stones")).isEmpty())
    }

    @Test fun `does not modify road surface tag`() {
        val tags = road + ("surface" to "sett")
        assertEquals("sett", apply(tags, mapOf(left to Surface.ASPHALT, right to Surface.ASPHALT))["surface"])
    }

    @Test fun `empty answer is rejected`() {
        assertFailsWith<IllegalArgumentException> { apply(road, emptyMap()) }
    }

    @Test fun `cannot overwrite answered side`() {
        val tags = road + (left to "asphalt")
        assertFailsWith<IllegalArgumentException> { apply(tags, mapOf(left to Surface.CONCRETE)) }
    }

    @Test fun `split both replaces common surface with side tags`() {
        val tags = road + mapOf(BOTH_CYCLEWAY_SURFACE_KEY to "cobblestone", "check_date:$BOTH_CYCLEWAY_SURFACE_KEY" to "2000-01-01")
        assertEquals(2, parts(tags).count { it.needsSurvey })
        val result = apply(
            tags,
            mapOf(left to Surface.ASPHALT, right to Surface.PAVING_STONES),
            split = true,
        )
        assertNull(result[BOTH_CYCLEWAY_SURFACE_KEY])
        assertEquals("asphalt", result[left])
        assertEquals("paving_stones", result[right])
    }

    // --- direction ---

    @Test fun `oneway and reversed geometry do not flip tag sides`() {
        val tags = road - "cycleway:both" + ("cycleway:left" to "track")
        val forward = way(nodes = listOf(1, 2, 3), tags = tags)
        val reverse = way(nodes = listOf(3, 2, 1), tags = tags + mapOf("oneway" to "yes"))
        val reverseBicycle = way(nodes = listOf(3, 2, 1), tags = tags + mapOf("oneway:bicycle" to "-1"))
        assertEquals(
            listOf(left),
            parts(forward.tags).filter { it.needsSurvey }.map { it.key },
        )
        assertEquals(
            parts(forward.tags).filter { it.needsSurvey }.map { it.key },
            parts(reverse.tags).filter { it.needsSurvey }.map { it.key },
        )
        assertEquals(
            parts(forward.tags).filter { it.needsSurvey }.map { it.key },
            parts(reverseBicycle.tags).filter { it.needsSurvey }.map { it.key },
        )
        val points = listOf(p(52.0, 13.0), p(52.001, 13.0))
        val geometry = ElementPolylinesGeometry(listOf(points), p(52.0005, 13.0))
        val reversedGeometry = geometry.copy(polylines = listOf(points.reversed()))
        val difference = (reversedGeometry.getOrientationOrZero() - geometry.getOrientationOrZero() + 360f) % 360f
        assertEquals(180f, difference, 0.1f)
        assertEquals("asphalt", apply(tags + ("oneway" to "-1"), mapOf(left to Surface.ASPHALT))[left])
    }
}
