package de.westnordost.streetcomplete.screens.main.bottom_sheet.quest

import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.WaterwayCrossingTestData
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import dev.mokkery.answering.calls
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OsmQuestFormContainerSplitWayTest {
    private val f = WaterwayCrossingTestData()

    @Test fun `split way resolves polylines from map when quest geometry is a point pin`() {
        val point = ElementPointGeometry(f.crossing.position)
        val source = mock<MapDataWithEditsSource> {
            every { getGeometry(any(), any()) } calls { (type: ElementType, id: Long) ->
                f.data.getGeometry(type, id)
            }
        }
        val resolved = wayPolylinesGeometryForSplit(f.road, point, source)
        assertNotNull(resolved)
        assertIs<ElementPolylinesGeometry>(resolved)
    }

    @Test fun `split way keeps explicit polyline geometry`() {
        val polyline = ElementGeometryCreator().create(f.road, f.data) as ElementPolylinesGeometry
        val source = mock<MapDataWithEditsSource>()
        assertEquals(polyline, wayPolylinesGeometryForSplit(f.road, polyline, source))
    }

    @Test fun `split way returns null for non way elements`() {
        val node = f.data.getNode(1)!!
        val source = mock<MapDataWithEditsSource>()
        assertNull(wayPolylinesGeometryForSplit(node, ElementPointGeometry(f.crossing.position), source))
    }
}
