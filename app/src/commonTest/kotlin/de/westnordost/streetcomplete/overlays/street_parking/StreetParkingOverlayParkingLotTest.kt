package de.westnordost.streetcomplete.overlays.street_parking

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.DatabaseImpl
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseConfigurator
import de.westnordost.streetcomplete.data.initialize
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.edits.create.RevertCreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.edits.update_tags.createChanges
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolygonsGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPolylinesGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataRepository
import de.westnordost.streetcomplete.data.osm.mapdata.Node
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.overlays.OverlayStyle
import de.westnordost.streetcomplete.osm.traffic_calming.LaneNarrowingTrafficCalming
import de.westnordost.streetcomplete.quests.custom.CustomQuestList
import de.westnordost.streetcomplete.quests.osmose.OsmoseDao
import de.westnordost.streetcomplete.quests.parking_type.AddParkingType
import de.westnordost.streetcomplete.quests.parking_type.ParkingType
import de.westnordost.streetcomplete.quests.questTypeRegistry
import de.westnordost.streetcomplete.resources.*
import de.westnordost.streetcomplete.screens.main.map.layers.StyledElement
import de.westnordost.streetcomplete.screens.main.map.layers.isDisabled
import de.westnordost.streetcomplete.screens.main.map.layers.toElementKey
import de.westnordost.streetcomplete.screens.main.map.layers.toGeoJsonFeatures
import de.westnordost.streetcomplete.testutils.TestMapDataWithGeometry
import de.westnordost.streetcomplete.testutils.inMemoryPrefs
import de.westnordost.streetcomplete.testutils.node
import de.westnordost.streetcomplete.testutils.p
import de.westnordost.streetcomplete.testutils.pGeom
import de.westnordost.streetcomplete.testutils.rel
import de.westnordost.streetcomplete.testutils.way
import de.westnordost.streetcomplete.testutils.waysAsMembers
import de.westnordost.streetcomplete.ui.util.measure.ArSupportChecker
import dev.mokkery.mock
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StreetParkingOverlayParkingLotTest {

    private val overlay = StreetParkingOverlay()

    private val road = way(1, listOf(1, 2), mapOf("highway" to "residential"))
    private val lot = way(2, listOf(3, 4, 5, 3), mapOf("amenity" to "parking"))
    private val lotNode = node(6, tags = mapOf("amenity" to "parking"))
    private val lotRelation = rel(3, waysAsMembers(listOf(7), "outer"), mapOf("type" to "multipolygon", "amenity" to "parking"))
    private val choker = node(8, tags = mapOf("traffic_calming" to "choker"))

    private val styles = overlay
        .getStyledElements(TestMapDataWithGeometry(listOf(road, lot, lotNode, lotRelation, choker)))
        .associate { it.first.key to it.second }

    /* ------------------------------------ selection ------------------------------------ */

    @Test fun `parking ways, relations and nodes are shown and selectable`() {
        assertIs<OverlayStyle.Polygon>(styles[lot.key])
        assertIs<OverlayStyle.Polygon>(styles[lotRelation.key])
        assertIs<OverlayStyle.Point>(styles[lotNode.key])
        for (key in listOf(lot.key, lotRelation.key, lotNode.key)) {
            assertFalse(styles[key]!!.disabled, key.toString())
        }
    }

    @Test fun `multipolygon parking map feature resolves to the relation`() {
        val geometry = ElementPolygonsGeometry(listOf(listOf(p(0.0, 0.0), p(0.0, 1.0), p(1.0, 1.0), p(0.0, 0.0))), p(0.5, 0.5))
        val features = StyledElement(lotRelation, geometry, styles[lotRelation.key]!!).toGeoJsonFeatures()
        assertTrue(features.isNotEmpty())
        for (f in features) {
            assertFalse(f.properties.isDisabled())
            assertEquals(ElementKey(ElementType.RELATION, 3), f.properties.toElementKey())
        }
    }

    @Test fun `roads remain selectable`() {
        val style = styles[road.key]
        assertIs<OverlayStyle.Polyline>(style)
        assertFalse(style.disabled)
        val geometry = ElementPolylinesGeometry(listOf(listOf(p(0.0, 0.0), p(0.0, 1.0))), p(0.0, 0.5))
        val features = StyledElement(road, geometry, style).toGeoJsonFeatures()
        assertTrue(features.all { !it.properties.isDisabled() && it.properties.toElementKey() == road.key })
    }

    @Test fun `form type per element`() {
        assertEquals(StreetParkingFormType.ROAD, getStreetParkingFormType(road))
        assertEquals(StreetParkingFormType.PARKING_LOT, getStreetParkingFormType(lot))
        assertEquals(StreetParkingFormType.PARKING_LOT, getStreetParkingFormType(lotNode))
        assertEquals(StreetParkingFormType.PARKING_LOT, getStreetParkingFormType(lotRelation))
        assertEquals(StreetParkingFormType.TRAFFIC_CALMING, getStreetParkingFormType(choker))
        assertEquals(StreetParkingFormType.CREATE, getStreetParkingFormType(null))
        // a service road that is also tagged as parking is still edited as road
        assertEquals(StreetParkingFormType.ROAD, getStreetParkingFormType(way(9, listOf(1, 2), mapOf("highway" to "service", "amenity" to "parking"))))
        // a parking area tagged as highway area is a parking lot
        assertEquals(StreetParkingFormType.PARKING_LOT, getStreetParkingFormType(way(10, listOf(1, 2, 3, 1), mapOf("highway" to "service", "area" to "yes", "amenity" to "parking"))))
        assertEquals(null, getStreetParkingFormType(node(11, tags = mapOf("amenity" to "bench"))))
    }

    /* ------------------------------------ summary -------------------------------------- */

    @Test fun `summary shows only set tags in fixed order`() {
        val summary = getParkingLotSummary(mapOf(
            "amenity" to "parking", "capacity" to "42", "access" to "customers", "surface" to "asphalt"
        ))
        assertEquals(listOf(Res.string.parking_lot_access to "customers", Res.string.parking_lot_capacity to "42"), summary)
        assertEquals(ParkingType.MULTI_STOREY, getParkingType(mapOf("parking" to "multi-storey")))
        assertEquals(null, getParkingType(mapOf("parking" to "carports")))
    }

    /* ------------------------------------ creation ------------------------------------- */

    @Test fun `new parking node gets only amenity and parking type`() {
        val position = LatLon(1.0, 2.0)
        for (type in CREATABLE_PARKING_TYPES) {
            val action = createParkingNodeAction(position, type)
            assertEquals(mapOf("amenity" to "parking", "parking" to type.osmValue), action.tags)
            assertEquals(position, action.position)
            assertTrue(action.insertIntoWays.isEmpty())
        }
    }

    @Test fun `street side and lane parking cannot be created as node`() {
        assertFalse(ParkingType.STREET_SIDE in CREATABLE_PARKING_TYPES)
        assertFalse(ParkingType.LANE in CREATABLE_PARKING_TYPES)
    }

    @Test fun `created parking node edit can be applied and reverted`() {
        val repos = mock<MapDataRepository>()
        val provider = ElementIdProvider(listOf(ElementKey(ElementType.NODE, -1L)))
        val action = createParkingNodeAction(LatLon(1.0, 2.0), ParkingType.SURFACE)
        val created = action.createUpdates(repos, provider).creations.single() as Node
        assertEquals(mapOf("amenity" to "parking", "parking" to "surface"), created.tags)
        assertEquals(StreetParkingFormType.PARKING_LOT, getStreetParkingFormType(created))

        val revert = action.createReverted(ElementIdProvider(listOf(ElementKey(ElementType.NODE, -2L))))
        assertIs<RevertCreateNodeAction>(revert)
    }

    @Test fun `traffic calming is only selectable on a road, parking always`() {
        val onRoad = NewStreetParkingFeature.selectable(isOnRoad = true)
        val offRoad = NewStreetParkingFeature.selectable(isOnRoad = false)
        for (t in LaneNarrowingTrafficCalming.entries) {
            assertTrue(NewStreetParkingFeature.TrafficCalming(t) in onRoad)
            assertFalse(NewStreetParkingFeature.TrafficCalming(t) in offRoad)
        }
        for (t in CREATABLE_PARKING_TYPES) {
            assertTrue(NewStreetParkingFeature.Parking(t) in onRoad)
            assertTrue(NewStreetParkingFeature.Parking(t) in offRoad)
        }
    }

    @Test fun `new feature items survive serialization for last picked`() {
        for (item in NewStreetParkingFeature.all) {
            val json = Json.encodeToString(NewStreetParkingFeature.serializer(), item)
            assertEquals(item, Json.decodeFromString(NewStreetParkingFeature.serializer(), json))
        }
    }

    /* ------------------------------------ quests --------------------------------------- */

    @Test fun `parking lot quests exist and apply to a newly created node`() {
        val connection = BundledSQLiteDriver().open(":memory:")
        try {
            Prefs.preferences = inMemoryPrefs()
            val database = DatabaseImpl(connection).apply { initialize(StreetCompleteDatabaseConfigurator) }
            val registry = questTypeRegistry(
                arSupportChecker = object : ArSupportChecker { override fun invoke() = false },
                getCountryInfoByLocation = { CountryInfo("DE", emptyList()) },
                getCountryOrSubdivisionCode = { null },
                getFeature = { null },
                osmoseDao = OsmoseDao(database, Prefs.preferences),
                customQuestList = CustomQuestList(),
            )
            val types = PARKING_LOT_QUEST_TYPE_NAMES.associateWith { registry.getByName(it) as? OsmElementQuestType<*> }
            types.forEach { (name, type) -> assertNotNull(type, name) }

            val newNode = node(-1, tags = mapOf("amenity" to "parking", "parking" to "surface"))
            // access is unknown on new nodes -> asked; fee/capacity depend on access -> not yet
            assertEquals(true, types["AddParkingAccess"]!!.isApplicableTo(newNode))
            assertEquals(false, types["AddParkingType"]!!.isApplicableTo(newNode))
            assertEquals(false, types["AddParkingFee"]!!.isApplicableTo(newNode))
            // parking type is asked for multipolygons without type
            assertEquals(true, types["AddParkingType"]!!.isApplicableTo(lotRelation))
        } finally {
            connection.close()
        }
    }

    @Test fun `answering a quest results in a tag update of the element`() {
        val tags = lot.tags.toMutableMap()
        val builder = StringMapChangesBuilder(tags)
        AddParkingType().applyAnswerTo(ParkingType.UNDERGROUND, builder, pGeom(), 0L)
        builder.create().applyTo(tags)
        // same conversion as in ParkingLotOverlayForm
        val action = UpdateElementTagsAction(lot, tags.createChanges(lot.tags).create())
        val newTags = lot.tags.toMutableMap().also { action.changes.applyTo(it) }
        assertEquals(mapOf("amenity" to "parking", "parking" to "underground"), newTags)
    }
}
