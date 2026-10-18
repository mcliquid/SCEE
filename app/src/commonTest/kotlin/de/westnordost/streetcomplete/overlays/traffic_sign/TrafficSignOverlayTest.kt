package de.westnordost.streetcomplete.overlays.traffic_sign

import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDerivation
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignNotDerived
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagState
import de.westnordost.streetcomplete.osm.traffic_sign.trafficSignTagState
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTagConflict
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.parseDE
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TrafficSignOverlayTest {

    /* ------------------------------------- style ------------------------------------- */

    private fun style(vararg tags: Pair<String, String>) = trafficSignWayStyle(mapOf(*tags)).stroke!!

    @Test fun `nothing surveyed is red`() {
        assertEquals(OverlayStyle.Stroke(OverlayColor.Red), style("highway" to "path"))
    }

    @Test fun `one direction surveyed is orange and dashed`() {
        val expected = OverlayStyle.Stroke(OverlayColor.Orange, dashed = true)
        assertEquals(expected, style("traffic_sign:forward" to "DE:237"))
        assertEquals(expected, style("traffic_sign:backward" to "none"))
    }

    @Test fun `both directions with signs are green`() {
        assertEquals(OverlayStyle.Stroke(OverlayColor.Lime), style("traffic_sign" to "DE:237"))
        assertEquals(
            OverlayStyle.Stroke(OverlayColor.Lime),
            style("traffic_sign:forward" to "DE:237", "traffic_sign:backward" to "DE:239")
        )
    }

    @Test fun `both directions none are gray`() {
        assertEquals(OverlayStyle.Stroke(NoSignsColor), style("traffic_sign" to "none"))
        assertEquals(
            OverlayStyle.Stroke(NoSignsColor),
            style("traffic_sign:forward" to "none", "traffic_sign:backward" to "none")
        )
    }

    @Test fun `signs and none mixed count as surveyed and have their own color`() {
        val mixed = style("traffic_sign:forward" to "DE:237", "traffic_sign:backward" to "none")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Teal), mixed)
        assertNotEquals(OverlayStyle.Stroke(OverlayColor.Lime), mixed)
        assertNotEquals(OverlayStyle.Stroke(NoSignsColor), mixed)
        assertEquals(mixed, style("traffic_sign:forward" to "none", "traffic_sign:backward" to "DE:237"))
    }

    @Test fun `not surveyed and none are different states`() {
        assertEquals(
            TrafficSignTagState.NOT_SURVEYED,
            trafficSignTagState(emptyMap(), TrafficSignDirection.FORWARD)
        )
        assertEquals(
            TrafficSignTagState.NONE,
            trafficSignTagState(mapOf("traffic_sign" to "none"), TrafficSignDirection.FORWARD)
        )
    }

    @Test fun `directional value wins over general value`() {
        val tags = mapOf("traffic_sign" to "none", "traffic_sign:forward" to "DE:237")
        assertEquals(TrafficSignTagState.SIGNED, trafficSignTagState(tags, TrafficSignDirection.FORWARD))
        assertEquals(TrafficSignTagState.NONE, trafficSignTagState(tags, TrafficSignDirection.BACKWARD))
    }

    @Test fun `unreadable values count as signed, blank as not surveyed`() {
        assertEquals(
            TrafficSignTagState.SIGNED,
            trafficSignTagState(mapOf("traffic_sign" to "AT:52"), TrafficSignDirection.BACKWARD)
        )
        assertEquals(
            TrafficSignTagState.NOT_SURVEYED,
            trafficSignTagState(mapOf("traffic_sign" to " "), TrafficSignDirection.BACKWARD)
        )
    }

    /* ---------------------------------- element selection ---------------------------------- */

    // Germany: lon < 10, Poland: lon >= 10
    private val overlay = TrafficSignOverlay { if (it.longitude < 10.0) "DE" else "PL" }

    private fun line(vararg points: LatLon) =
        ElementPolylinesGeometry(listOf(points.toList()), points[points.size / 2])

    private fun styled(vararg ways: Pair<de.westnordost.streetcomplete.data.osm.mapdata.Way, ElementPolylinesGeometry?>) =
        overlay.getStyledElements(createMapData(ways.toMap())).map { it.first.id }.toList()

    private val de1 = LatLon(50.0, 9.0)
    private val de2 = LatLon(50.0, 9.001)
    private val pl = LatLon(50.0, 10.5)

    @Test fun `ways in Germany are shown`() {
        val result = styled(
            way(1, tags = mapOf("highway" to "residential")) to line(de1, de2),
            way(2, tags = mapOf("highway" to "footway")) to line(de1, de2),
        )
        assertEquals(listOf(1L, 2L), result.sorted())
    }

    @Test fun `ways outside of Germany are not shown`() {
        assertEquals(
            emptyList(),
            styled(way(1, tags = mapOf("highway" to "residential")) to line(pl, LatLon(50.0, 10.6)))
        )
    }

    @Test fun `ways that cross the border are not shown`() {
        assertEquals(
            emptyList(),
            styled(way(1, tags = mapOf("highway" to "residential")) to line(de1, de2, pl))
        )
        assertEquals(
            emptyList(),
            styled(way(1, tags = mapOf("highway" to "residential")) to line(pl, de1, de2))
        )
    }

    @Test fun `ways without geometry or highway or areas are not shown`() {
        assertEquals(
            emptyList(),
            styled(
                way(1, tags = mapOf("highway" to "residential")) to null,
                way(2, tags = mapOf("building" to "yes")) to line(de1, de2),
                way(3, tags = mapOf("highway" to "pedestrian", "area" to "yes")) to line(de1, de2),
                way(4, tags = mapOf("highway" to "bus_stop")) to line(de1, de2),
            )
        )
    }

    @Test fun `styled way has the style of its tags`() {
        val w = way(1, tags = mapOf("highway" to "path", "traffic_sign" to "none"))
        val (_, s) = overlay.getStyledElements(createMapData(mapOf(w to line(de1, de2)))).single()
        assertEquals(OverlayStyle.Polyline(OverlayStyle.Stroke(NoSignsColor)), s)
    }

    /* ----------------------------------- preview texts ----------------------------------- */

    @Test fun `tag keys are described in words`() {
        assertEquals(PreviewSubject.BICYCLE, previewSubjectOf("bicycle"))
        assertEquals(PreviewSubject.BICYCLE, previewSubjectOf("bicycle:backward"))
        assertEquals(PreviewSubject.MOTOR, previewSubjectOf("hgv:forward:conditional"))
        assertEquals(PreviewSubject.FOOT, previewSubjectOf("foot"))
        assertEquals(PreviewSubject.SEGREGATION, previewSubjectOf("segregated"))
        assertEquals(PreviewSubject.PART_DETAILS, previewSubjectOf("footway:surface"))
        assertEquals(PreviewSubject.SIGN_ENTRY, previewSubjectOf("traffic_sign:forward"))
        assertEquals(PreviewSubject.OTHER, previewSubjectOf("some_unknown_key"))
    }

    @Test fun `every reason has a text`() {
        // the texts are chosen with exhaustive when expressions; this makes sure the enums are not
        // empty and that a new reason would be noticed in review of this test
        assertEquals(9, TrafficSignTagConflict.Reason.entries.size)
        assertEquals(14, TrafficSignNotDerived.Reason.entries.size)
    }

    @Test fun `preview model of the example does not contain tags`() {
        val tags = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val tagging = TrafficSignWaySigns(parseDE("DE:240"), parseDE("DE:239,1022-10")).deriveWayTags(tags)
        val model = tagging.toPreviewModel()
        assertEquals(TrafficSignDerivation.FULL, model.derivation)
        assertEquals("path" to "footway", model.highwayChange)
        assertTrue(model.adjusted.any { it.subject == PreviewSubject.BICYCLE && it.direction == TrafficSignDirection.BACKWARD })
        // the model holds subjects and reasons only, never tag keys or values
        assertTrue(model.adjusted.all { it.subject.name == it.subject.name.uppercase() })
    }

    @Test fun `unresolvable conflicts are reported separately`() {
        val tags = mapOf("highway" to "residential", "hgv" to "delivery")
        val tagging = TrafficSignWaySigns.both(parseDE("DE:250,1020-30")).deriveWayTags(tags)
        val model = tagging.toPreviewModel()
        assertTrue(model.needsCheck.any { it.reason == TrafficSignTagConflict.Reason.UNCLEAR_ACCESS && it.subject == PreviewSubject.MOTOR })
    }

    @Test fun `unusual combination is saved and explained`() {
        val tags = mapOf("highway" to "residential")
        val tagging = TrafficSignWaySigns.both(parseDE("DE:237;250;274[30]")).deriveWayTags(tags)
        assertEquals(TrafficSignDerivation.TRAFFIC_SIGN_ONLY, tagging.derivation)
        assertTrue(tagging.changes.isNotEmpty())
        assertTrue(tagging.toPreviewModel().notDerived.isNotEmpty())
    }

    @Test fun `catalogue is available for the tests`() {
        assertEquals("DE", TrafficSignTestData.catalogueDE.country)
    }
}
