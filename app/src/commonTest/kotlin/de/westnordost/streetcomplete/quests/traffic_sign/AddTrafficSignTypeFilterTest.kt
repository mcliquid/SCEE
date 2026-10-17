package de.westnordost.streetcomplete.quests.traffic_sign

import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.osm.accessKeys
import de.westnordost.streetcomplete.osm.traffic_sign.DirectionSelection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignAccess
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignDirection
import de.westnordost.streetcomplete.osm.traffic_sign.TrafficSignTestData
import de.westnordost.streetcomplete.osm.traffic_sign.WaySignSelection
import de.westnordost.streetcomplete.osm.traffic_sign.deriveWayTags
import de.westnordost.streetcomplete.osm.traffic_sign.parseDE
import de.westnordost.streetcomplete.overlays.traffic_sign.TrafficSignWayEditorState
import de.westnordost.streetcomplete.quests.getPrefixedFullElementSelectionPref
import de.westnordost.streetcomplete.testutils.createMapData
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs
import de.westnordost.streetcomplete.testutils.mockPrefs3
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.way
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The default filter of AddTrafficSignType: only the obviously relevant ways, and the warning
 *  for "no signs" on ways with access tags */
class AddTrafficSignTypeFilterTest {

    private val country: (LatLon) -> String? = { if (it.longitude < 10.0) "DE" else "PL" }
    private val de1 = LatLon(50.0, 9.0)
    private val de2 = LatLon(50.0, 9.001)
    private val pl = LatLon(50.0, 10.5)
    private lateinit var questType: AddTrafficSignType

    @BeforeTest fun setUp() {
        Prefs.sharedPreferences = mockPrefs()
        Prefs.preferences = mockPrefs3()
        questType = AddTrafficSignType(country)
    }

    private fun line(vararg points: LatLon) = ElementPolylinesGeometry(listOf(points.toList()), points[points.size / 2])

    private fun asked(questType: AddTrafficSignType = this.questType, vararg tags: Pair<String, String>): Boolean =
        questType.getApplicableElements(createMapData(mapOf(way(1, tags = mapOf(*tags)) to line(de1, de2)))).any()

    private fun asked(vararg tags: Pair<String, String>) = asked(questType, *tags)

    private fun hw(highway: String, vararg more: Pair<String, String>) = arrayOf("highway" to highway, *more)

    private val ALWAYS = listOf("track", "path", "cycleway", "footway", "living_street", "pedestrian")
    private val WITH_ACCESS = listOf("residential", "service", "unclassified", "tertiary", "secondary", "primary")
    private val EXCLUDED = listOf(
        "motorway", "trunk", "bridleway", "steps",
        "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link",
        "construction", "proposed", "bus_stop", "raceway", "corridor", "elevator",
    )
    private val PRIVATE = "access" to "private"

    /* ---------------------------------------- highway types ---------------------------------------- */

    @Test fun `living streets and pedestrian ways are asked with and without access tags`() {
        for (h in listOf("living_street", "pedestrian")) {
            assertTrue(asked(*hw(h)), h)
            assertTrue(asked(*hw(h, PRIVATE)), h)
            assertTrue(asked(*hw(h, "motor_vehicle" to "destination", "bicycle" to "yes")), h)
            assertTrue(asked(*hw(h, "access" to "yes")), h)
            // areas (e.g. pedestrian squares) are still excluded
            assertFalse(asked(*hw(h, "area" to "yes")), h)
            assertFalse(asked(*hw(h, PRIVATE, "area" to "yes")), h)
            // surveyed signs
            assertFalse(asked(*hw(h, "traffic_sign" to "none")), h)
            assertFalse(asked(*hw(h, "traffic_sign" to "DE:325.1")), h)
            assertFalse(asked(*hw(h, PRIVATE, "traffic_sign" to "none")), h)
            assertTrue(asked(*hw(h, "traffic_sign:forward" to "DE:325.1")), h)
            assertTrue(asked(*hw(h, PRIVATE, "traffic_sign:backward" to "none")), h)
            assertTrue(asked(*hw(h, "traffic_sign" to "yes")), h)
            assertFalse(asked(*hw(h, "traffic_sign:forward" to "DE:325.1", "traffic_sign:backward" to "none")), h)
        }
    }

    @Test fun `tracks, paths, cycleways and footways are asked without access tags`() {
        for (h in ALWAYS) assertTrue(asked(*hw(h)), h)
    }

