package de.westnordost.streetcomplete.overlays.maxspeed

import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.meta.IncompleteCountryInfo
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit.KILOMETERS_PER_HOUR
import de.westnordost.streetcomplete.data.meta.SpeedMeasurementUnit.MILES_PER_HOUR
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.overlays.OverlayColor
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.maxspeed.MaxSpeedUnit
import de.westnordost.streetcomplete.osm.maxspeed.Speed
import de.westnordost.streetcomplete.osm.maxspeed.interpretMaxSpeed
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxSpeedOverlayEditTest {

    private fun kmh(v: Int?) = Speed(v, KILOMETERS_PER_HOUR)
    private fun general(speed: Speed?) = MaxSpeedEditState.General(speed)
    private fun apply(tags: Map<String, String>, changes: StringMapChanges?): Map<String, String> =
        tags.toMutableMap().also { changes!!.applyTo(it) }

    /* ------------------------------------- state ------------------------------------- */

    @Test fun `existing value is preselected`() {
        assertEquals(MaxSpeedEditState.General(kmh(50)), getMaxSpeedEditState(mapOf("highway" to "residential", "maxspeed" to "50")))
    }

    @Test fun `missing value is empty, not inferred from highway`() {
        for (highway in listOf("residential", "primary", "service", "motorway")) {
            assertEquals(MaxSpeedEditState.General(null), getMaxSpeedEditState(mapOf("highway" to highway)))
        }
    }

    @Test fun `mph is kept as mph`() {
        assertEquals(MaxSpeedEditState.General(Speed(30, MILES_PER_HOUR)), getMaxSpeedEditState(mapOf("maxspeed" to "30 mph")))
    }

    @Test fun `special values are not editable`() {
        for (value in listOf("none", "walk", "DE:urban", "DE:rural", "DE:zone30", "signals", "fast")) {
            val state = assertIs<MaxSpeedEditState.NotEditable>(getMaxSpeedEditState(mapOf("maxspeed" to value)), value)
            assertEquals(MaxSpeedEditState.Reason.SPECIAL_VALUE, state.reason)
        }
        assertIs<MaxSpeedEditState.NotEditable>(getMaxSpeedEditState(mapOf("maxspeed:forward" to "none", "maxspeed:backward" to "50")))
    }

    @Test fun `unsupported unit and lanes are not editable`() {
        assertEquals(MaxSpeedEditState.Reason.UNSUPPORTED_UNIT,
            assertIs<MaxSpeedEditState.NotEditable>(getMaxSpeedEditState(mapOf("maxspeed" to "10 knots"))).reason)
        assertEquals(MaxSpeedEditState.Reason.LANES,
            assertIs<MaxSpeedEditState.NotEditable>(getMaxSpeedEditState(mapOf("maxspeed" to "100", "maxspeed:lanes" to "100|80"))).reason)
    }

    @Test fun `not editable states can not produce edits`() {
        assertFailsWith<IllegalArgumentException> {
            createMaxSpeedTagChanges(mapOf("maxspeed" to "none"), MaxSpeedEditState.General(kmh(30)))
        }
    }

    @Test fun `directional state`() {
        assertEquals(
            MaxSpeedEditState.Directional(kmh(70), kmh(50)),
            getMaxSpeedEditState(mapOf("maxspeed:forward" to "70", "maxspeed:backward" to "50"))
        )
        assertEquals(
            MaxSpeedEditState.Directional(kmh(30), kmh(50)),
            getMaxSpeedEditState(mapOf("maxspeed" to "50", "maxspeed:forward" to "30"))
        )
        assertEquals(
            MaxSpeedEditState.Directional(kmh(70), null),
            getMaxSpeedEditState(mapOf("maxspeed:forward" to "70"))
        )
    }

    /* -------------------------------------- edits ------------------------------------- */

    @Test fun `change 50 to 30`() {
        val tags = mapOf("highway" to "residential", "maxspeed" to "50")
        val changes = createMaxSpeedTagChanges(tags, general(kmh(30)))
        assertEquals(mapOf("highway" to "residential", "maxspeed" to "30"), apply(tags, changes))
    }

    @Test fun `other changes`() {
        for ((from, to) in listOf(70 to 50, 100 to 80)) {
            val tags = mapOf("maxspeed" to "$from")
            assertEquals(mapOf("maxspeed" to "$to"), apply(tags, createMaxSpeedTagChanges(tags, general(kmh(to)))))
        }
    }

    @Test fun `add missing value`() {
        val tags = mapOf("highway" to "residential")
        assertEquals(mapOf("highway" to "residential", "maxspeed" to "30"), apply(tags, createMaxSpeedTagChanges(tags, general(kmh(30)))))
    }

    @Test fun `unchanged selection produces no edit`() {
        val tags = mapOf("maxspeed" to "50")
        assertNull(createMaxSpeedTagChanges(tags, general(kmh(50))))
        assertNull(createMaxSpeedTagChanges(mapOf("maxspeed" to "50 km/h"), general(kmh(50))))
        assertNull(createMaxSpeedTagChanges(mapOf("highway" to "residential"), general(null)))
    }

    @Test fun `incomplete value is not accepted`() {
        assertFalse(isMaxSpeedEditComplete(general(null), general(kmh(null))))
        assertTrue(isMaxSpeedEditComplete(general(kmh(50)), general(kmh(30))))
    }

    @Test fun `mph stays mph when changing the number`() {
        val tags = mapOf("maxspeed" to "30 mph")
        assertEquals(mapOf("maxspeed" to "25 mph"), apply(tags, createMaxSpeedTagChanges(tags, general(Speed(25, MILES_PER_HOUR)))))
    }

    @Test fun `implicit type codes are protected, the form is not editable`() {
        val withCode = listOf(
            mapOf("maxspeed:type" to "DE:urban"),
            mapOf("source:maxspeed" to "DE:urban"),
            mapOf("maxspeed" to "50", "maxspeed:type" to "DE:urban"),
            mapOf("maxspeed" to "50", "source:maxspeed" to "DE:urban"),
            mapOf("maxspeed" to "30", "maxspeed:type" to "DE:zone30"),
            mapOf("maxspeed:forward" to "50", "maxspeed:backward" to "30", "maxspeed:type" to "DE:urban"),
        )
        for (tags in withCode) {
            assertEquals(MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.IMPLICIT_TYPE), getMaxSpeedEditState(tags), "$tags")
            // so no numeric change can remove the code, nor add a `sign`
            assertFailsWith<IllegalArgumentException>("$tags") { createMaxSpeedTagChanges(tags, general(kmh(20))) }
            assertNull(createMaxSpeedTagChanges(tags, getMaxSpeedEditState(tags)), "$tags")
        }
    }

    @Test fun `sign and other non code values stay editable and are kept, no sign is invented`() {
        val sign = mapOf("maxspeed" to "50", "maxspeed:type" to "sign")
        assertEquals(mapOf("maxspeed" to "30", "maxspeed:type" to "sign"), apply(sign, createMaxSpeedTagChanges(sign, general(kmh(30)))))
        val plain = mapOf("highway" to "residential", "maxspeed" to "50")
        assertEquals(mapOf("highway" to "residential", "maxspeed" to "30"), apply(plain, createMaxSpeedTagChanges(plain, general(kmh(30)))))
        val missing = mapOf("highway" to "residential")
        assertEquals(mapOf("highway" to "residential", "maxspeed" to "30"), apply(missing, createMaxSpeedTagChanges(missing, general(kmh(30)))))
    }

    /* ------------------------------------ zones --------------------------------------- */

    @Test fun `lower local limit keeps zone membership`() {
        val tags = mapOf("highway" to "residential", "maxspeed" to "30", "zone:maxspeed" to "DE:30")
        val edited = apply(tags, createMaxSpeedTagChanges(tags, general(kmh(20))))
        assertEquals(mapOf("highway" to "residential", "maxspeed" to "20", "zone:maxspeed" to "DE:30"), edited)
        // and the result is not shown as contradictory
        val style = maxSpeedWayStyle(interpretMaxSpeed(edited), false) { MaxSpeedUnit.KMH }
        assertEquals(OverlayStyle.Stroke(OverlayColor.Purple), style.stroke)
        assertEquals("20", style.label)
    }

    @Test fun `zone traffic and non numeric zone maxspeed are kept`() {
        val tags = mapOf("maxspeed" to "50", "zone:traffic" to "DE:urban", "zone:maxspeed" to "DE:urban")
        assertEquals(
            mapOf("maxspeed" to "30", "zone:traffic" to "DE:urban", "zone:maxspeed" to "DE:urban"),
            apply(tags, createMaxSpeedTagChanges(tags, general(kmh(30))))
        )
    }

    @Test fun `higher limit or none with zone membership is shown neutrally and stays editable`() {
        val higher = mapOf("maxspeed" to "50", "zone:maxspeed" to "DE:30")
        assertEquals(general(kmh(50)), getMaxSpeedEditState(higher))
        val style = maxSpeedWayStyle(interpretMaxSpeed(higher), false) { MaxSpeedUnit.KMH }
        assertEquals(OverlayStyle.Stroke(OverlayColor.Aquamarine), style.stroke)
        assertEquals("50", style.label)
        val none = maxSpeedWayStyle(interpretMaxSpeed(mapOf("maxspeed" to "none", "zone:maxspeed" to "DE:30")), false) { MaxSpeedUnit.KMH }
        assertEquals(OverlayStyle.Stroke(OverlayColor.Orange, dashed = true), none.stroke)
    }

    @Test fun `free text in source maxspeed is kept`() {
        val tags = mapOf("maxspeed" to "50", "source:maxspeed" to "https://portal-mainroads.opendata.arcgis.com/datasets")
        assertEquals(tags + ("maxspeed" to "60"), apply(tags, createMaxSpeedTagChanges(tags, general(kmh(60)))))
    }

    /* --------------------------------- type conflicts --------------------------------- */

    @Test fun `contradicting type keys are not editable`() {
        val tags = mapOf("maxspeed" to "50", "maxspeed:type" to "PL:urban", "source:maxspeed" to "PL:rural")
        assertEquals(MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.TYPE_CONFLICT), getMaxSpeedEditState(tags))
        assertFailsWith<IllegalArgumentException> { createMaxSpeedTagChanges(tags, general(kmh(30))) }
    }

    @Test fun `equivalent type keys are no conflict`() {
        val tags = mapOf("maxspeed" to "30", "maxspeed:type" to "DE:zone30", "source:maxspeed" to "DE:zone:30")
        // protected because of the implicit type, but not reported as conflict
        assertEquals(MaxSpeedEditState.NotEditable(MaxSpeedEditState.Reason.IMPLICIT_TYPE), getMaxSpeedEditState(tags))
    }

    /* ------------------------------------ preview ------------------------------------- */

    @Test fun `preview is exactly the saved diff plus the untouched related tags`() {
        val tags = mapOf(
            "highway" to "residential",
            "maxspeed" to "50",
            "maxspeed:type" to "sign",
            "maxspeed:conditional" to "30 @ (22:00-06:00)",
            "source:maxspeed:advisory" to "sign",
            "zone:traffic" to "DE:urban",
            "zone:maxspeed" to "DE:30",
            "name" to "x",
        )
        val changes = createMaxSpeedTagChanges(tags, general(kmh(30)))!!
        val preview = createMaxSpeedTagPreview(tags, changes)
        assertEquals(changes.changes, preview.changes.toSet())
        assertEquals(listOf<StringMapEntryChange>(StringMapEntryModify("maxspeed", "50", "30")), preview.changes)
        assertEquals(
            listOf(
                "maxspeed:conditional" to "30 @ (22:00-06:00)",
                "maxspeed:type" to "sign",
                "source:maxspeed:advisory" to "sign",
                "zone:maxspeed" to "DE:30",
                "zone:traffic" to "DE:urban",
            ),
            preview.unchanged
        )
        // no tag is both changed and unchanged, and the unchanged ones really are unchanged
        val changedKeys = preview.changes.map { it.key }.toSet()
        assertTrue(preview.unchanged.none { it.first in changedKeys })
        val edited = apply(tags, changes)
        for ((key, value) in preview.unchanged) assertEquals(value, edited[key])
    }

    @Test fun `type code is shown before anything is changed`() {
        val tags = mapOf("highway" to "residential", "maxspeed:type" to "DE:urban", "source:maxspeed" to "survey")
        val preview = createMaxSpeedTagPreview(tags, createMaxSpeedTagChanges(tags, getMaxSpeedEditState(tags)))
        assertTrue(preview.changes.isEmpty())
        assertEquals(listOf("maxspeed:type" to "DE:urban", "source:maxspeed" to "survey"), preview.unchanged)
    }

    @Test fun `added value is shown as addition`() {
        val tags = mapOf("highway" to "residential", "zone:maxspeed" to "DE:30")
        val preview = createMaxSpeedTagPreview(tags, createMaxSpeedTagChanges(tags, general(kmh(20))))
        assertEquals(listOf<StringMapEntryChange>(StringMapEntryAdd("maxspeed", "20")), preview.changes)
        assertEquals(listOf("zone:maxspeed" to "DE:30"), preview.unchanged)
    }

    @Test fun `no-op and cancel produce no changes`() {
        val tags = mapOf("maxspeed" to "30", "maxspeed:type" to "sign", "zone:maxspeed" to "DE:30")
        val unchanged = createMaxSpeedTagChanges(tags, getMaxSpeedEditState(tags))
        assertNull(unchanged)
        val preview = createMaxSpeedTagPreview(tags, unchanged)
        assertTrue(preview.changes.isEmpty())
        assertEquals(tags.toList().sortedBy { it.first }, preview.unchanged)
        // changing the value and going back to the original value is no change either
        assertNull(createMaxSpeedTagChanges(tags, general(kmh(30))))
    }

    @Test fun `forward and backward are edited separately`() {
        val tags = mapOf("maxspeed:forward" to "70", "maxspeed:backward" to "50")
        val changes = createMaxSpeedTagChanges(tags, MaxSpeedEditState.Directional(kmh(80), kmh(50)))
        assertEquals(mapOf("maxspeed:forward" to "80", "maxspeed:backward" to "50"), apply(tags, changes))
    }

    @Test fun `missing direction can be added and stays missing otherwise`() {
        val tags = mapOf("maxspeed:forward" to "70")
        assertEquals(
            mapOf("maxspeed:forward" to "70", "maxspeed:backward" to "50"),
            apply(tags, createMaxSpeedTagChanges(tags, MaxSpeedEditState.Directional(kmh(70), kmh(50))))
        )
        assertEquals(
            mapOf("maxspeed:forward" to "60"),
            apply(tags, createMaxSpeedTagChanges(tags, MaxSpeedEditState.Directional(kmh(60), null)))
        )
        assertTrue(isMaxSpeedEditComplete(MaxSpeedEditState.Directional(kmh(70), null), MaxSpeedEditState.Directional(kmh(60), null)))
    }

    @Test fun `general limit with directional override keeps the override`() {
        val tags = mapOf("maxspeed" to "50", "maxspeed:forward" to "30")
        // editing the override
        assertEquals(
            mapOf("maxspeed" to "50", "maxspeed:forward" to "20"),
            apply(tags, createMaxSpeedTagChanges(tags, MaxSpeedEditState.Directional(kmh(20), kmh(50))))
        )
        // editing the direction that uses the general value edits the general value
        assertEquals(
            mapOf("maxspeed" to "60", "maxspeed:forward" to "30"),
            apply(tags, createMaxSpeedTagChanges(tags, MaxSpeedEditState.Directional(kmh(30), kmh(60))))
        )
    }

    @Test fun `directional ways are never collapsed into a general value`() {
        assertFailsWith<IllegalArgumentException> {
            createMaxSpeedTagChanges(mapOf("maxspeed:forward" to "70", "maxspeed:backward" to "50"), general(kmh(50)))
        }
    }

    @Test fun `oneway does not swap directions`() {
        val tags = mapOf("oneway" to "-1", "maxspeed:forward" to "70", "maxspeed:backward" to "50")
        assertEquals(MaxSpeedEditState.Directional(kmh(70), kmh(50)), getMaxSpeedEditState(tags))
    }

    @Test fun `conditional and advisory are preserved`() {
        val tags = mapOf(
            "maxspeed" to "50",
            "maxspeed:conditional" to "30 @ (Mo-Fr 07:00-17:00)",
            "maxspeed:forward:conditional" to "30 @ wet",
            "maxspeed:advisory" to "40",
            "maxspeed:hgv" to "40",
        )
        val result = apply(tags, createMaxSpeedTagChanges(tags, general(kmh(70))))
        assertEquals(tags + ("maxspeed" to "70"), result)
    }

    @Test fun `advisory only is not treated as binding`() {
        assertEquals(general(null), getMaxSpeedEditState(mapOf("maxspeed:advisory" to "70")))
    }

    /* ------------------------------- undo and refresh --------------------------------- */

    @Test fun `reversed changes restore the original tags (undo)`() {
        val tags = mapOf("highway" to "residential", "maxspeed" to "50", "maxspeed:type" to "sign", "zone:maxspeed" to "DE:30")
        val changes = createMaxSpeedTagChanges(tags, general(kmh(30)))!!
        val edited = apply(tags, changes)
        assertEquals(tags, edited.toMutableMap().also { changes.reversed().applyTo(it) })
    }

    @Test fun `overlay style reflects the edited tags`() {
        val overlay = MaxSpeedOverlay { CountryInfo(null, listOf(IncompleteCountryInfo(isLeftHandTraffic = false))) }
        val tags = mapOf("highway" to "residential", "maxspeed" to "50")
        val edited = apply(tags, createMaxSpeedTagChanges(tags, general(kmh(30))))
        val style = overlay.getStyledElements(TestMapDataWithGeometry(listOf(way(1, tags = edited)))).single().second
        assertEquals(OverlayColor.Blue, assertIs<OverlayStyle.Polyline>(style).stroke!!.color)
        assertEquals("30", style.label)
    }
}
