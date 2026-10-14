package de.westnordost.streetcomplete.quests.smoothness

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChanges
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.osm.nowAsCheckDateString
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import de.westnordost.streetcomplete.screens.main.elementKeyForImmediateSameElementQuest
import de.westnordost.streetcomplete.screens.main.selectSameElementQuestSuccessor
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.osmQuest
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.util.math.getOrientationOrZero
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmoothnessPartsTest {
    private val left = "cycleway:left:smoothness"
    private val right = "cycleway:right:smoothness"
    private val road = mapOf("highway" to "primary", "cycleway:both" to "track", "cycleway:both:surface" to "asphalt")
    private val path = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated",
        "segregated" to "yes", "cycleway:surface" to "asphalt", "footway:surface" to "paving_stones")
    private val old = 0L
    private val today = LocalDate(2026, 10, 14)
    private val quest = AddCyclewayPartSmoothness()

    @BeforeTest fun setup() { Prefs.preferences = inMemoryPrefs() }

    private fun parts(tags: Map<String, String>) = resolveSmoothnessParts(tags, old, today)
    private fun apply(tags: Map<String, String>, values: Map<String, Smoothness>, split: Boolean = false): Map<String, String> {
        val builder = StringMapChangesBuilder(tags)
        PartSmoothnessAnswer(values, split).applyToParts(builder, old)
        val result = tags.toMutableMap()
        builder.create().changes.forEach { it.applyTo(result) }
        return result
    }

    @Test fun `left and right single explicit parts`() {
        for (side in listOf("left", "right")) {
            val tags = mapOf("highway" to "residential", "cycleway:$side" to "track", "cycleway:$side:surface" to "asphalt")
            val resolved = parts(tags)
            assertEquals(listOf("cycleway:$side:smoothness"), resolved.map { it.key })
            assertTrue(autoSubmitPartSmoothness(resolved))
            assertEquals("good", apply(tags, mapOf(resolved.single().key to Smoothness.GOOD))[resolved.single().key])
        }
    }

    @Test fun `both equal observations can write both`() {
        val result = apply(road, mapOf(left to Smoothness.GOOD, right to Smoothness.GOOD))
        assertEquals("good", result[BOTH_SMOOTHNESS_KEY])
        assertNull(result[left]); assertNull(result[right])
        assertEquals(nowAsCheckDateString(), result["check_date:$BOTH_SMOOTHNESS_KEY"])
    }

    @Test fun `both different observations write only individual sides`() {
        val result = apply(road, mapOf(left to Smoothness.GOOD, right to Smoothness.INTERMEDIATE))
        assertEquals("good", result[left]); assertEquals("intermediate", result[right])
        assertNull(result[BOTH_SMOOTHNESS_KEY])
    }

    @Test fun `partial answer preserves other part and unrelated metadata`() {
        val untouched = mapOf("surface" to "asphalt", "smoothness" to "bad", "check_date" to "2001-01-01",
            "check_date:surface" to "2002-01-01", "check_date:cycleway:both:surface" to "2002-01-01",
            "cycleway:both:surface:grade" to "2", "footway:smoothness" to "good", "smoothness:date" to "2001-01-01")
        val result = apply(road + untouched, mapOf(right to Smoothness.GOOD))
        untouched.forEach { (key, value) -> assertEquals(value, result[key], key) }
        assertNull(result[left]); assertNull(result[BOTH_SMOOTHNESS_KEY])
        assertEquals(setOf(right, "check_date:$right"), result.keys - (road + untouched).keys)
    }

    @Test fun `existing fresh specific side is never overwritten or collapsed`() {
        val tags = road + mapOf(left to "good", "check_date:$left" to nowAsCheckDateString())
        val result = apply(tags, mapOf(right to Smoothness.GOOD))
        assertEquals(tags[left], result[left]); assertNull(result[BOTH_SMOOTHNESS_KEY])
        assertFailsWith<IllegalArgumentException> { apply(tags, mapOf(left to Smoothness.BAD)) }
    }

    @Test fun `existing both requires both observations even for same value`() {
        val tags = road + (BOTH_SMOOTHNESS_KEY to "good")
        assertFailsWith<IllegalArgumentException> { apply(tags, mapOf(right to Smoothness.GOOD)) }
        assertFailsWith<IllegalArgumentException> { apply(tags, mapOf(right to Smoothness.BAD), split = true) }
        val result = apply(tags, mapOf(left to Smoothness.GOOD, right to Smoothness.GOOD))
        assertEquals("good", result[BOTH_SMOOTHNESS_KEY]); assertNull(result[left])
    }

    @Test fun `both different values require explicit split and remove only common dates`() {
        val tags = road + mapOf(BOTH_SMOOTHNESS_KEY to "good", "$BOTH_SMOOTHNESS_KEY:date" to "2000-01-01",
            "check_date:$BOTH_SMOOTHNESS_KEY" to "2000-01-01", "check_date" to "2000-01-01")
        val values = mapOf(left to Smoothness.GOOD, right to Smoothness.INTERMEDIATE)
        assertFailsWith<IllegalArgumentException> { apply(tags, values) }
        val result = apply(tags, values, split = true)
        assertNull(result[BOTH_SMOOTHNESS_KEY]); assertNull(result["$BOTH_SMOOTHNESS_KEY:date"])
        assertNull(result["check_date:$BOTH_SMOOTHNESS_KEY"])
        assertEquals("2000-01-01", result["check_date"])
        assertEquals("good", result[left]); assertEquals("intermediate", result[right])
    }

    @Test fun `both and specific smoothness conflict even when equal`() {
        assertTrue(parts(road + mapOf(BOTH_SMOOTHNESS_KEY to "good", left to "good")).isEmpty())
        assertTrue(parts(road + mapOf(BOTH_SMOOTHNESS_KEY to "good", right to "bad")).isEmpty())
    }

    @Test fun `unknown smoothness and ambiguous generic street value are preserved`() {
        assertTrue(parts(road + ("cycleway:smoothness" to "good")).isEmpty())
        assertFalse(parts(road + (left to "unknown")).any { it.key == left })
        assertEquals("unknown", apply(road + (left to "unknown"), mapOf(right to Smoothness.GOOD))[left])
    }

    @Test fun `segregated shared way resolves and writes cycle and foot independently`() {
        assertEquals(listOf("cycleway:smoothness", "footway:smoothness"), parts(path).map { it.key })
        val result = apply(path + ("smoothness" to "bad"), mapOf("cycleway:smoothness" to Smoothness.GOOD,
            "footway:smoothness" to Smoothness.INTERMEDIATE))
        assertEquals("good", result["cycleway:smoothness"])
        assertEquals("intermediate", result["footway:smoothness"])
        assertEquals("bad", result["smoothness"])
        assertFalse(result.keys.any { it.startsWith("footway:left") || it.startsWith("footway:right") })
    }

    @Test fun `foot evidence is required and road segregation never creates foot slots`() {
        val cycle = path + ("highway" to "cycleway") - "foot"
        assertTrue(parts(cycle).isEmpty())
        assertEquals(listOf("cycleway:smoothness"), parts(path - "foot").map { it.key })
        assertEquals(2, parts(road + ("cycleway:both:segregated" to "yes")).size)
        assertTrue(parts(path + ("foot" to "no")).isEmpty())
    }

    @Test fun `only foot part can be missing or stale`() {
        val tags = path + mapOf("cycleway:smoothness" to "good", "check_date:cycleway:smoothness" to nowAsCheckDateString())
        assertEquals(listOf("footway:smoothness"), parts(tags).filter { it.needsSurvey }.map { it.key })
        val result = apply(tags, mapOf("footway:smoothness" to Smoothness.GOOD))
        assertEquals(tags["check_date:cycleway:smoothness"], result["check_date:cycleway:smoothness"])
    }

    @Test fun `explicit supported surface is required also for lane`() {
        assertTrue(parts(road - "cycleway:both:surface").isEmpty())
        assertTrue(parts(road + ("cycleway:both:surface" to "sand")).isEmpty())
        assertTrue(parts(road - "cycleway:both:surface" + ("surface" to "asphalt") + ("cycleway:both" to "lane")).isEmpty())
        assertEquals(2, parts(road + ("cycleway:both" to "lane")).size)
        assertFailsWith<IllegalArgumentException> { apply(road + ("cycleway:both:surface" to "gravel"), mapOf(left to Smoothness.EXCELLENT)) }
    }

    @Test fun `unambiguous surface fallback and conflicting surfaces`() {
        assertEquals(2, parts(road - "cycleway:both:surface" + ("cycleway:surface" to "asphalt")).size)
        assertEquals(listOf(right), parts(road + ("cycleway:left:surface" to "sett")).map { it.key })
    }

    @Test fun `infrastructure conflicts excluded and unknown opposite side not invented`() {
        assertEquals(listOf(right), parts(road + ("cycleway:left" to "separate")).map { it.key })
        assertTrue(parts(road - "cycleway:both" + ("cycleway" to "track")).isEmpty())
        assertTrue(parts(road + ("cycleway" to "lane")).isEmpty())
        assertTrue(parts(road + ("cycleway:both:segregated" to "no")).isEmpty())
        assertTrue(parts(road + ("cycleway:lanes" to "lane|lane")).isEmpty())
    }

    @Test fun `separate and shared lanes are not queried`() {
        for (value in listOf("separate", "shared_lane", "no", "shoulder", "share_busway", "opposite_track"))
            assertTrue(parts(road + ("cycleway:both" to value)).isEmpty(), value)
        assertTrue(parts(path + ("segregated" to "no")).isEmpty())
    }

    @Test fun `four year boundary and explicit fresh check overrides old element and legacy date`() {
        val tags = road + mapOf(left to "good", "$left:date" to "2000-01-01", "check_date:$left" to "2022-10-14")
        assertFalse(parts(tags).first { it.key == left }.needsSurvey)
        assertTrue(parts(tags + ("check_date:$left" to "2022-10-13")).first { it.key == left }.needsSurvey)
        assertFalse(parts(tags + ("check_date:$left" to "2026-10-14")).first { it.key == left }.needsSurvey)
    }

    @Test fun `invalid and competing checkdates do not cause metadata repairs`() {
        val tags = road + mapOf(left to "good", "$left:date" to "2026-10-14", "check_date:$left" to "bad date")
        assertFalse(parts(tags).first { it.key == left }.needsSurvey)
        assertFalse(parts(tags + mapOf("check_date:$left" to "2020-01-01", "$left:check_date" to "2021-01-01")).any { it.key == left })
    }

    @Test fun `old noncanonical checkdate is not made contradictory by a new canonical date`() {
        val tags = road + mapOf(left to "good", "$left:check_date" to "2000-01-01")
        assertFalse(parts(tags).any { it.key == left })
        assertFailsWith<IllegalArgumentException> { apply(tags, mapOf(left to Smoothness.GOOD)) }
    }

    @Test fun `download and edit selection share resolver even with a broad custom filter`() {
        val eligible = way(id = 1, tags = road)
        val invalid = way(id = 2, tags = road + ("cycleway:both:surface" to "sand"))
        val separate = way(id = 3, tags = road + ("cycleway:both" to "separate"))
        val mapData = TestMapDataWithGeometry(listOf(eligible, invalid, separate))
        Prefs.preferences.putString(quest.getPrefixedFullElementSelectionPref(Prefs.preferences), "ways with highway")
        assertEquals(listOf(eligible), quest.getApplicableElements(mapData).toList())
        assertTrue(quest.isApplicableTo(eligible))
        assertFalse(quest.isApplicableTo(invalid))
        assertFalse(quest.isApplicableTo(separate))
    }

    @Test fun `restricted access sidewalk context and crossing links are excluded`() {
        for (extra in listOf(mapOf("access" to "private"), mapOf("vehicle" to "no"), mapOf("bicycle" to "dismount")))
            assertTrue(parts(road + extra).isEmpty())
        for (extra in listOf(mapOf("sidewalk" to "no"), mapOf("footway" to "crossing"), mapOf("path" to "link"),
            mapOf("indoor" to "yes"), mapOf("conveying" to "yes"), mapOf("area" to "yes")))
            assertTrue(parts(path + extra).isEmpty())
    }

    @Test fun `both cannot be updated when only one infrastructure side resolves`() {
        assertTrue(parts(road + mapOf(BOTH_SMOOTHNESS_KEY to "good", "cycleway:left:surface" to "sand")).isEmpty())
    }

    @Test fun `resurvey refreshes only the observed check date preserving old legacy date`() {
        val tags = road + mapOf(left to "good", "$left:date" to "2000-01-01", "check_date" to "2000-01-01")
        val result = apply(tags, mapOf(left to Smoothness.GOOD))
        assertEquals("2000-01-01", result["$left:date"])
        assertEquals("2000-01-01", result["check_date"])
        assertFalse(resolveSmoothnessParts(result, old).first { it.key == left }.needsSurvey)
    }

    @Test fun `single surface auto submits but overview never does`() {
        assertFalse(autoSubmitPartSmoothness(parts(road)))
        assertFalse(autoSubmitPartSmoothness(parts(path)))
        assertFalse(autoSubmitPartSmoothness(parts(road + (BOTH_SMOOTHNESS_KEY to "good"))))
        assertFalse(autoSubmitPartSmoothness(parts(path + mapOf("cycleway:smoothness" to "good", "check_date:cycleway:smoothness" to "2026-10-14"))))
    }

    @Test fun `reversed geometry and oneway do not flip tag sides`() {
        val tags = road - "cycleway:both" + ("cycleway:left" to "track")
        val forward = way(nodes = listOf(1, 2, 3), tags = tags)
        val reverse = way(nodes = listOf(3, 2, 1), tags = tags + ("oneway" to "-1"))
        assertEquals(resolveSmoothnessParts(forward.tags, forward.timestampEdited).map { it.key },
            resolveSmoothnessParts(reverse.tags, reverse.timestampEdited).map { it.key })
        val points = listOf(p(52.0, 13.0), p(52.001, 13.0))
        val geometry = ElementPolylinesGeometry(listOf(points), p(52.0005, 13.0))
        val reversedGeometry = geometry.copy(polylines = listOf(points.reversed()))
        val difference = (reversedGeometry.getOrientationOrZero() - geometry.getOrientationOrZero() + 360f) % 360f
        assertEquals(180f, difference, 0.1f)
    }

    @Test fun `invalid answer is rejected atomically`() {
        val builder = StringMapChangesBuilder(road)
        assertFailsWith<IllegalArgumentException> {
            PartSmoothnessAnswer(mapOf(left to Smoothness.GOOD, "smoothness" to Smoothness.BAD)).applyToParts(builder, old)
        }
        assertTrue(builder.create().isEmpty())
    }

    @Test fun `F3 partial answer leaves same type as successor and full answer removes it`() {
        val element = way(id = 77, nodes = listOf(1, 2), tags = road)
        val changes = quest.answerAppliedTo(PartSmoothnessAnswer(mapOf(left to Smoothness.GOOD)), road)
        val key = elementKeyForImmediateSameElementQuest(quest,
            UpdateElementTagsAction(element, StringMapChanges(changes)), true)
        assertNotNull(key)
        val partial = road.toMutableMap().also { tags -> changes.forEach { it.applyTo(tags) } }
        assertTrue(quest.isApplicableTo(way(tags = partial)))
        val candidate = osmQuest(quest, elementType = ElementType.WAY, elementId = 77, geometry = pGeom(52.0, 13.0))
        assertEquals(candidate, selectSameElementQuestSuccessor(listOf(candidate), key, listOf(quest)))
        val full = apply(partial, mapOf(right to Smoothness.GOOD))
        assertFalse(quest.isApplicableTo(way(tags = full)))
        assertNull(selectSameElementQuestSuccessor(emptyList(), key, listOf(quest)))
    }

    @Test fun `draft serialization keeps explicit observations and split intent`() {
        val draft = PartSmoothnessAnswer(mapOf(left to Smoothness.GOOD, right to Smoothness.BAD), true)
        assertEquals(draft, Json.decodeFromString<PartSmoothnessAnswer>(Json.encodeToString(PartSmoothnessAnswer.serializer(), draft)))
    }

    @Test fun `legacy saved tag action JSON is readable with unchanged identity`() {
        val action = UpdateElementTagsAction(way(tags = path), StringMapChanges(setOf(StringMapEntryAdd("cycleway:smoothness", "good"))))
        val persisted = Json.encodeToString(UpdateElementTagsAction.serializer(), action)
        assertEquals(action, Json.decodeFromString<UpdateElementTagsAction>(persisted))
        assertEquals("AddCyclewayPartSmoothness", quest.name)
        // Legacy answer APIs remain valid; no migration of existing tag diffs is needed.
        assertTrue(quest.answerAppliedTo(SmoothnessValueAnswer(Smoothness.GOOD), path).isNotEmpty())
    }
}