    @Test fun `normal roads without access tags are not asked`() {
        for (h in WITH_ACCESS) assertFalse(asked(*hw(h)), h)
        // other tags do not qualify either
        for (h in WITH_ACCESS) assertFalse(asked(*hw(h, "name" to "A", "maxspeed" to "30", "oneway" to "yes", "surface" to "asphalt")), h)
    }

    @Test fun `normal roads with qualifying access tags are asked`() {
        for (h in WITH_ACCESS) assertTrue(asked(*hw(h, PRIVATE)), h)
        for (h in WITH_ACCESS) assertTrue(asked(*hw(h, "bicycle" to "designated")), h)
    }

    @Test fun `excluded highway types are never asked, with or without access tags`() {
        for (h in EXCLUDED) {
            assertFalse(asked(*hw(h)), h)
            assertFalse(asked(*hw(h, PRIVATE)), h)
            assertFalse(asked(*hw(h, "motor_vehicle" to "no", "bicycle" to "designated")), h)
        }
    }

    @Test fun `area, nodes, relations and other elements are never asked`() {
        assertFalse(asked(*hw("track", "area" to "yes")))
        assertFalse(asked(*hw("residential", PRIVATE, "area" to "yes")))
        assertTrue(asked(*hw("track", "area" to "no")))
        val n = node(1, tags = mapOf("highway" to "track"))
        assertEquals(false, questType.isApplicableTo(n))
        assertFalse(asked("building" to "yes", PRIVATE))
    }

    /* --------------------------------- crossings, links, minor service ways --------------------------------- */

    @Test fun `sidewalks, crossings, links and traffic islands are not asked, whatever the access tags`() {
        for (v in listOf("sidewalk", "crossing", "link", "traffic_island")) {
            assertFalse(asked(*hw("footway", "footway" to v)), v)
            assertFalse(asked(*hw("footway", "footway" to v, "bicycle" to "designated", PRIVATE)), v)
        }
        for (v in listOf("crossing", "link")) {
            assertFalse(asked(*hw("cycleway", "cycleway" to v)), v)
            assertFalse(asked(*hw("cycleway", "cycleway" to v, "bicycle" to "designated")), v)
        }
        for (v in listOf("sidewalk", "crossing", "link")) {
            assertFalse(asked(*hw("path", "path" to v)), v)
            assertFalse(asked(*hw("path", "path" to v, "bicycle" to "designated")), v)
        }
    }

    @Test fun `other kinds of footways, cycleways and paths are asked`() {
        assertTrue(asked(*hw("footway", "footway" to "alley")))
        assertTrue(asked(*hw("cycleway", "cycleway" to "track")))
        assertTrue(asked(*hw("path", "path" to "hiking")))
        assertTrue(asked(*hw("cycleway", "is_sidepath" to "yes")))
        // the sub-keys only matter for their own highway
        assertTrue(asked(*hw("track", "footway" to "sidewalk", "cycleway" to "crossing", "path" to "link")))
    }

    @Test fun `minor service ways are not asked even with access tags, the other ones are`() {
        for (s in listOf("driveway", "parking_aisle", "drive-through", "slipway", "emergency_access", "alley", "bus", "siding", "yard", "spur")) {
            assertFalse(asked(*hw("service", "service" to s, PRIVATE)), s)
            assertFalse(asked(*hw("service", "service" to s)), s)
        }
        assertTrue(asked(*hw("service", PRIVATE)))
        assertTrue(asked(*hw("service", "service" to "residential", "motor_vehicle" to "destination")))
        assertFalse(asked(*hw("service", "service" to "residential")))
        // the service key is only meaningful for highway=service
        assertTrue(asked(*hw("track", "service" to "driveway")))
        assertTrue(asked(*hw("residential", "service" to "driveway", PRIVATE)))
    }

    /* ---------------------------------------- access tags ---------------------------------------- */

    private fun suffixes() = listOf("", ":forward", ":backward")

    @Test fun `special access values on all keys and directions qualify a road`() {
        for (key in TrafficSignAccess.KEYS) for (suffix in suffixes()) for (value in TrafficSignAccess.SPECIAL_VALUES) {
            assertTrue(asked(*hw("residential", "$key$suffix" to value)), "$key$suffix=$value")
        }
    }

    @Test fun `lists of values qualify if one of them is special`() {
        assertTrue(asked(*hw("residential", "motor_vehicle" to "agricultural;forestry")))
        assertTrue(asked(*hw("residential", "motor_vehicle" to "yes;destination")))
        assertTrue(asked(*hw("residential", "hgv" to "delivery;yes")))
        assertFalse(asked(*hw("residential", "motor_vehicle" to "yes;permissive")))
        assertFalse(asked(*hw("residential", "motor_vehicle" to "undestinationed")))
    }

