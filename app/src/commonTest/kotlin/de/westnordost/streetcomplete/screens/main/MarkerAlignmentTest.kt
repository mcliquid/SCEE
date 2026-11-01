package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.screens.main.map.layers.toGeoJsonFeature
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.ui.common.quest.Marker
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarkerAlignmentTest {
    @Test fun `ordinary highlights have no geographic rotation`() {
        val feature = Marker(pGeom()).toGeoJsonFeature().single()
        assertFalse(feature.properties.containsKey("rotation"))
    }

    @Test fun `explicit zero rotation remains geographic and cone stays separate`() {
        val features = Marker(pGeom(), rotation = 0.0, direction = 90.0).toGeoJsonFeature()
        assertEquals(JsonPrimitive(0.0), features[0].properties["rotation"])
        assertEquals(JsonPrimitive(90.0), features[1].properties["rotation"])
        assertEquals(JsonPrimitive(true), features[1].properties["direction"])
    }
}
