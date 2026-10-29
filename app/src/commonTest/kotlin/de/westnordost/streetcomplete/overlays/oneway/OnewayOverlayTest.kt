package de.westnordost.streetcomplete.overlays.oneway

import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.data.overlays.OverlayStyle.ArrowDirection
import de.westnordost.streetcomplete.data.overlays.OverlayStyle.ArrowShape
import de.westnordost.streetcomplete.osm.oneway.TravelDirection.*
import de.westnordost.streetcomplete.osm.oneway.interpretOneway
import de.westnordost.streetcomplete.overlays.overlaysRegistry
import de.westnordost.streetcomplete.screens.main.map.MapLabel
import de.westnordost.streetcomplete.screens.main.map.layers.StyledElement
import de.westnordost.streetcomplete.screens.main.map.layers.toGeoJsonFeatures
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.maplibre.spatialk.geojson.LineString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnewayOverlayTest {

    private val overlay = OnewayOverlay()
    private fun style(vararg tags: Pair<String, String>) = onewayWayStyle(interpretOneway(mapOf("highway" to "residential") + tags))
    private fun apply(tags: Map<String, String>, changes: StringMapChanges?) = tags.toMutableMap().also { changes!!.applyTo(it) }

    /* ------------------------------------ registration -------------------------------- */

    @Test fun `registered and other overlays keep their ordinals`() {
        val registry = overlaysRegistry({ CountryInfo(null, listOf(IncompleteCountryInfo())) }, { null }, { null }, inMemoryPrefs())
        val oneway = registry.getByName("OnewayOverlay")!!
        assertEquals(EE_QUEST_OFFSET + 5, registry.getOrdinalOf(oneway))
        assertEquals(EE_QUEST_OFFSET + 4, registry.getOrdinalOf(registry.getByName("MaxSpeedOverlay")!!))
        assertEquals(5, registry.getOrdinalOf(registry.getByName("CyclewayOverlay")!!))
    }

    @Test fun `background arrows are hidden only by this overlay`() {
        val registry = overlaysRegistry({ CountryInfo(null, listOf(IncompleteCountryInfo())) }, { null }, { null }, inMemoryPrefs())
        for (o in registry) {
            assertEquals(o is OnewayOverlay, MapLabel.ONEWAY_ARROWS in o.hiddenLabels, o::class.simpleName)
        }
    }

    /* ----------------------------------- selection ------------------------------------ */

    @Test fun `roads without restriction are selectable but invisible`() {
        val road = way(1, tags = mapOf("highway" to "residential"))
        val cycleway = way(2, tags = mapOf("highway" to "cycleway"))
        val bikePath = way(3, tags = mapOf("highway" to "footway", "bicycle" to "designated"))
        val footway = way(4, tags = mapOf("highway" to "footway"))
        val area = way(5, nodes = listOf(1, 2, 3, 1), tags = mapOf("highway" to "pedestrian", "area" to "yes"))
        val styles = overlay.getStyledElements(TestMapDataWithGeometry(listOf(road, cycleway, bikePath, footway, area))).toList()
        assertEquals(listOf(road, cycleway, bikePath), styles.map { it.first })
        val s = styles.first().second as OverlayStyle.Polyline
        assertNull(s.stroke)
        assertNull(s.arrows)
    }

    /* ---------------------------------- classification -------------------------------- */

    @Test fun `general oneway is orange with solid arrows`() {
        val s = style("oneway" to "yes")
        assertEquals(OverlayColor.Orange, s.stroke!!.color)
        assertEquals(OverlayStyle.Arrows(ArrowDirection.FORWARD, OverlayColor.Orange, ArrowShape.SOLID), s.arrows)
        assertEquals(ArrowDirection.BACKWARD, style("oneway" to "-1").arrows!!.direction)
        assertEquals(ArrowDirection.FORWARD, style("junction" to "roundabout").arrows!!.direction)
    }

    @Test fun `bicycle exemption is blue with double arrows`() {
        val s = style("oneway" to "yes", "oneway:bicycle" to "no")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue), s.stroke)
        assertEquals(ArrowDirection.BOTH, s.arrows!!.direction)
    }

    @Test fun `inferred contraflow is blue dashed`() {
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue, dashed = true), style("oneway" to "yes", "cycleway" to "opposite").stroke)
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue, dashed = true),
            style("oneway" to "yes", "cycleway:left" to "lane", "cycleway:left:oneway" to "-1").stroke)
    }

    @Test fun `bicycle only oneway is purple with open arrows`() {
        val s = style("oneway" to "no", "oneway:bicycle" to "yes")
        assertEquals(OverlayColor.Purple, s.stroke!!.color)
        assertEquals(OverlayStyle.Arrows(ArrowDirection.FORWARD, OverlayColor.Purple, ArrowShape.OPEN), s.arrows)
        assertEquals(ArrowDirection.BACKWARD, style("oneway:bicycle" to "-1").arrows!!.direction)
    }

    @Test fun `ordinary oneway without oneway bicycle is not an error`() {
        assertEquals(OnewayCategory.GENERAL_ONEWAY, interpretOneway(mapOf("oneway" to "yes")).category())
    }

    @Test fun `redundant explicit bicycle oneway is a general oneway`() {
        assertEquals(OverlayColor.Orange, style("oneway" to "yes", "oneway:bicycle" to "yes").stroke!!.color)
    }

    @Test fun `no bicycle access is black without arrows`() {
        val s = style("oneway" to "yes", "bicycle" to "no")
        assertEquals(OverlayColor.Black, s.stroke!!.color)
        assertNull(s.arrows)
        assertNull(style("bicycle" to "no").stroke)
    }

    @Test fun `conflicts and invalid values are red without arrows`() {
        for (s in listOf(
            style("oneway" to "reversible"),
            style("oneway" to "yes", "oneway:bicycle" to "yes", "cycleway" to "opposite"),
            style("junction" to "roundabout", "oneway" to "no"),
        )) {
            assertEquals(OverlayColor.Red, s.stroke!!.color)
            assertNull(s.arrows)
        }
    }

    @Test fun `motor vehicle only oneway is not shown as bicycle restriction`() {
        val s = style("oneway:motor_vehicle" to "yes")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue, dashed = true), s.stroke)
        assertEquals(ArrowDirection.BOTH, s.arrows!!.direction)
    }

    @Test fun `circular without oneway is uncertain`() {
        assertEquals(OverlayStyle.Stroke(OnewayUncertainColor, dashed = true), style("junction" to "circular").stroke)
    }

    @Test fun `conditional is marked`() {
        assertEquals("*", style("oneway" to "yes", "oneway:conditional" to "no @ Su").label)
        assertNull(style("oneway" to "yes").label)
    }

    /* ------------------------------------ geojson ------------------------------------- */

    private fun arrowFeature(direction: ArrowDirection, shape: ArrowShape = ArrowShape.SOLID) = StyledElement(
        way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "residential")),
        ElementPolylinesGeometry(listOf(listOf(LatLon(0.0, 0.0), LatLon(1.0, 0.0))), LatLon(0.5, 0.0)),
        OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Orange), arrows = OverlayStyle.Arrows(direction, OverlayColor.Orange, shape)),
    ).toGeoJsonFeatures().single { it.properties.containsKey("arrows") }

    @Test fun `arrows follow way geometry`() {
        val forward = arrowFeature(ArrowDirection.FORWARD)
        assertEquals("solid", forward.properties["arrows"]!!.jsonPrimitive.content)
        assertEquals(90, forward.properties["arrow-rotation"]!!.jsonPrimitive.int)
        // geometry is in node order, never reversed
        val line = forward.geometry as LineString
        assertEquals(0.0, line.coordinates.first().latitude)
        assertEquals(1.0, line.coordinates.last().latitude)

        assertEquals(270, arrowFeature(ArrowDirection.BACKWARD).properties["arrow-rotation"]!!.jsonPrimitive.int)
        assertEquals("double", arrowFeature(ArrowDirection.BOTH).properties["arrows"]!!.jsonPrimitive.content)
        assertEquals("open", arrowFeature(ArrowDirection.FORWARD, ArrowShape.OPEN).properties["arrows"]!!.jsonPrimitive.content)
    }

    @Test fun `no arrow properties without arrows`() {
        val features = StyledElement(
            way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "residential")),
            ElementPolylinesGeometry(listOf(listOf(LatLon(0.0, 0.0), LatLon(1.0, 0.0))), LatLon(0.5, 0.0)),
            OverlayStyle.Polyline(OverlayStyle.Stroke(OverlayColor.Orange)),
        ).toGeoJsonFeatures()
        assertTrue(features.none { it.properties.containsKey("arrows") })
    }

    /* ------------------------------------- editing ------------------------------------ */

    @Test fun `preselection`() {
        assertEquals(OnewayEditState(FORWARD, null), getOnewayEditState(mapOf("oneway" to "yes")))
        assertEquals(OnewayEditState(FORWARD, BOTH), getOnewayEditState(mapOf("oneway" to "yes", "oneway:bicycle" to "no")))
        assertEquals(OnewayEditState(null, null), getOnewayEditState(mapOf("highway" to "residential")))
        // inferred contraflow is not shown as explicit
        assertEquals(OnewayEditState(FORWARD, null), getOnewayEditState(mapOf("oneway" to "yes", "cycleway" to "opposite")))
        // implied roundabout is not shown as explicit
        assertEquals(OnewayEditState(null, null), getOnewayEditState(mapOf("junction" to "roundabout")))
    }

    @Test fun `add oneway`() {
        val tags = mapOf("highway" to "residential")
        assertEquals(tags + ("oneway" to "yes"), apply(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, null))))
    }

    @Test fun `reverse oneway`() {
        val tags = mapOf("oneway" to "yes", "cycleway:left" to "lane")
        assertEquals(mapOf("oneway" to "-1", "cycleway:left" to "lane"), apply(tags, createOnewayTagChanges(tags, OnewayEditState(BACKWARD, null))))
    }

    @Test fun `add bicycle exemption`() {
        val tags = mapOf("oneway" to "yes")
        assertEquals(mapOf("oneway" to "yes", "oneway:bicycle" to "no"), apply(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, BOTH))))
    }

    @Test fun `add bicycle only oneway`() {
        val tags = mapOf("oneway" to "no")
        assertEquals(mapOf("oneway" to "no", "oneway:bicycle" to "yes"), apply(tags, createOnewayTagChanges(tags, OnewayEditState(BOTH, FORWARD))))
        // general oneway is not added if not chosen
        val untagged = mapOf("highway" to "residential")
        assertEquals(untagged + ("oneway:bicycle" to "yes"), apply(untagged, createOnewayTagChanges(untagged, OnewayEditState(null, FORWARD))))
    }

    @Test fun `choosing follow removes bicycle override`() {
        val tags = mapOf("oneway" to "yes", "oneway:bicycle" to "no")
        assertEquals(mapOf("oneway" to "yes"), apply(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, null))))
    }

    @Test fun `no-op creates no edit`() {
        assertNull(createOnewayTagChanges(mapOf("oneway" to "yes"), OnewayEditState(FORWARD, null)))
        assertNull(createOnewayTagChanges(mapOf("oneway" to "1"), OnewayEditState(FORWARD, null)))
        assertNull(createOnewayTagChanges(mapOf("highway" to "residential"), OnewayEditState(null, null)))
    }

    @Test fun `related tags are preserved`() {
        val tags = mapOf(
            "oneway" to "yes",
            "oneway:conditional" to "no @ Su",
            "oneway:bicycle:conditional" to "yes @ Sa",
            "cycleway:left" to "lane", "cycleway:left:oneway" to "-1",
            "bicycle" to "designated", "bicycle:backward" to "yes",
            "vehicle" to "destination", "access" to "yes",
        )
        val result = apply(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, BOTH)))
        assertEquals(tags + ("oneway:bicycle" to "no"), result)
    }

    @Test fun `unsupported combinations are not editable`() {
        assertEquals(OnewayNotEditableReason.UNKNOWN_VALUE, getOnewayNotEditableReason(mapOf("oneway" to "reversible")))
        assertEquals(OnewayNotEditableReason.VEHICLE_SPECIFIC, getOnewayNotEditableReason(mapOf("oneway:motor_vehicle" to "yes")))
        assertNull(getOnewayNotEditableReason(mapOf("oneway" to "yes", "oneway:bicycle" to "no")))
        assertFailsWith<IllegalArgumentException> {
            createOnewayTagChanges(mapOf("oneway" to "alternating"), OnewayEditState(FORWARD, null))
        }
    }

    @Test fun `edit that would conflict with contraflow tags is detected`() {
        val tags = mapOf("oneway" to "yes", "cycleway:left" to "lane", "cycleway:left:oneway" to "-1")
        val changes = createOnewayTagChanges(tags, OnewayEditState(FORWARD, FORWARD))!!
        assertTrue(createsOnewayConflict(tags, changes))
        assertFalse(createsOnewayConflict(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, BOTH))!!))
    }

    @Test fun `undo restores tags`() {
        val tags = mapOf("oneway" to "yes", "oneway:bicycle" to "no")
        val changes = createOnewayTagChanges(tags, OnewayEditState(BACKWARD, null))!!
        val edited = apply(tags, changes)
        assertEquals(tags, edited.toMutableMap().also { changes.reversed().applyTo(it) })
    }

    @Test fun `overlay reflects edit`() {
        val tags = mapOf("highway" to "residential", "oneway" to "yes")
        val edited = apply(tags, createOnewayTagChanges(tags, OnewayEditState(FORWARD, BOTH)))
        val s = overlay.getStyledElements(TestMapDataWithGeometry(listOf(way(1, tags = edited)))).single().second as OverlayStyle.Polyline
        assertEquals(OverlayColor.Blue, s.stroke!!.color)
    }
}