    @Test fun `yes only qualifies for the special road users`() {
        for (key in TrafficSignAccess.YES_KEYS) for (suffix in suffixes()) {
            assertTrue(asked(*hw("residential", "$key$suffix" to "yes")), "$key$suffix=yes")
        }
        for (key in TrafficSignAccess.KEYS - TrafficSignAccess.YES_KEYS.toSet()) for (suffix in suffixes()) {
            assertFalse(asked(*hw("residential", "$key$suffix" to "yes")), "$key$suffix=yes")
        }
    }

    @Test fun `ordinary access values and unrelated keys do not qualify`() {
        for (tag in listOf(
            "access" to "yes", "foot" to "yes", "bicycle" to "yes", "motor_vehicle" to "yes", "vehicle" to "yes",
            "motorcar" to "yes", "motorcycle" to "yes", "horse" to "yes", "access" to "permissive", "foot" to "customers",
            "motor_vehicle" to "permit", "bicycle" to "dismount", "bicycle" to "use_sidepath", "bicycle" to "discouraged",
            "access" to "", "access" to "unknown", "motor_vehicle" to "Private", "horse" to "no", "moped" to "no", "emergency" to "no",
            "bicycle:signed" to "yes", "bicycle:signed" to "no", "access:conditional" to "no @ (22:00-06:00)",
            "motor_vehicle:conditional" to "destination @ (Mo-Fr)", "motor_vehicle:lanes" to "no|yes", "access:lanes" to "no",
            "motor_vehicle:forward:conditional" to "no @ (Su)", "foot:left" to "no", "mybicycle" to "no", "name" to "private",
            "motor_vehicle:both" to "no", "motor_vehicle:forward:extra" to "no",
        )) {
            assertFalse(asked(*hw("residential", tag)), tag.toString())
        }
    }

    @Test fun `access tags on always asked ways change nothing`() {
        for (h in ALWAYS) {
            assertTrue(asked(*hw(h, "access" to "yes")), h)
            assertTrue(asked(*hw(h, PRIVATE)), h)
        }
    }

    @Test fun `the filter and the access function agree`() {
        val keys = TrafficSignAccess.KEYS + listOf("horse", "moped", "bicycle:signed", "access:conditional", "motor_vehicle:forward:conditional")
        val values = TrafficSignAccess.SPECIAL_VALUES + listOf("yes", "permissive", "agricultural;forestry", "", "x;yes")
        for (key in keys) for (suffix in suffixes()) for (value in values) {
            val tags = mapOf("highway" to "residential", "$key$suffix" to value)
            assertEquals(TrafficSignAccess.hasRelevantTags(tags), asked(*hw("residential", "$key$suffix" to value)), "$key$suffix=$value")
        }
    }

    @Test fun `the access keys are access keys of the access manager`() {
        // agricultural and forestry are keys of their own that the access manager does not offer
        val notOffered = TrafficSignAccess.KEYS - accessKeys.toSet()
        assertEquals(listOf("agricultural", "forestry"), notOffered)
        assertTrue(TrafficSignAccess.KEYS.containsAll(TrafficSignAccess.YES_KEYS))
    }

    /* ------------------------------ access tags and existing traffic sign values ------------------------------ */

    @Test fun `access tags and surveyed signs`() {
        val r = hw("residential", "motor_vehicle" to "destination")
        assertTrue(asked(*r))
        assertTrue(asked(*r, "traffic_sign" to "yes"))
        assertTrue(asked(*r, "traffic_sign:forward" to "DE:250"))
        assertTrue(asked(*r, "traffic_sign:backward" to "none"))
        assertFalse(asked(*r, "traffic_sign" to "none"))
        assertFalse(asked(*r, "traffic_sign" to "no"))
        assertFalse(asked(*r, "traffic_sign" to "DE:250,1020-30"))
        assertFalse(asked(*r, "traffic_sign:forward" to "DE:250", "traffic_sign:backward" to "none"))
        // empty values do not count as surveyed
        assertTrue(asked(*r, "traffic_sign" to "", "traffic_sign:forward" to " "))
        // always asked ways behave the same
        assertFalse(asked(*hw("track", "traffic_sign" to "none")))
        assertTrue(asked(*hw("track", "traffic_sign:forward" to "none")))
    }

