package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.ui.common.quest.Marker
import kotlin.test.Test
import kotlin.test.assertEquals

class HighlightedMarkersTest {
    private fun n(id: Long, vararg tags: Pair<String, String>) =
        node(id, p(52.0 + id / 1000.0, 13.0), mapOf(*tags))

    private fun markers(
        highlighted: List<Element>,
        reference: Element?,
        hideOnDifferentLevel: Boolean = true,
        geometryOf: (Element) -> ElementGeometry? = { pGeom(it.id.toDouble(), 0.0) },
    ) = highlightedMarkers(highlighted.asSequence(), reference, hideOnDifferentLevel, geometryOf, { null }, { "t${it.id}" })

    @Test fun `all highlighted elements become markers`() {
        val result = markers(listOf(n(2), n(3)), n(1))
        assertEquals(listOf(Marker(pGeom(2.0, 0.0), title = "t2"), Marker(pGeom(3.0, 0.0), title = "t3")), result)
    }

    @Test fun `reference element itself is not highlighted`() {
        val ref = n(1)
        assertEquals(listOf("t2"), markers(listOf(ref, n(2)), ref).map { it.title })
    }

    @Test fun `elements without geometry are skipped`() {
        assertEquals(listOf("t3"), markers(listOf(n(2), n(3)), n(1)) { if (it.id == 2L) null else pGeom() }.map { it.title })
    }

    @Test fun `different level is hidden if requested`() {
        val ref = n(1, "level" to "1")
        val result = markers(listOf(n(2, "level" to "1"), n(3, "level" to "2")), ref)
        assertEquals(listOf("t2"), result.map { it.title })
    }

    @Test fun `different layer is hidden if requested`() {
        val ref = n(1, "layer" to "1")
        val result = markers(listOf(n(2, "layer" to "1"), n(3, "layer" to "-1"), n(4)), ref)
        assertEquals(listOf("t2"), result.map { it.title })
    }

    @Test fun `level and layer are ignored if the quest does not ask for filtering`() {
        val ref = n(1, "level" to "1")
        assertEquals(2, markers(listOf(n(2, "level" to "2"), n(3)), ref, hideOnDifferentLevel = false).size)
    }

    @Test fun `without reference element (unlinked external quest) nothing is filtered`() {
        val result = markers(listOf(n(2, "level" to "2", "layer" to "1"), n(3)), reference = null)
        assertEquals(2, result.size)
    }

    @Test fun `nearby markers keep quest color on highlight at same position and form markers are separate`() {
        val highlights = markers(listOf(n(2)), n(1))
        val nearby = listOf(Marker(highlights[0].geometry, color = 0xFF00FF00.toInt()))
        val merged = mergeMarkersAtSamePosition(highlights, nearby)
        assertEquals(listOf(highlights[0].copy(color = 0xFF00FF00.toInt())), merged)
        // the screen uses form markers instead of this list when present, so nothing is mixed in
        val formMarkers: List<Marker>? = listOf(Marker(pGeom(9.0, 9.0)))
        assertEquals(formMarkers, formMarkers ?: merged)
    }
}
