package de.westnordost.streetcomplete.osm.traffic_sign

import de.westnordost.streetcomplete.data.preferences.getLastPicked
import de.westnordost.streetcomplete.data.preferences.setLastPicked
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TrafficSignHistoryTest {
    private val catalogue = TrafficSignTestData.catalogueDE
    private fun c(f: String, b: String = f) = TrafficSignCombination(f, b)

    @Test fun `newest first, no duplicates, at most six`() {
        var h = emptyList<TrafficSignCombination>()
        for (i in 1..8) h = h.withPicked(c("DE:274[$i]"))
        h = h.withPicked(c("DE:274[5]"))
        assertEquals(6, h.size)
        assertEquals(c("DE:274[5]"), h[0])
        assertEquals(c("DE:274[8]"), h[1])
        assertEquals(6, h.toSet().size)
    }

    @Test fun `per direction combination keeps both values`() {
        val sel = WaySignSelection(DirectionSelection.decode("DE:250", catalogue), DirectionSelection.None)
        val combo = TrafficSignCombination.of(sel)!!
        assertEquals(c("DE:250", "none"), combo)
        assertEquals(sel.forward.encode(), combo.toSelection(catalogue).forward.encode())
        assertEquals(DirectionSelection.None, combo.toSelection(catalogue).backward)
        assertEquals(false, combo.isSameInBothDirections)
    }

    @Test fun `nothing is remembered without signs`() {
        assertNull(TrafficSignCombination.of(WaySignSelection(DirectionSelection.None, DirectionSelection.NotSurveyed)))
    }

    @Test fun `multiple signs per combination survive persistence`() {
        val prefs = inMemoryPrefs()
        val combo = c("DE:250,1020-30")
        prefs.setLastPicked("k", listOf<TrafficSignCombination>().withPicked(combo))
        assertEquals(listOf(combo), prefs.getLastPicked<TrafficSignCombination>("k"))
    }

    @Test fun `applying a combination in both directions writes traffic_sign`() {
        val sel = c("DE:250").toSelection(catalogue)
        val tags = sel.toWaySigns(WaySignSelection.of(emptyMap(), catalogue)).deriveWayTags(emptyMap()).signChanges
        assertEquals(true, tags.any { it is TrafficSignTagChange.Set && it.key == "traffic_sign" && it.value == "DE:250" })
        assertEquals(false, tags.any { it.key.startsWith("traffic_sign:") })
    }
}