    @Test fun `country and node rules still apply`() {
        val w = way(1, tags = mapOf("highway" to "track"))
        val outside = createMapData(mapOf(w to line(pl, LatLon(50.0, 10.6))))
        assertTrue(questType.getApplicableElements(outside).none())
        val border = createMapData(mapOf(w to line(de1, de2, pl)))
        assertTrue(questType.getApplicableElements(border).none())
        assertNull(questType.isApplicableTo(w))
        assertEquals(false, questType.isApplicableTo(way(1, tags = mapOf("highway" to "residential"))))
        assertNull(questType.isApplicableTo(way(1, tags = mapOf("highway" to "residential", "access" to "private"))))
    }

    /* ----------------------------------------- manual filter ----------------------------------------- */

    @Test fun `a configured filter replaces the default filter but not the fixed conditions`() {
        Prefs.preferences = inMemoryPrefs()
        val probe = AddTrafficSignType(country)
        Prefs.preferences.putString(probe.getPrefixedFullElementSelectionPref(Prefs.preferences), "ways with highway ~ residential|steps")
        val custom = AddTrafficSignType(country)
        // not asked by default, asked with the configured filter
        assertTrue(asked(custom, *hw("residential")))
        assertTrue(asked(custom, *hw("steps")))
        // not asked by the configured filter any more
        assertFalse(asked(custom, *hw("track")))
        // fixed: a survey must be needed
        assertFalse(asked(custom, *hw("residential", "traffic_sign" to "none")))
        assertTrue(asked(custom, *hw("residential", "traffic_sign:forward" to "none")))
        // fixed: ways in Germany only
        val w = way(1, tags = mapOf("highway" to "residential"))
        assertTrue(custom.getApplicableElements(createMapData(mapOf(w to line(de1, de2, pl)))).none())
        // fixed: only ways, also if the configured filter says otherwise
        Prefs.preferences.putString(probe.getPrefixedFullElementSelectionPref(Prefs.preferences), "nodes with highway")
        val nodes = AddTrafficSignType(country)
        assertEquals(false, nodes.isApplicableTo(node(1, tags = mapOf("highway" to "residential"))))
        assertTrue(nodes.getApplicableElements(createMapData(mapOf(node(1, tags = mapOf("highway" to "residential")) to de1.let { de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry(it) }))).none())
    }

    @Test fun `a stored filter is not replaced by a changed default`() {
        Prefs.preferences = inMemoryPrefs()
        val key = AddTrafficSignType(country).getPrefixedFullElementSelectionPref(Prefs.preferences)
        // the default of phase 3.3: pedestrian and living_street needed access tags or were excluded
        Prefs.preferences.putString(key, "ways with highway ~ track|path|cycleway|footway")
        val stored = AddTrafficSignType(country)
        assertFalse(asked(stored, *hw("living_street")))
        assertFalse(asked(stored, *hw("pedestrian")))
        assertTrue(asked(stored, *hw("track")))
        // after "reset" (the preference is removed) the new default applies
        Prefs.preferences.remove(key)
        val reset = AddTrafficSignType(country)
        assertTrue(asked(reset, *hw("living_street")))
        assertTrue(asked(reset, *hw("pedestrian")))
    }

    @Test fun `the default filter parses and is the one in use`() {
        assertNotNull(AddTrafficSignType.DEFAULT_ELEMENT_FILTER)
        assertTrue(AddTrafficSignType.DEFAULT_ELEMENT_FILTER.contains("highway ~ track|path|cycleway|footway|living_street|pedestrian"))
        assertTrue(AddTrafficSignType.DEFAULT_ELEMENT_FILTER.contains(TrafficSignAccess.ELEMENT_FILTER_FRAGMENT))
        for (h in EXCLUDED.filter { it.endsWith("_link") || it in listOf("motorway", "trunk", "bridleway", "steps") }) {
            assertFalse(AddTrafficSignType.DEFAULT_ELEMENT_FILTER.contains(h), h)
        }
        // the quest uses exactly this text if nothing is configured
        assertTrue(asked(*hw("track")))
        assertFalse(asked(*hw("residential")))
    }

    /* --------------------------------------- warning for "no signs" --------------------------------------- */

    private val catalogue = TrafficSignTestData.catalogueDE
    private val F = TrafficSignDirection.FORWARD
    private val B = TrafficSignDirection.BACKWARD

    private fun state(tags: Map<String, String>, forward: DirectionSelection, backward: DirectionSelection): TrafficSignWayEditorState {
        val original = WaySignSelection.of(tags, catalogue)
        val selection = WaySignSelection(forward, backward)
        return TrafficSignWayEditorState(
            catalogue, original, selection, selection.toWaySigns(original).deriveWayTags(tags),
            selection.replacesSignsWithNone(tags, catalogue), tags,
        ) { _, _ -> }
    }

