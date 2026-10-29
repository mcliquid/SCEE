package de.westnordost.streetcomplete.osm.oneway

import de.westnordost.streetcomplete.osm.oneway.TravelDirection.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnewayInterpretationTest {

    private fun i(vararg tags: Pair<String, String>) = interpretOneway(mapOf("highway" to "residential") + tags)

    /* --------------------------------- basic direction -------------------------------- */

    @Test fun `oneway yes is forward`() {
        val r = i("oneway" to "yes")
        assertEquals(FORWARD, r.general)
        assertEquals(FORWARD, r.bicycle)
        assertEquals(BicycleDirectionSource.GENERAL, r.bicycleSource)
    }

    @Test fun `oneway -1 is backward`() {
        assertEquals(BACKWARD, i("oneway" to "-1").general)
        assertEquals(BACKWARD, i("oneway" to "-1").bicycle)
    }

    @Test fun `oneway no and missing are both directions`() {
        assertEquals(BOTH, i("oneway" to "no").general)
        assertEquals(BOTH, i().general)
        assertEquals(BOTH, i().bicycle)
        assertTrue(i().issues.isEmpty())
    }

    @Test fun `equivalent values`() {
        assertEquals(FORWARD, parseOnewayValue("1"))
        assertEquals(FORWARD, parseOnewayValue("true"))
        assertEquals(BOTH, parseOnewayValue("0"))
        assertEquals(BOTH, parseOnewayValue("false"))
        assertNull(parseOnewayValue(null))
    }

    @Test fun `unsupported values`() {
        for (v in listOf("reversible", "alternating", "maybe", "-1;yes")) {
            assertEquals(UNKNOWN, parseOnewayValue(v))
            assertEquals(setOf(OnewayIssue.UNKNOWN_VALUE), i("oneway" to v).issues)
        }
    }

    /* -------------------------------- bicycle direction ------------------------------- */

    @Test fun `oneway with bicycle exemption`() {
        val r = i("oneway" to "yes", "oneway:bicycle" to "no")
        assertEquals(FORWARD, r.general)
        assertEquals(BOTH, r.bicycle)
        assertEquals(BicycleDirectionSource.EXPLICIT, r.bicycleSource)
    }

    @Test fun `reversed oneway with bicycle exemption`() {
        val r = i("oneway" to "-1", "oneway:bicycle" to "no")
        assertEquals(BACKWARD, r.general)
        assertEquals(BOTH, r.bicycle)
    }

    @Test fun `bicycle only oneway`() {
        assertEquals(FORWARD, i("oneway" to "no", "oneway:bicycle" to "yes").bicycle)
        assertEquals(BACKWARD, i("oneway" to "no", "oneway:bicycle" to "-1").bicycle)
        assertEquals(BACKWARD, i("oneway:bicycle" to "-1").bicycle)
        assertTrue(i("oneway" to "no", "oneway:bicycle" to "yes").issues.isEmpty())
    }

    @Test fun `bicycle restriction opposite to general oneway is a conflict`() {
        assertTrue(OnewayIssue.CONFLICT in i("oneway" to "yes", "oneway:bicycle" to "-1").issues)
    }

    @Test fun `bicycle access prohibited`() {
        assertNull(i("oneway" to "yes", "bicycle" to "no").bicycle)
        assertNull(i("oneway" to "yes", "vehicle" to "no").bicycle)
        assertNull(i("oneway" to "yes", "access" to "no").bicycle)
        assertNull(interpretOneway(mapOf("highway" to "motorway", "oneway" to "yes")).bicycle)
        // exemption does not grant access
        assertNull(i("oneway" to "yes", "oneway:bicycle" to "no", "bicycle" to "no").bicycle)
        // more specific permission
        assertEquals(FORWARD, i("oneway" to "yes", "access" to "no", "bicycle" to "yes").bicycle)
    }

    /* -------------------------------- vehicle specific -------------------------------- */

    @Test fun `motor vehicle oneway does not apply to bicycles`() {
        val r = i("oneway:motor_vehicle" to "yes")
        assertEquals(BOTH, r.general)
        assertEquals(FORWARD, r.motorVehicle)
        assertEquals(BOTH, r.bicycle)
    }

    @Test fun `vehicle oneway applies to bicycles`() {
        val r = i("oneway:vehicle" to "yes")
        assertEquals(FORWARD, r.bicycle)
        assertEquals(BicycleDirectionSource.VEHICLE, r.bicycleSource)
        assertEquals(FORWARD, r.motorVehicle)
    }

    @Test fun `bicycle specific overrides vehicle`() {
        assertEquals(BOTH, i("oneway:vehicle" to "yes", "oneway:bicycle" to "no").bicycle)
        assertEquals(BOTH, i("oneway:motor_vehicle" to "yes", "oneway:bicycle" to "no").bicycle)
        assertEquals(FORWARD, i("oneway" to "yes", "oneway:motor_vehicle" to "no").bicycle)
    }

    /* ---------------------------------- roundabouts ----------------------------------- */

    @Test fun `roundabout implies oneway`() {
        val r = i("junction" to "roundabout")
        assertEquals(FORWARD, r.general)
        assertEquals(FORWARD, r.bicycle)
        assertTrue(r.isGeneralImplied)
    }

    @Test fun `roundabout with explicit oneway`() {
        assertEquals(BACKWARD, i("junction" to "roundabout", "oneway" to "-1").general)
        assertFalse(i("junction" to "roundabout", "oneway" to "yes").isGeneralImplied)
        assertEquals(BOTH, i("junction" to "roundabout", "oneway:bicycle" to "no").bicycle)
    }

    @Test fun `circular does not imply oneway`() {
        val r = i("junction" to "circular")
        assertEquals(BOTH, r.general)
        assertEquals(setOf(OnewayIssue.CIRCULAR_WITHOUT_ONEWAY), r.issues)
        assertTrue(i("junction" to "circular", "oneway" to "yes").issues.isEmpty())
    }

    @Test fun `roundabout with oneway no is a conflict`() {
        assertTrue(OnewayIssue.CONFLICT in i("junction" to "roundabout", "oneway" to "no").issues)
    }

    /* ----------------------------------- contraflow ----------------------------------- */

    @Test fun `legacy opposite lane`() {
        val r = i("oneway" to "yes", "cycleway" to "opposite_lane")
        assertEquals(BOTH, r.bicycle)
        assertEquals(BicycleDirectionSource.LEGACY_OPPOSITE, r.bicycleSource)
        assertEquals(BicycleDirectionSource.LEGACY_OPPOSITE, i("oneway" to "yes", "cycleway:left" to "opposite_lane").bicycleSource)
    }

    @Test fun `legacy opposite on non-oneway changes nothing`() {
        assertEquals(BicycleDirectionSource.GENERAL, i("cycleway" to "opposite").bicycleSource)
    }

    @Test fun `contraflow lane is inferred`() {
        val r = i("oneway" to "yes", "cycleway:left" to "lane", "cycleway:left:oneway" to "-1")
        assertEquals(BOTH, r.bicycle)
        assertEquals(BicycleDirectionSource.INFERRED_CONTRAFLOW, r.bicycleSource)
        assertEquals(BicycleDirectionSource.INFERRED_CONTRAFLOW,
            i("oneway" to "-1", "cycleway:right" to "track", "cycleway:right:oneway" to "yes").bicycleSource)
        assertEquals(BicycleDirectionSource.INFERRED_CONTRAFLOW,
            i("oneway" to "yes", "cycleway:both" to "lane", "cycleway:both:oneway" to "no").bicycleSource)
    }

    @Test fun `lane in flow direction or without oneway tag is no contraflow`() {
        assertEquals(FORWARD, i("oneway" to "yes", "cycleway:right" to "lane", "cycleway:right:oneway" to "yes").bicycle)
        assertEquals(FORWARD, i("oneway" to "yes", "cycleway:left" to "lane").bicycle)
    }

    @Test fun `separately mapped cycleway is no contraflow`() {
        assertEquals(FORWARD, i("oneway" to "yes", "cycleway:left" to "separate", "cycleway:left:oneway" to "-1").bicycle)
    }

    @Test fun `bicycle backward against oneway is inferred`() {
        assertEquals(BicycleDirectionSource.INFERRED_CONTRAFLOW, i("oneway" to "yes", "bicycle:backward" to "yes").bicycleSource)
    }

    @Test fun `explicit bicycle oneway contradicting contraflow`() {
        assertTrue(OnewayIssue.CONFLICT in i("oneway" to "yes", "oneway:bicycle" to "yes", "cycleway" to "opposite").issues)
        assertTrue(OnewayIssue.CONFLICT in i("oneway" to "yes", "oneway:bicycle" to "yes",
            "cycleway:left" to "lane", "cycleway:left:oneway" to "-1").issues)
        assertTrue(i("oneway" to "yes", "oneway:bicycle" to "no", "cycleway" to "opposite").issues.isEmpty())
    }

    @Test fun `explicit exemption wins over inference`() {
        assertEquals(BicycleDirectionSource.EXPLICIT,
            i("oneway" to "yes", "oneway:bicycle" to "no", "cycleway:left" to "lane", "cycleway:left:oneway" to "-1").bicycleSource)
    }

    /* ---------------------------------- conditional ----------------------------------- */

    @Test fun `conditional is recognized but not evaluated`() {
        val r = i("oneway" to "yes", "oneway:conditional" to "no @ (Mo-Fr 06:00-09:00)")
        assertTrue(r.isConditional)
        assertEquals(FORWARD, r.bicycle)
        assertTrue(i("oneway:bicycle:conditional" to "yes @ Su").isConditional)
        assertFalse(i("oneway" to "yes").isConditional)
    }
}
