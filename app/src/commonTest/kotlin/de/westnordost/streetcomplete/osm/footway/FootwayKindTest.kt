package de.westnordost.streetcomplete.osm.footway

import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryChange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.osm.footway.FootwayKind.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FootwayKindTest {

    @Test fun `parse supported values`() {
        assertEquals(SIDEWALK, parse("sidewalk"))
        assertEquals(CROSSING, parse("crossing"))
        assertEquals(TRAFFIC_ISLAND, parse("traffic_island"))
        assertEquals(ACCESS_AISLE, parse("access_aisle"))
    }

    @Test fun `parse missing footway as unspecified`() {
        assertEquals(UNSPECIFIED, parseFootwayKind(mapOf("highway" to "footway")))
    }

    @Test fun `parse values that are not offered as unsupported, not as missing`() {
        for (value in listOf("link", "alley", "lane", "no", "Sidewalk", "sidewalk;crossing", "")) {
            assertEquals(UNSUPPORTED, parse(value), value)
        }
    }

    @Test fun `parse only highway=footway`() {
        for (highway in listOf("path", "cycleway", "steps", "bridleway", "residential", "pedestrian")) {
            assertNull(parseFootwayKind(mapOf("highway" to highway, "footway" to "sidewalk")), highway)
        }
        assertNull(parseFootwayKind(mapOf("footway" to "sidewalk")))
    }

    @Test fun `link and unsupported kinds are not selectable`() {
        assertEquals(listOf(SIDEWALK, CROSSING, TRAFFIC_ISLAND, ACCESS_AISLE, UNSPECIFIED), FootwayKind.selectable)
        assertTrue(FootwayKind.entries.none { it.osmValue == "link" })
    }

    @Test fun `set kind`() {
        assertEquals(setOf(StringMapEntryAdd("footway", "sidewalk")), apply(SIDEWALK, mapOf("highway" to "footway")))
        assertEquals(setOf(StringMapEntryAdd("footway", "crossing")), apply(CROSSING, mapOf("highway" to "footway")))
        assertEquals(setOf(StringMapEntryAdd("footway", "traffic_island")), apply(TRAFFIC_ISLAND, mapOf("highway" to "footway")))
        assertEquals(setOf(StringMapEntryAdd("footway", "access_aisle")), apply(ACCESS_AISLE, mapOf("highway" to "footway")))
    }

    @Test fun `replace kind`() {
        assertEquals(
            setOf(StringMapEntryModify("footway", "sidewalk", "crossing")),
            apply(CROSSING, mapOf("highway" to "footway", "footway" to "sidewalk"))
        )
    }

    @Test fun `replace unsupported value only by explicit selection`() {
        assertEquals(
            setOf(StringMapEntryModify("footway", "link", "sidewalk")),
            apply(SIDEWALK, mapOf("highway" to "footway", "footway" to "link"))
        )
    }

    @Test fun `unspecified explicitly removes only footway`() {
        assertEquals(
            setOf(StringMapEntryDelete("footway", "alley")),
            apply(UNSPECIFIED, mapOf("highway" to "footway", "footway" to "alley", "surface" to "asphalt"))
        )
    }

    @Test fun `same kind is no change`() {
        for (kind in listOf(SIDEWALK, CROSSING, TRAFFIC_ISLAND, ACCESS_AISLE)) {
            assertEquals(emptySet(), apply(kind, mapOf("highway" to "footway", "footway" to kind.osmValue!!)), kind.name)
        }
        assertEquals(emptySet(), apply(UNSPECIFIED, mapOf("highway" to "footway")))
    }

    @Test fun `unsupported can not be applied`() {
        assertFailsWith<IllegalArgumentException> {
            apply(UNSUPPORTED, mapOf("highway" to "footway", "footway" to "link"))
        }
    }

    @Test fun `all other tags are kept`() {
        val others = mapOf(
            "highway" to "footway",
            "crossing" to "uncontrolled", "crossing:markings" to "zebra", "crossing:signals" to "no",
            "crossing_link" to "yes", "kerb" to "lowered", "kerb:left" to "raised", "tactile_paving" to "yes",
            "access" to "private", "foot" to "designated", "bicycle" to "yes", "bicycle:signed" to "yes",
            "segregated" to "no", "sidewalk" to "no", "sidewalk:both" to "separate", "is_sidepath" to "yes",
            "is_sidepath:of" to "residential", "surface" to "paving_stones", "smoothness" to "good",
            "check_date" to "2020-01-01", "check_date:surface" to "2020-01-01", "lit" to "yes",
        )
        for (kind in FootwayKind.selectable) {
            for (before in listOf(null, "sidewalk", "crossing", "link")) {
                val tags = if (before != null) others + ("footway" to before) else others
                val builder = StringMapChangesBuilder(tags)
                kind.applyTo(builder)
                for ((key, value) in others) {
                    assertEquals(value, builder[key], "$kind from $before: $key")
                }
                assertTrue(builder.create().changes.all { it.key == "footway" }, "$kind from $before")
                assertEquals(kind.osmValue, builder["footway"])
            }
        }
    }

    private fun parse(footway: String) = parseFootwayKind(mapOf("highway" to "footway", "footway" to footway))

    private fun apply(kind: FootwayKind, tags: Map<String, String>): Set<StringMapEntryChange> {
        val builder = StringMapChangesBuilder(tags)
        kind.applyTo(builder)
        return builder.create().changes.toSet()
    }
}