    private val NONE = DirectionSelection.None
    private val NOT = DirectionSelection.NotSurveyed

    @Test fun `none with access tags warns and lists the tags`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle" to "destination", "foot" to "yes", "name" to "x")
        val w = state(tags, NONE, NONE).noneAccessWarning
        assertNotNull(w)
        assertEquals(listOf(F, B), w.directions)
        assertEquals(mapOf("motor_vehicle" to "destination"), w.tags)
        assertTrue(state(tags, NONE, NONE).needsConfirmation)
    }

    @Test fun `no warning without relevant access tags`() {
        for (tags in listOf(
            mapOf("highway" to "residential"),
            mapOf("highway" to "residential", "access" to "yes", "foot" to "yes", "bicycle" to "yes", "motor_vehicle" to "yes"),
            mapOf("highway" to "residential", "access:conditional" to "no @ (Su)"),
        )) {
            assertNull(state(tags, NONE, NONE).noneAccessWarning, tags.toString())
            assertFalse(state(tags, NONE, NONE).needsConfirmation, tags.toString())
        }
    }

    @Test fun `no warning for other answers`() {
        val tags = mapOf("highway" to "residential", "access" to "private")
        assertNull(state(tags, parseSigned("DE:250"), parseSigned("DE:250")).noneAccessWarning)
        assertNull(state(tags, NOT, NOT).noneAccessWarning)
        assertNull(state(tags, parseSigned("DE:250"), NOT).noneAccessWarning)
    }

    @Test fun `warning only for the direction that gets none and its access tags`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle:backward" to "no", "bicycle:forward" to "designated", "access" to "private")
        val forward = state(tags, NONE, NOT).noneAccessWarning!!
        assertEquals(listOf(F), forward.directions)
        assertEquals(mapOf("access" to "private", "bicycle:forward" to "designated"), forward.tags)
        val backward = state(tags, NOT, NONE).noneAccessWarning!!
        assertEquals(listOf(B), backward.directions)
        assertEquals(mapOf("access" to "private", "motor_vehicle:backward" to "no"), backward.tags)
        // only directional tags of the other direction: no warning
        val other = mapOf("highway" to "residential", "motor_vehicle:backward" to "no")
        assertNull(state(other, NONE, NOT).noneAccessWarning)
        assertNotNull(state(other, NOT, NONE).noneAccessWarning)
    }

    @Test fun `existing none that is left alone does not warn, also written as no`() {
        for (existing in listOf("none", "no")) {
            val tags = mapOf("highway" to "residential", "access" to "private", "traffic_sign" to existing)
            val original = WaySignSelection.of(tags, catalogue)
            assertNull(state(tags, original.forward, original.backward).noneAccessWarning, existing)
            // one direction newly none, the other one stays none: only the new one is mentioned
        }
        val tags = mapOf("highway" to "residential", "access" to "private", "traffic_sign:forward" to "none")
        assertEquals(listOf(B), state(tags, NONE, NONE).noneAccessWarning!!.directions)
        assertNull(state(tags, NONE, NOT).noneAccessWarning)
    }

    @Test fun `none replacing yes or signs with access tags warns`() {
        val yes = mapOf("highway" to "residential", "access" to "private", "traffic_sign" to "yes")
        assertNotNull(state(yes, NONE, NONE).noneAccessWarning)
        val signed = mapOf("highway" to "residential", "hgv" to "no", "traffic_sign" to "DE:250")
        assertNotNull(state(signed, NONE, NONE).noneAccessWarning)
    }

    @Test fun `the warning changes nothing and the answer can be saved`() {
        val tags = mapOf("highway" to "residential", "motor_vehicle" to "destination", "access:conditional" to "no @ (Su)")
        val s = state(tags, NONE, NONE)
        assertNotNull(s.noneAccessWarning)
        // cancel: nothing is applied by the state; confirm: only the traffic sign is written
        val builder = StringMapChangesBuilder(tags)
        s.tagging.applyTo(builder)
        val changes = builder.create().changes
        assertEquals(setOf("traffic_sign"), changes.map { it.key }.toSet())
        assertEquals("none", s.waySigns.forward?.toOsmValue())
        assertTrue(s.hasChanges)
        // the access tags are never part of the changes of any answer
        for (answer in listOf(NONE to NONE, NONE to NOT, NOT to NONE)) {
            val c = state(tags, answer.first, answer.second).tagging.changes
            assertTrue(c.all { it.key.startsWith("traffic_sign") }, c.toString())
        }
    }

    private fun parseSigned(v: String) = DirectionSelection.Signed(parseDE(v))
}
