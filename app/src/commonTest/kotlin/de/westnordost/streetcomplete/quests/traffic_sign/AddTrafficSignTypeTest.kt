package de.westnordost.streetcomplete.quests.traffic_sign

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignValue
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignWaySigns
import de.westnordost.streetcomplete.osm.traffic_sign.WaySignSelection
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.needsTrafficSignSurvey
import de.westnordost.streetcomplete.osm.traffic_sign.parseDE
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignWayEditorState
import de.westnordost.streetcomplete.overlays.traffic_sign.trafficSignWayStyle
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignOverlay
import de.westnordost.streetcomplete.quests.answerAppliedTo
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.util.countryboundaries.NoCountriesExcept
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTime

class AddTrafficSignTypeTest {

    // Germany: lon < 10, Poland: lon >= 10
    private var lookups = 0
    private val country: (LatLon) -> String? = { lookups++; if (it.longitude < 10.0) "DE" else "PL" }
    private lateinit var questType: AddTrafficSignType

    /** the answers of these tests are the signs only: only the proposals that add tags are written */
    private fun AddTrafficSignType.answerAppliedTo(signs: TrafficSignWaySigns, tags: Map<String, String>) =
        answerAppliedTo(TrafficSignAnswer(signs), tags)

    @BeforeTest fun setUp() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = mockPrefs3()
        lookups = 0
        questType = AddTrafficSignType(country)
    }

    private val de1 = LatLon(50.0, 9.0)
    private val de2 = LatLon(50.0, 9.001)
    private val pl = LatLon(50.0, 10.5)

    private fun line(vararg points: LatLon) =
        ElementPolylinesGeometry(listOf(points.toList()), points[points.size / 2])

    private fun w(vararg tags: Pair<String, String>, id: Long = 1) = way(id, tags = mapOf(*tags))

    private fun applicable(vararg ways: Pair<Way, ElementGeometry?>): List<Long> =
        questType.getApplicableElements(createMapData(ways.toMap())).map { it.id }.sorted()

    private fun asked(vararg tags: Pair<String, String>) = applicable(w(*tags) to line(de1, de2)).isNotEmpty()

    /* -------------------------------------- settings -------------------------------------- */

    @Test fun `only enabled in Germany and not overridable`() {
        assertEquals(NoCountriesExcept("DE"), questType.enabledInCountries)
        assertFalse(questType.isCountryRestrictionOverridable)
    }

    @Test fun `disabled by default, configurable and without achievements`() {
        assertNotNull(questType.defaultDisabledMessage)
        assertTrue(questType.hasQuestSettings)
        assertTrue(questType.achievements.isEmpty())
    }

    /* ------------------------------ road and path selection ------------------------------- */

    @Test fun `steps and other things are not asked`() {
        assertFalse(asked("highway" to "steps"))
        assertFalse(asked("highway" to "bus_stop"))
        assertFalse(asked("highway" to "construction"))
        assertFalse(asked("highway" to "proposed"))
        assertFalse(asked("building" to "yes"))
        assertFalse(asked())
    }

    @Test fun `areas are not asked`() {
        assertFalse(asked("highway" to "pedestrian", "area" to "yes"))
    }

    @Test fun `sidewalks, crossings and links are not asked`() {
        assertFalse(asked("highway" to "footway", "footway" to "sidewalk"))
        assertFalse(asked("highway" to "footway", "footway" to "crossing"))
        assertFalse(asked("highway" to "footway", "footway" to "link"))
        assertFalse(asked("highway" to "cycleway", "cycleway" to "crossing"))
        assertFalse(asked("highway" to "cycleway", "cycleway" to "link"))
        assertFalse(asked("highway" to "path", "path" to "crossing"))
        assertFalse(asked("highway" to "path", "path" to "sidewalk"))
        assertTrue(asked("highway" to "footway"))
        assertTrue(asked("highway" to "cycleway", "bicycle" to "designated"))
        // separately mapped cycleways along roads are the typical place for a sign
        assertTrue(asked("highway" to "cycleway", "is_sidepath" to "yes"))
        assertTrue(asked("highway" to "path", "bicycle" to "designated", "foot" to "designated"))
    }

    @Test fun `private ways are not excluded`() {
        assertTrue(asked("highway" to "residential", "access" to "private"))
        assertTrue(asked("highway" to "track", "access" to "no"))
    }

    @Test fun `nodes and relations are never asked`() {
        val n = node(1, tags = mapOf("highway" to "residential"))
        assertEquals(false, questType.isApplicableTo(n))
        assertTrue(questType.getApplicableElements(createMapData(mapOf(n to ElementPointGeometry(de1)))).toList().isEmpty())
    }

    /* ----------------------------- existing signs, three states ----------------------------- */

    // a track is always asked about (roads need access tags, see AddTrafficSignTypeFilterTest)
    private val road = "highway" to "track"

    @Test fun `unanswered ways are asked, also with yes`() {
        assertTrue(asked(road))
        assertTrue(asked(road, "traffic_sign" to "yes"))
        assertTrue(asked(road, "traffic_sign:forward" to "yes", "traffic_sign:backward" to "yes"))
    }

    @Test fun `none and no are answers`() {
        assertFalse(asked(road, "traffic_sign" to "none"))
        assertFalse(asked(road, "traffic_sign" to "no"))
        assertFalse(asked(road, "traffic_sign:forward" to "none", "traffic_sign:backward" to "none"))
    }

    @Test fun `signed ways are answered, also with named and unreadable values`() {
        assertFalse(asked(road, "traffic_sign" to "DE:274[30]"))
        assertFalse(asked(road, "traffic_sign" to "maxspeed"))
        assertFalse(asked(road, "traffic_sign" to "AT:52"))
        assertFalse(asked(road, "traffic_sign:forward" to "DE:240", "traffic_sign:backward" to "DE:239"))
    }

    @Test fun `partly answered ways are still asked`() {
        assertTrue(asked(road, "traffic_sign:forward" to "DE:274-30"))
        assertTrue(asked(road, "traffic_sign:backward" to "none"))
        assertTrue(asked(road, "traffic_sign" to "DE:274-30", "traffic_sign:backward" to "yes"))
        assertTrue(asked(road, "traffic_sign" to "yes", "traffic_sign:backward" to "none"))
    }

    @Test fun `oneway does not complete the other direction`() {
        assertTrue(asked(road, "oneway" to "yes", "traffic_sign:forward" to "DE:274-30"))
        assertTrue(asked(road, "oneway" to "-1", "traffic_sign:backward" to "DE:274-30"))
        assertTrue(asked(road, "oneway" to "yes"))
        assertFalse(asked(road, "oneway" to "yes", "traffic_sign" to "none"))
    }

    @Test fun `reversed way is asked exactly like the original`() {
        val states = listOf(null, "yes", "none", "DE:274-30")
        for (f in states) for (b in states) {
            fun tags(f: String?, b: String?) = listOfNotNull(
                road, f?.let { "traffic_sign:forward" to it }, b?.let { "traffic_sign:backward" to it }
            ).toTypedArray()
            assertEquals(asked(*tags(f, b)), asked(*tags(b, f)), "$f $b")
        }
    }

    /* --------------------------------------- country --------------------------------------- */

    @Test fun `ways outside Germany and ways crossing the border are not asked`() {
        assertEquals(emptyList(), applicable(w(road) to line(pl, LatLon(50.0, 10.6))))
        assertEquals(emptyList(), applicable(w(road) to line(de1, de2, pl)))
        assertEquals(emptyList(), applicable(w(road) to line(pl, de1, de2)))
        assertEquals(emptyList(), applicable(w(road) to null))
        assertEquals(listOf(1L), applicable(w(road) to line(de1, de2)))
    }

    @Test fun `country is only looked up for ways that qualify by their tags`() {
        applicable(
            w(road, "traffic_sign" to "none", id = 1) to line(de1, de2),
            w(road, "traffic_sign" to "DE:274-30", id = 2) to line(de1, de2),
            w("highway" to "steps", id = 3) to line(de1, de2),
            w("building" to "yes", id = 4) to line(de1, de2),
        )
        assertEquals(0, lookups)
        applicable(w(road, id = 5) to line(de1, de2))
        assertEquals(3, lookups)
    }

    @Test fun `isApplicableTo is false without lookup for non-candidates and null for candidates`() {
        assertEquals(false, questType.isApplicableTo(w(road, "traffic_sign" to "none")))
        assertEquals(false, questType.isApplicableTo(w("highway" to "steps")))
        assertNull(questType.isApplicableTo(w(road)))
        assertNull(questType.isApplicableTo(w(road, "traffic_sign" to "yes")))
        assertEquals(0, lookups)
    }

    /* -------------------------------------- answers --------------------------------------- */

    private fun answer(forward: String?, backward: String?) =
        TrafficSignWaySigns(forward?.let(::parseDE), backward?.let(::parseDE))

    @Test fun `answer for both directions`() {
        assertEquals(
            setOf(StringMapEntryAdd("traffic_sign", "DE:274-30")),
            questType.answerAppliedTo(answer("DE:274-30", "DE:274-30"), mapOf("highway" to "residential"))
        )
    }

    @Test fun `different signs per direction`() {
        val changes = questType.answerAppliedTo(
            answer("DE:274-30", "DE:274-50"), mapOf("highway" to "residential")
        )
        assertTrue(StringMapEntryAdd("traffic_sign:forward", "DE:274-30") in changes)
        assertTrue(StringMapEntryAdd("traffic_sign:backward", "DE:274-50") in changes)
    }

    @Test fun `none is written for an explicit answer only`() {
        val none = TrafficSignValue.NONE
        assertEquals(
            setOf(StringMapEntryAdd("traffic_sign", "none")),
            questType.answerAppliedTo(TrafficSignWaySigns.both(none), mapOf("highway" to "residential"))
        )
        // not surveyed never becomes none
        val changes = questType.answerAppliedTo(
            TrafficSignWaySigns(parseDE("DE:274-30"), null), mapOf("highway" to "residential")
        )
        assertEquals(setOf(StringMapEntryAdd("traffic_sign:forward", "DE:274-30")), changes)
    }

    @Test fun `answering one direction keeps the other one`() {
        val original = mapOf("highway" to "residential", "traffic_sign:backward" to "DE:274-50")
        val changes = questType.answerAppliedTo(answer("DE:274-30", null), original)
        assertEquals(setOf(StringMapEntryAdd("traffic_sign:forward", "DE:274-30")), changes)
    }

    @Test fun `answering one direction keeps the general sign for the other direction`() {
        val original = mapOf("highway" to "residential", "traffic_sign" to "DE:274-50")
        val changes = questType.answerAppliedTo(answer("DE:274-30", null), original)
        val result = original.toMutableMap().also { m ->
            changes.forEach { c ->
                when (c) {
                    is StringMapEntryAdd -> m[c.key] = c.value
                    is StringMapEntryModify -> m[c.key] = c.value
                    is StringMapEntryDelete -> m.remove(c.key)
                }
            }
        }
        assertEquals("DE:274-30", result["traffic_sign:forward"])
        assertEquals("DE:274-50", result["traffic_sign:backward"])
        assertNull(result["traffic_sign"])
    }

    @Test fun `yes is replaced by the answer but not lost for the other direction`() {
        val original = mapOf("highway" to "residential", "traffic_sign" to "yes")
        val both = questType.answerAppliedTo(answer("DE:274-30", "DE:274-30"), original)
        assertEquals(setOf(StringMapEntryModify("traffic_sign", "yes", "DE:274-30")), both)

        val oneDirection = questType.answerAppliedTo(answer("DE:274-30", null), original)
        assertTrue(StringMapEntryAdd("traffic_sign:forward", "DE:274-30") in oneDirection)
        assertTrue(StringMapEntryAdd("traffic_sign:backward", "yes") in oneDirection)
        // still a quest candidate afterwards: the backward direction is only "yes"
        val after = mapOf("highway" to "residential", "traffic_sign:forward" to "DE:274-30", "traffic_sign:backward" to "yes")
        assertTrue(needsTrafficSignSurvey(after))
    }

    @Test fun `unreadable existing value is kept for the unanswered direction`() {
        val original = mapOf("highway" to "residential", "traffic_sign" to "AT:52")
        val changes = questType.answerAppliedTo(answer("DE:274-30", null), original)
        assertTrue(StringMapEntryAdd("traffic_sign:backward", "AT:52") in changes)
    }

    @Test fun `nothing answered changes nothing and does not fail`() {
        val original = mapOf("highway" to "residential", "traffic_sign:forward" to "DE:274-30")
        assertTrue(questType.answerAppliedTo(TrafficSignWaySigns(null, null), original).isEmpty())
        // answering what is already there
        assertTrue(questType.answerAppliedTo(answer("DE:274-30", null), original).isEmpty())
        val builder = StringMapChangesBuilder(original)
        questType.applyAnswerTo(TrafficSignAnswer(TrafficSignWaySigns(null, null)), builder, ElementPointGeometry(de1), 0)
        assertTrue(builder.create().changes.isEmpty())
    }

    @Test fun `answers are idempotent`() {
        val original = mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated")
        val a = answer("DE:240", "DE:239,1022-10")
        val first = StringMapChangesBuilder(original).also { questType.applyAnswerTo(TrafficSignAnswer(a), it, ElementPointGeometry(de1), 0) }
        val tagsAfter = original.toMutableMap().also { m ->
            first.create().changes.forEach { c ->
                when (c) {
                    is StringMapEntryAdd -> m[c.key] = c.value
                    is StringMapEntryModify -> m[c.key] = c.value
                    is StringMapEntryDelete -> m.remove(c.key)
                }
            }
        }
        assertTrue(questType.answerAppliedTo(a, tagsAfter).isEmpty())
        assertFalse(needsTrafficSignSurvey(tagsAfter))
    }

    @Test fun `reversed way gets the reversed answer`() {
        val tags = mapOf("highway" to "residential")
        val a = answer("DE:274-30", "DE:274-50")
        val reversed = TrafficSignWaySigns(a.backward, a.forward)
        val c1 = questType.answerAppliedTo(a, tags)
        val c2 = questType.answerAppliedTo(reversed, tags)
        assertTrue(StringMapEntryAdd("traffic_sign:forward", "DE:274-30") in c1)
        assertTrue(StringMapEntryAdd("traffic_sign:forward", "DE:274-50") in c2)
        assertTrue(StringMapEntryAdd("traffic_sign:backward", "DE:274-30") in c2)
    }

    @Test fun `unusual combinations are saved without unsafe derived tags`() {
        val changes = questType.answerAppliedTo(
            TrafficSignWaySigns.both(parseDE("DE:237;250;274[30]")), mapOf("highway" to "residential")
        )
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.all { it.key.startsWith("traffic_sign") }, changes.toString())
    }

    @Test fun `only traffic sign tags are changed for simple speed limit signs`() {
        val changes = questType.answerAppliedTo(
            TrafficSignWaySigns.both(parseDE("DE:274[30]")), mapOf("highway" to "residential")
        )
        assertTrue(changes.all { it.key.startsWith("traffic_sign") }, changes.toString())
    }

    /* --------------------------------- quest and overlay ---------------------------------- */

    @Test fun `ways the overlay shows as complete are not asked by the quest`() {
        val states = listOf(null, "yes", "none", "DE:274-30")
        for (g in states) for (f in states) for (b in states) {
            val tags = listOfNotNull(
                road,
                g?.let { "traffic_sign" to it },
                f?.let { "traffic_sign:forward" to it },
                b?.let { "traffic_sign:backward" to it },
            ).toMap()
            val stroke = trafficSignWayStyle(tags).stroke!!
            val overlayComplete = stroke.color !in listOf(
                de.westnordost.streetcomplete.data.overlays.OverlayColor.Red,
                de.westnordost.streetcomplete.data.overlays.OverlayColor.Orange,
            )
            assertEquals(overlayComplete, !needsTrafficSignSurvey(tags), "$tags")
        }
    }

    @Test fun `overlay shows yes as unspecific, not as complete`() {
        val orange = de.westnordost.streetcomplete.data.overlays.OverlayColor.Orange
        assertEquals(OverlayStyle.Stroke(orange), trafficSignWayStyle(mapOf("traffic_sign" to "yes")).stroke)
        assertEquals(
            OverlayStyle.Stroke(orange, dashed = true),
            trafficSignWayStyle(mapOf("traffic_sign:forward" to "yes", "traffic_sign:backward" to "DE:239")).stroke
        )
    }

    @Test fun `the overlay still handles its own selection of ways`() {
        val overlay = TrafficSignOverlay(country)
        val ways: Map<Element, ElementGeometry?> = mapOf(w(road, id = 1) to line(de1, de2), w(road, id = 2) to line(de1, de2, pl))
        assertEquals(listOf(1L), overlay.getStyledElements(createMapData(ways)).map { it.first.id }.toList())
    }

    /* ------------------------------------- confirmation ------------------------------------ */

    private fun editorState(tags: Map<String, String>, forward: DirectionSelection, backward: DirectionSelection): TrafficSignWayEditorState {
        val catalogue = TrafficSignTestData.catalogueDE
        val original = WaySignSelection.of(tags, catalogue)
        val selection = WaySignSelection(forward, backward)
        return TrafficSignWayEditorState(
            catalogue, original, selection, selection.toWaySigns(original).deriveWayTags(tags),
            selection.replacesSignsWithNone(tags, catalogue),
        ) { _, _ -> }
    }

    private fun signed(v: String) = DirectionSelection.Signed(parseDE(v))

    @Test fun `plain new entries need no second confirmation`() {
        val s = editorState(mapOf("highway" to "residential"), signed("DE:274-30"), signed("DE:274-30"))
        assertTrue(s.hasChanges)
        assertFalse(s.needsConfirmation)
        val none = editorState(mapOf("highway" to "residential"), DirectionSelection.None, DirectionSelection.None)
        assertFalse(none.needsConfirmation)
    }

    @Test fun `replacing signs or changing other tags needs confirmation`() {
        val replaced = editorState(
            mapOf("highway" to "residential", "traffic_sign" to "DE:274-30"), signed("DE:274-50"), DirectionSelection.NotSurveyed
        )
        assertTrue(replaced.needsConfirmation)
        val none = editorState(
            mapOf("highway" to "residential", "traffic_sign" to "DE:274-30"), DirectionSelection.None, DirectionSelection.NotSurveyed
        )
        assertTrue(none.needsConfirmation)
        val highwayChange = editorState(
            mapOf("highway" to "path", "bicycle" to "designated", "foot" to "designated"), signed("DE:240"), signed("DE:239,1022-10")
        )
        assertTrue(highwayChange.needsConfirmation)
    }

    @Test fun `replacing yes with signs needs no confirmation`() {
        val s = editorState(
            mapOf("highway" to "residential", "traffic_sign" to "yes"), signed("DE:274-30"), signed("DE:274-30")
        )
        assertFalse(s.needsConfirmation)
    }

    @Test fun `replacing an unreadable value needs confirmation and shows the old value`() {
        val tags = mapOf("highway" to "residential", "traffic_sign:forward" to "DE:250;AT:52", "traffic_sign:backward" to "DE:239")
        val replaced = editorState(tags, signed("DE:274-30"), signed("DE:239"))
        assertEquals(listOf(TrafficSignDirection.FORWARD to "DE:250;AT:52"), replaced.replacedUnreadable)
        assertTrue(replaced.needsConfirmation)
        // none replaces it as well
        val none = editorState(tags, DirectionSelection.None, signed("DE:239"))
        assertEquals(listOf(TrafficSignDirection.FORWARD to "DE:250;AT:52"), none.replacedUnreadable)
        assertTrue(none.needsConfirmation)
        // the old value is still part of the tags until the user confirms: nothing is applied by the state
        assertEquals("DE:250;AT:52", tags["traffic_sign:forward"])
    }

    @Test fun `leaving an unreadable value as it is replaces nothing`() {
        val tags = mapOf("highway" to "residential", "traffic_sign:forward" to "AT:52")
        val unchanged = editorState(tags, signed("DE:250").let { WaySignSelection.of(tags, TrafficSignTestData.catalogueDE).forward }, DirectionSelection.None)
        assertTrue(unchanged.replacedUnreadable.isEmpty())
        val kept = editorState(tags, DirectionSelection.NotSurveyed, DirectionSelection.None)
        assertTrue(kept.replacedUnreadable.isEmpty())
        assertEquals(null, kept.waySigns.forward)
        assertEquals(TrafficSignValue.NONE, kept.waySigns.backward)
    }

    @Test fun `waySigns only contains what was changed`() {
        val tags = mapOf("highway" to "residential", "traffic_sign" to "DE:274[30]")
        val s = editorState(tags, signed("DE:274-30"), signed("DE:250"))
        assertEquals(null, s.waySigns.forward)
        assertEquals("DE:250", s.waySigns.backward?.toOsmValue())
        assertFalse(editorState(tags, signed("DE:274-30"), signed("DE:274-30")).hasChanges)
    }

    @Test fun `unchanged selection is not edited and has no changes`() {
        val tags = mapOf("highway" to "residential", "traffic_sign:forward" to "DE:274-30")
        val s = editorState(tags, signed("DE:274-30"), DirectionSelection.NotSurveyed)
        assertFalse(s.isEdited)
        assertFalse(s.hasChanges)
    }

    /* ------------------------------------- performance -------------------------------------- */

    private fun syntheticWays(count: Int, random: Random): Map<Element, ElementGeometry?> {
        val tagsets = listOf(
            mapOf("highway" to "residential"),
            mapOf("highway" to "residential", "traffic_sign" to "none"),
            mapOf("highway" to "service", "service" to "driveway"),
            mapOf("highway" to "footway", "footway" to "sidewalk"),
            mapOf("highway" to "tertiary", "traffic_sign:forward" to "DE:274-50"),
            mapOf("highway" to "path", "bicycle" to "designated", "traffic_sign" to "yes"),
            mapOf("highway" to "track"),
            mapOf("building" to "yes"),
        )
        val result = LinkedHashMap<Element, ElementGeometry?>(count * 2)
        for (i in 0 until count) {
            val lat = 47.5 + random.nextDouble() * 7.0
            val lon = 6.0 + random.nextDouble() * 8.5
            val a = LatLon(lat, lon)
            val b = LatLon(lat + 0.0005, lon + 0.0005)
            val c = LatLon(lat + 0.001, lon)
            result[way(i + 1L, tags = tagsets[random.nextInt(tagsets.size)])] = line(a, b, c)
        }
        return result
    }

    @Test fun `quest generation for a large synthetic area stays fast`() {
        val ways = syntheticWays(200_000, Random(42))
        val data = createMapData(ways)
        // warm up
        questType.getApplicableElements(data).count()
        lookups = 0
        var count = 0
        val time = measureTime { count = questType.getApplicableElements(data).count() }
        println("PERF generation: 200000 ways -> $count quests, $lookups country lookups, $time (stub country lookup)")
        assertTrue(count > 0)
        assertTrue(lookups <= 3 * count + 3 * 200_000 / 8 * 3, "lookups only for tag candidates")
        // generous bound, to find real regressions only
        assertTrue(time.inWholeMilliseconds < 5_000, "took $time")
    }

    @Test fun `quest generation with the real country boundaries`() {
        val bytes = TrafficSignTestData.readBytesOrNull("${TrafficSignTestData.RESOURCES_DIR}/files/boundaries.ser")
            ?: return // resource not available in this environment
        val boundaries = de.westnordost.countryboundaries.CountryBoundaries.deserializeFrom(
            kotlinx.io.Buffer().also { it.write(bytes) }
        )
        val real: (LatLon) -> String? = { boundaries.getIds(it.longitude, it.latitude).firstOrNull() }
        val realQuest = AddTrafficSignType(real)
        val ways = syntheticWays(200_000, Random(42))
        val data = createMapData(ways)
        realQuest.getApplicableElements(data).count() // warm up
        var count = 0
        val time = measureTime { count = realQuest.getApplicableElements(data).count() }
        println("PERF real boundaries: 200000 ways -> $count quests, $time")

        // cost of one isApplicableTo == null: the controller then runs getApplicableElements on all
        // ways of the surrounding area (padded bbox of the updated element) and picks the element
        val area = createMapData(syntheticWays(5_000, Random(7)))
        val perElement = measureTime { repeat(20) { realQuest.getApplicableElements(area).count() } } / 20
        println("PERF per null-check of one updated element, 5000 ways in the area: $perElement")
        assertTrue(count > 0)
        assertTrue(time.inWholeMilliseconds < 10_000, "took $time")
    }
}
