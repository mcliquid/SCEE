package de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.bicycle_in_pedestrian_street.BicycleInPedestrianStreet.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BicycleInPedestrianStreetKtTest {

    @Test fun create() {
        assertEquals(
            null,
            parseBicycleInPedestrianStreet(mapOf("highway" to "residential"))
        )
        assertEquals(
            NOT_SIGNED,
            parseBicycleInPedestrianStreet(mapOf("highway" to "pedestrian"))
        )
        assertEquals(
            NOT_SIGNED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle" to "designated",
                "bicycle:signed" to "no",
            ))
        )
        assertEquals(
            NOT_SIGNED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle" to "yes",
            ))
        )
        assertEquals(
            NOT_SIGNED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle" to "no",
            ))
        )
        assertEquals(
            ALLOWED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle:signed" to "yes",
                "bicycle" to "yes",
            ))
        )
        assertEquals(
            NOT_ALLOWED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle:signed" to "yes",
                "bicycle" to "no",
            ))
        )
        assertEquals(
            NOT_ALLOWED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle:signed" to "yes",
                "bicycle" to "dismount",
            ))
        )
        assertEquals(
            DESIGNATED,
            parseBicycleInPedestrianStreet(mapOf(
                "highway" to "pedestrian",
                "bicycle" to "designated",
            ))
        )
    }

    @Test fun `apply designated`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle", "designated")
            ),
            DESIGNATED.appliedTo(mapOf())
        )
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle", "no", "designated")
            ),
            DESIGNATED.appliedTo(mapOf("bicycle" to "no"))
        )
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle", "designated"),
                StringMapEntryModify("bicycle:signed", "no", "yes")
            ),
            DESIGNATED.appliedTo(mapOf("bicycle:signed" to "no"))
        )
    }

    @Test fun `apply allowed`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle", "yes"),
                StringMapEntryAdd("bicycle:signed", "yes"),
            ),
            ALLOWED.appliedTo(mapOf())
        )
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle", "no", "yes"),
                StringMapEntryModify("bicycle:signed", "no", "yes"),
            ),
            ALLOWED.appliedTo(mapOf("bicycle" to "no", "bicycle:signed" to "no"))
        )
    }

    @Test fun `apply not allowed`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle", "no"),
                StringMapEntryAdd("bicycle:signed", "yes"),
            ),
            NOT_ALLOWED.appliedTo(mapOf())
        )
        // transition from confirmed NOT_SIGNED
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle", "no"),
                StringMapEntryModify("bicycle:signed", "no", "yes"),
            ),
            NOT_ALLOWED.appliedTo(mapOf("bicycle:signed" to "no"))
        )
        // transition from ALLOWED
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle", "yes", "no"),
                StringMapEntryModify("bicycle:signed", "yes", "yes"),
            ),
            NOT_ALLOWED.appliedTo(mapOf("bicycle" to "yes", "bicycle:signed" to "yes"))
        )
        // transition from DESIGNATED
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle", "designated", "no"),
                StringMapEntryAdd("bicycle:signed", "yes"),
            ),
            NOT_ALLOWED.appliedTo(mapOf("bicycle" to "designated"))
        )
        // bicycle=dismount is deliberately preserved
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle:signed", "yes", "yes"),
            ),
            NOT_ALLOWED.appliedTo(mapOf("bicycle" to "dismount", "bicycle:signed" to "yes"))
        )
    }

    @Test fun `apply not signed`() {
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle:signed", "no"),
            ),
            NOT_SIGNED.appliedTo(mapOf())
        )
        assertEquals(
            setOf(
                StringMapEntryModify("bicycle:signed", "yes", "no"),
            ),
            NOT_SIGNED.appliedTo(mapOf("bicycle:signed" to "yes"))
        )
        // bicycle=yes is not changed
        assertEquals(
            setOf(
                StringMapEntryAdd("bicycle:signed", "no"),
            ),
            NOT_SIGNED.appliedTo(mapOf("bicycle" to "yes"))
        )
        assertEquals(
            setOf(
                StringMapEntryDelete("bicycle", "designated"),
                StringMapEntryAdd("bicycle:signed", "no"),
            ),
            NOT_SIGNED.appliedTo(mapOf("bicycle" to "designated"))
        )
    }

    @Test fun `not signed is persistable without changing bicycle access`() {
        val tags = StringMapChangesBuilder(mapOf("highway" to "pedestrian"))

        assertFalse(hasConfirmedNoBicycleSign(tags))
        NOT_SIGNED.applyTo(tags)

        assertEquals("no", tags["bicycle:signed"])
        assertFalse(tags.containsKey("bicycle"))
        assertEquals(NOT_SIGNED, parseBicycleInPedestrianStreet(tags))
        assertTrue(hasConfirmedNoBicycleSign(tags))
    }

    @Test fun `apply not signed preserves non-designated bicycle access values`() {
        val bicycleValues = listOf(
            "yes", "no", "dismount", "permissive", "private", "destination", "customers", "permit"
        )

        for (bicycle in bicycleValues) {
            val tags = StringMapChangesBuilder(mapOf("highway" to "pedestrian", "bicycle" to bicycle))
            NOT_SIGNED.applyTo(tags)

            assertEquals(bicycle, tags["bicycle"])
            assertEquals("no", tags["bicycle:signed"])
        }
    }
}

private fun BicycleInPedestrianStreet.appliedTo(tags: Map<String, String>): Set<StringMapEntryChange> {
    val cb = StringMapChangesBuilder(tags)
    applyTo(cb)
    return cb.create().changes
}
