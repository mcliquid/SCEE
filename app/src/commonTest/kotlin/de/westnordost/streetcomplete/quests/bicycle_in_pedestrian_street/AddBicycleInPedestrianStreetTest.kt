package de.westnordost.streetcomplete.quests.bicycle_in_pedestrian_street

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street.BicycleInPedestrianStreet.*
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AddBicycleInPedestrianStreetTest {
    private val quest = AddBicycleInPedestrianStreet()
    private val pedestrian = mapOf("highway" to "pedestrian")

    private fun selected(element: Element): Boolean =
        element in quest.getApplicableElements(TestMapDataWithGeometry(listOf(element)))

    @Test fun `pedestrian ways and closed areas are selected`() {
        assertTrue(selected(way(tags = pedestrian)))
        assertTrue(selected(way(nodes = listOf(1, 2, 3, 1), tags = pedestrian + ("area" to "yes"))))
        assertTrue(selected(rel(tags = pedestrian + ("type" to "multipolygon"))))
        assertFalse(selected(node(tags = pedestrian)))
        assertFalse(selected(way(tags = mapOf("highway" to "footway"))))
        assertFalse(selected(rel(tags = pedestrian + ("type" to "route"))))
    }

    @Test fun `any existing bicycle information excludes quest`() {
        for ((key, values) in mapOf(
            "bicycle" to listOf("yes", "no", "designated", "dismount"),
            "bicycle:conditional" to listOf("yes @ (19:00-09:00)"),
            "bicycle:signed" to listOf("no", "yes"),
        )) for (value in values) {
            val element = way(tags = pedestrian + (key to value))
            assertFalse(selected(element), "$key=$value")
            assertEquals(false, quest.isApplicableTo(element))
        }
    }

    @Test fun `general access restrictions do not exclude quest`() {
        for ((key, value) in listOf(
            "access" to "no", "access" to "private", "vehicle" to "no",
            "vehicle:conditional" to "no @ (09:00-19:00)",
            "access:conditional" to "private @ (09:00-19:00)",
        )) assertTrue(selected(way(tags = pedestrian + (key to value))), "$key=$value")
    }

    @Test fun `multipolygon boundaries are not asked separately even after relation answered`() {
        val boundary = way(tags = pedestrian)
        for (extra in listOf(emptyMap(), mapOf("bicycle:signed" to "no"))) {
            val area = rel(
                members = listOf(member(ElementType.WAY, boundary.id, "outer")),
                tags = pedestrian + ("type" to "multipolygon") + extra,
            )
            assertEquals(
                if (extra.isEmpty()) listOf(area) else emptyList(),
                quest.getApplicableElements(TestMapDataWithGeometry(listOf(boundary, area))).toList(),
            )
        }
        assertEquals(null, quest.isApplicableTo(boundary))
    }

    @Test fun `sign answers change exactly one tag and preserve unrelated restrictions`() {
        val source = pedestrian + mapOf("vehicle" to "no", "access" to "private", "traffic_sign" to "DE:242.1")
        for ((sign, tag) in listOf(
            ALLOWED to ("bicycle" to "yes"),
            NOT_ALLOWED to ("bicycle" to "no"),
            NOT_SIGNED to ("bicycle:signed" to "no"),
        )) {
            assertEquals(setOf(StringMapEntryAdd(tag.first, tag.second)), quest.answerAppliedTo(BicycleInPedestrianStreetAnswer.Sign(sign), source))
            assertFalse(selected(way(tags = source + tag)))
        }
    }

    @Test fun `conditional answer adds only conditional access and resolves quest`() {
        val value = "yes @ (19:00-09:00)"
        assertEquals(
            setOf(StringMapEntryAdd("bicycle:conditional", value)),
            quest.answerAppliedTo(BicycleInPedestrianStreetAnswer.Conditional(value), pedestrian),
        )
        assertFalse(selected(way(tags = pedestrian + ("bicycle:conditional" to value))))
    }

    @Test fun `answers preserve bicycle tags added since opening quest`() {
        for (tag in listOf("bicycle" to "designated", "bicycle:conditional" to "no @ (09:00-19:00)", "bicycle:signed" to "yes")) {
            for (answer in listOf(
                BicycleInPedestrianStreetAnswer.Sign(ALLOWED),
                BicycleInPedestrianStreetAnswer.Sign(NOT_ALLOWED),
                BicycleInPedestrianStreetAnswer.Sign(NOT_SIGNED),
                BicycleInPedestrianStreetAnswer.Conditional("yes @ (19:00-09:00)"),
            )) assertTrue(quest.answerAppliedTo(answer, pedestrian + tag).isEmpty())
        }
    }

    @Test fun `quest is disabled by default`() {
        assertNotNull(quest.defaultDisabledMessage)
    }
}
