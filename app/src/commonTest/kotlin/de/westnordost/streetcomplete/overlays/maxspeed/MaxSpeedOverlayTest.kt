package de.westnordost.streetcomplete.overlays.maxspeed

import de.westnordost.streetcomplete.ApplicationConstants.EE_QUEST_OFFSET
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.maxspeed.interpretMaxSpeed
import de.westnordost.streetcomplete.overlays.cycleway.CyclewayOverlay
import de.westnordost.streetcomplete.overlays.overlaysRegistry
import de.westnordost.streetcomplete.overlays.smoothness.SmoothnessOverlay
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxSpeedOverlayTest {

    private val countryInfo = CountryInfo(null, listOf(IncompleteCountryInfo(isLeftHandTraffic = false)))
    private val overlay = MaxSpeedOverlay { countryInfo }

    private fun style(vararg tags: Pair<String, String>, leftHandTraffic: Boolean = false) =
        maxSpeedWayStyle(interpretMaxSpeed(mapOf(*tags)), leftHandTraffic)

    /* ------------------------------------ registration -------------------------------- */

    @Test fun `overlay is registered with its ordinal`() {
        val registry = overlaysRegistry({ countryInfo }, { null }, { null }, inMemoryPrefs())
        val maxSpeed = assertIs<MaxSpeedOverlay>(registry.getByName("MaxSpeedOverlay"))
        assertEquals(EE_QUEST_OFFSET + 4, registry.getOrdinalOf(maxSpeed))
        // existing overlays keep their ordinals
        assertEquals(EE_QUEST_OFFSET + 3, registry.getOrdinalOf(registry.getByName("SmoothnessOverlay") as SmoothnessOverlay))
        assertEquals(5, registry.getOrdinalOf(registry.getByName("CyclewayOverlay") as CyclewayOverlay))
    }

    /* ------------------------------------ eligibility --------------------------------- */

    @Test fun `eligible ways`() {
        val residential = way(1, tags = mapOf("highway" to "residential"))
        val service = way(2, tags = mapOf("highway" to "service"))
        val link = way(3, tags = mapOf("highway" to "primary_link"))
        val track = way(4, tags = mapOf("highway" to "track"))
        val trackWithLimit = way(5, tags = mapOf("highway" to "track", "maxspeed" to "30"))
        val footway = way(6, tags = mapOf("highway" to "footway", "maxspeed" to "walk"))
        val area = way(7, nodes = listOf(1, 2, 3, 1), tags = mapOf("highway" to "service", "area" to "yes"))
        val construction = way(8, tags = mapOf("highway" to "construction", "maxspeed" to "50"))
        val pedestrianWithZone = way(9, tags = mapOf("highway" to "pedestrian", "zone:maxspeed" to "DE:20"))
        val mapData = TestMapDataWithGeometry(listOf(residential, service, link, track, trackWithLimit, footway, area, construction, pedestrianWithZone))
        assertEquals(
            listOf(residential, service, link, trackWithLimit, pedestrianWithZone),
            overlay.getStyledElements(mapData).map { it.first }.toList()
        )
    }

    /* --------------------------------- classification -------------------------------- */

    @Test fun `numeric colors`() {
        assertEquals(OverlayColor.Purple, style("maxspeed" to "20").stroke!!.color)
        assertEquals(OverlayColor.Blue, style("maxspeed" to "30").stroke!!.color)
        assertEquals(OverlayColor.Aquamarine, style("maxspeed" to "50").stroke!!.color)
        assertEquals(OverlayColor.Lime, style("maxspeed" to "70").stroke!!.color)
        assertEquals(OverlayColor.Gold, style("maxspeed" to "100").stroke!!.color)
        assertEquals(OverlayColor.Orange, style("maxspeed" to "130").stroke!!.color)
        assertEquals(false, style("maxspeed" to "130").stroke!!.dashed)
    }

    @Test fun `mph is converted for color but not for the label`() {
        val s = style("maxspeed" to "30 mph")
        assertEquals(OverlayColor.Aquamarine, s.stroke!!.color) // 48 km/h
        assertEquals("30 mph", s.label)
        assertEquals(OverlayColor.Lime, style("maxspeed" to "40 mph").stroke!!.color) // 64 km/h
    }

    @Test fun `labels are actual values`() {
        assertEquals("50", style("maxspeed" to "50").label)
        assertEquals("50", style("maxspeed" to "50 km/h").label)
        assertEquals("70", style("maxspeed" to "70").label)
    }

    @Test fun `missing is neutral gray without label, also on service roads`() {
        for (highway in listOf("residential", "service", "primary")) {
            val s = style("highway" to highway)
            assertEquals(OverlayStyle.Stroke(MaxSpeedMissingColor), s.stroke)
            assertNull(s.label)
        }
    }

    @Test fun `none is orange dashed with infinity`() {
        val s = style("maxspeed" to "none")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Orange, dashed = true), s.stroke)
        assertEquals("∞", s.label)
    }

    @Test fun `walk`() {
        val s = style("maxspeed" to "walk")
        assertEquals(OverlayColor.Purple, s.stroke!!.color)
        assertEquals("walk", s.label)
    }

    @Test fun `implicit codes are dashed gray and keep their code`() {
        val s = style("maxspeed" to "DE:urban")
        assertEquals(OverlayStyle.Stroke(MaxSpeedMissingColor, dashed = true), s.stroke)
        assertEquals("DE:urban", s.label)
        assertEquals("DE:rural", style("maxspeed:type" to "DE:rural").label)
    }

    @Test fun `zone code is dashed in speed color, distinct from explicit`() {
        val zone = style("maxspeed" to "DE:zone30")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue, dashed = true), zone.stroke)
        assertEquals("Z30", zone.label)
        val explicit = style("maxspeed" to "30", "maxspeed:type" to "DE:zone30")
        assertEquals(OverlayStyle.Stroke(OverlayColor.Blue), explicit.stroke)
        assertEquals("30", explicit.label)
    }

    @Test fun `variable is teal dashed`() {
        assertEquals(OverlayStyle.Stroke(OverlayColor.Teal, dashed = true), style("maxspeed" to "signals").stroke)
    }

    @Test fun `invalid and contradictory are red`() {
        assertEquals(OverlayColor.Red, style("maxspeed" to "fast").stroke!!.color)
        assertEquals("! fast", style("maxspeed" to "fast").label)
        assertEquals(OverlayColor.Red, style("maxspeed" to "50", "maxspeed:type" to "DE:zone30").stroke!!.color)
    }

    @Test fun `unknown plausible code is not red`() {
        assertEquals(MaxSpeedMissingColor, style("maxspeed" to "XY:special").stroke!!.color)
    }

    /* ---------------------------------- conditional ----------------------------------- */

    @Test fun `conditional is marked with star`() {
        val s = style("maxspeed" to "50", "maxspeed:conditional" to "30 @ (Mo-Fr 07:00-17:00)")
        assertEquals(OverlayColor.Aquamarine, s.stroke!!.color)
        assertEquals("50*", s.label)
    }

    @Test fun `conditional only gets a distinct marker and no number`() {
        val s = style("maxspeed:conditional" to "30 @ (22:00-06:00)")
        assertEquals("(*)", s.label)
        assertEquals(MaxSpeedMissingColor, s.stroke!!.color)
    }

    /* ----------------------------------- advisory ------------------------------------- */

    @Test fun `advisory only is not rendered like a binding limit`() {
        val advisory = style("highway" to "primary", "maxspeed:advisory" to "70")
        val binding = style("highway" to "primary", "maxspeed" to "70")
        assertEquals(OverlayStyle.Stroke(MaxSpeedMissingColor), advisory.stroke)
        assertEquals("(70)", advisory.label)
        assertTrue(advisory != binding)
    }

    @Test fun `binding plus advisory`() {
        val s = style("maxspeed" to "100", "maxspeed:advisory" to "70")
        assertEquals(OverlayColor.Gold, s.stroke!!.color)
        assertEquals("100 (70)", s.label)
    }

    /* ---------------------------------- directional ----------------------------------- */

    @Test fun `directional values are on sides, forward on the right in right hand traffic`() {
        val s = style("maxspeed:forward" to "70", "maxspeed:backward" to "50")
        assertNull(s.stroke)
        assertEquals(OverlayColor.Lime, s.strokeRight!!.color)
        assertEquals(OverlayColor.Aquamarine, s.strokeLeft!!.color)
        assertEquals("→70 ←50", s.label)
    }

    @Test fun `forward on the left in left hand traffic`() {
        val s = style("maxspeed:forward" to "70", "maxspeed:backward" to "50", leftHandTraffic = true)
        assertEquals(OverlayColor.Lime, s.strokeLeft!!.color)
        assertEquals(OverlayColor.Aquamarine, s.strokeRight!!.color)
    }

    @Test fun `general with override`() {
        val s = style("maxspeed" to "50", "maxspeed:forward" to "30")
        assertEquals(OverlayColor.Blue, s.strokeRight!!.color)
        assertEquals(OverlayColor.Aquamarine, s.strokeLeft!!.color)
        assertEquals("→30 ←50", s.label)
    }

    @Test fun `missing direction stays unknown`() {
        val s = style("maxspeed:forward" to "70")
        assertEquals(OverlayColor.Lime, s.strokeRight!!.color)
        assertEquals(MaxSpeedMissingColor, s.strokeLeft!!.color)
        assertEquals("→70 ←?", s.label)
    }

    @Test fun `oneway does not swap sides`() {
        assertEquals(
            style("maxspeed:forward" to "70", "maxspeed:backward" to "50"),
            style("maxspeed:forward" to "70", "maxspeed:backward" to "50", "oneway" to "-1"),
        )
    }

    @Test fun `directional conditional is marked only on its direction`() {
        val s = style("maxspeed:forward" to "70", "maxspeed:backward" to "50", "maxspeed:backward:conditional" to "30 @ wet")
        assertEquals("→70 ←50*", s.label)
    }

    @Test fun `overlay uses country for directional sides`() {
        val lht = MaxSpeedOverlay { CountryInfo(null, listOf(IncompleteCountryInfo(isLeftHandTraffic = true))) }
        val road = way(1, nodes = listOf(1, 2), tags = mapOf("highway" to "primary", "maxspeed:forward" to "70", "maxspeed:backward" to "50"))
        val mapData = TestMapDataWithGeometry(listOf(road))
        val s = assertIs<OverlayStyle.Polyline>(lht.getStyledElements(mapData).single().second)
        // without geometry in the test map data, the country cannot be determined -> right hand traffic default
        val expected = if (mapData.getWayGeometry(1) != null) OverlayColor.Lime else OverlayColor.Aquamarine
        assertEquals(expected, s.strokeLeft!!.color)
    }

    /* ---------------------------------- tag updates ----------------------------------- */

    @Test fun `style follows updated tags`() {
        val before = way(1, tags = mapOf("highway" to "residential"))
        val after = way(1, tags = mapOf("highway" to "residential", "maxspeed" to "30"))
        val s1 = overlay.getStyledElements(TestMapDataWithGeometry(listOf(before))).single().second
        val s2 = overlay.getStyledElements(TestMapDataWithGeometry(listOf(after))).single().second
        assertEquals(MaxSpeedMissingColor, (s1 as OverlayStyle.Polyline).stroke!!.color)
        assertEquals(OverlayColor.Blue, (s2 as OverlayStyle.Polyline).stroke!!.color)
    }
}
