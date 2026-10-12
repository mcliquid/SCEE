package de.westnordost.streetcomplete.data.osm.osmquests

import com.russhwolf.settings.MapSettings
import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.StreetCompleteDatabaseTestCase
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.*
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryCreator
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometryEntry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.*
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing
import de.westnordost.streetcomplete.testutils.*
import de.westnordost.streetcomplete.util.countryboundaries.AllCountries
import de.westnordost.streetcomplete.util.countryboundaries.Countries
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.util.math.contains
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import kotlin.test.*

class WaterwayCrossingQuestControllerTest : StreetCompleteDatabaseTestCase() {
    private val type = AddWaterwayCrossing()
    private val f = WaterwayCrossingTestData()
    private var data = f.data
    private lateinit var dao: OsmQuestDao
    private lateinit var ctrl: OsmQuestController
    private lateinit var mapListener: MapDataWithEditsSource.Listener
    private val source = mock<MapDataWithEditsSource> {
        every { addListener(any()) } calls { (l: MapDataWithEditsSource.Listener) -> mapListener = l }
        every { getMapDataWithGeometry(any()) } calls { data }
        every { getGeometry(any(), any()) } calls { (type: ElementType, id: Long) -> data.getGeometry(type, id) }
        every { getGeometries(any()) } calls { (keys: Collection<ElementKey>) -> keys.mapNotNull { key ->
            data.getGeometry(key.type, key.id)?.let { ElementGeometryEntry(key.type, key.id, it) }
        } }
    }
    private val boundaries = object : CountryBoundaries {
        override fun getIds(position: LatLon) = emptyList<String>()
        override fun isInAny(position: LatLon, countries: Countries) = true
        override fun intersects(bbox: BoundingBox, countries: Countries) = true
    }
    private val notes = mock<NotesWithEditsSource> {
        every { getAllPositions(any()) } returns emptyList()
    }
    private val area = BoundingBox(-0.01, -0.01, 0.01, 0.01)
    override fun onDatabaseInitialized(database: Database) {
        dao = OsmQuestDao(database)
        ctrl = controller()
    }
    private fun controller() = OsmQuestController(dao, source, notes,
        QuestTypeRegistry({ listOf(202 to type) }), lazyOf(boundaries), MapSettings())

    @Test fun `irrelevant way and node tag updates do not rerun crossing queries`() {
        var queries = 0
        every { source.getMapDataWithGeometry(any()) } calls { queries++; data }
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        val before = queries
        val renamed = f.road.copy(tags = f.road.tags + ("name" to "New name"))
        val geometry = data.getWayGeometry(f.road.id)
        data.put(renamed, geometry)
        mapListener.onUpdated(MutableMapDataWithGeometry().apply { put(renamed, geometry) }, emptyList())
        val taggedNode = data.getNode(1)!!.copy(tags = mapOf("name" to "Point"))
        data.putElement(taggedNode)
        mapListener.onUpdated(MutableMapDataWithGeometry().apply { put(taggedNode, data.getNodeGeometry(1)) }, emptyList())
        assertEquals(before, queries)
        assertEquals(quest.position, dao.get(quest.key)!!.position)
    }

    @Test fun `deleting known selected counterpart retires its pin`() {
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        data.remove(ElementType.WAY, f.water.id)
        mapListener.onUpdated(MutableMapDataWithGeometry(), listOf(f.water.key))
        assertNull(dao.get(quest.key))
    }

    @Test fun `changing viewport never changes the selected crossing including after restart`() {
        data.putElement(node(4, p(0.0, 0.008)))
        data.put(f.road, ElementGeometryCreator().create(f.road, data))
        data.putElement(node(7, p(-0.002, 0.006)))
        data.putElement(node(8, p(0.002, 0.006)))
        val water = way(30, listOf(7, 8), mapOf("waterway" to "ditch"))
        data.put(water, ElementGeometryCreator().create(water, data))
        // Reproduce the real node-based spatial query, including complete selected ways.
        every { source.getMapDataWithGeometry(any()) } calls { (bbox: BoundingBox) ->
            val nodes = data.nodes.filter { bbox.contains(it.position) }
            val nodeIds = nodes.map { it.id }.toSet()
            val result = MutableMapDataWithGeometry()
            nodes.forEach { result.put(it, data.getNodeGeometry(it.id)) }
            data.ways.filter { it.nodeIds.any { id -> id in nodeIds } }.forEach { way ->
                result.put(way, data.getWayGeometry(way.id))
                way.nodeIds.forEach { id -> data.getNode(id)?.let { result.put(it, data.getNodeGeometry(id)) } }
            }
            result
        }
        val firstTile = BoundingBox(-0.0001, -0.0001, 0.0001, 0.0001)
        val otherTile = BoundingBox(-0.0001, 0.0059, 0.0001, 0.0061)
        assertTrue(ctrl.getAllInBBox(otherTile, listOf(type)).isEmpty())
        val quest = ctrl.getAllInBBox(firstTile, listOf(type)).single()
        assertEquals(p(), quest.position)
        assertTrue(ctrl.getAllInBBox(otherTile, listOf(type)).isEmpty())
        assertEquals(quest.position, dao.get(quest.key)!!.position)
        assertTrue(controller().getAllInBBox(otherTile, listOf(type)).isEmpty())
        assertEquals(quest, controller().getAllInBBox(firstTile, listOf(type)).single())
    }

    @Test fun `incomplete counterpart data never retargets or deletes a saved pin`() {
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        data.remove(ElementType.WAY, f.water.id)
        data.putElement(node(7, p(-0.002, 0.001)))
        data.putElement(node(8, p(0.002, 0.001)))
        val other = way(30, listOf(7, 8), mapOf("waterway" to "ditch"))
        data.put(other, ElementGeometryCreator().create(other, data))
        ctrl.getAllInBBox(area, listOf(type))
        assertEquals(quest.position, dao.get(quest.key)!!.position)
    }

    @Test fun `unchanged viewport skips expanded crossing queries and persistence`() {
        var queries = 0
        every { source.getMapDataWithGeometry(any()) } calls { queries++; data }
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        val before = queries
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type)).single())
        assertEquals(before + 1, queries)
        data.putElement(f.road.copy(tags = f.road.tags + ("name" to "New name")))
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type)).single())
        assertEquals(before + 2, queries)
    }

    @Test fun `cached area from older build generates and persists new quest without dynamic quests`() {
        val quests = ctrl.getAllInBBox(area, listOf(type))
        val quest = quests.single()
        assertEquals(f.road.id, quest.elementId)
        assertEquals(p(), quest.position)
        assertEquals(listOf(p()), quest.markerLocations)
        assertNotNull(dao.get(quest.key))
    }

    @Test fun `stored crossing marker survives controller restart and differs from road center`() {
        val shifted = node(4, p(0.0, 0.008))
        data.putElement(shifted)
        data.put(f.road, ElementGeometryCreator().create(f.road, data))
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        assertNotEquals(quest.position, data.getGeometry(ElementType.WAY, f.road.id)!!.center)
        assertEquals(quest, controller().get(quest.key))
        assertEquals(quest, controller().getAllInBBox(area, listOf(type)).single())
    }

    @Test fun `normal download places quest at intersection even if road center is outside download`() {
        data.putElement(node(4, p(0.0, 0.008)))
        data.put(f.road, ElementGeometryCreator().create(f.road, data))
        val smallArea = BoundingBox(-0.0001, -0.0001, 0.0001, 0.0001)
        assertFalse(smallArea.contains(data.getGeometry(ElementType.WAY, f.road.id)!!.center))
        mapListener.onReplacedForBBox(smallArea, data)
        assertEquals(p(), dao.getAllInBBox(smallArea, listOf(type.name)).single().position)
    }

    @Test fun `editing only counterpart removes quest and undo restores it`() {
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        val changes = f.culvert.createUpdates(f.repo, f.idProvider())
        val creator = ElementGeometryCreator()
        (changes.creations + changes.modifications).forEach { data.putElement(it) }
        (changes.creations + changes.modifications).forEach { data.put(it, creator.create(it, data)) }
        assertTrue(ctrl.getAllInBBox(area, listOf(type)).isEmpty())
        assertNull(dao.get(quest.key))
        data = WaterwayCrossingTestData().data
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type)).single())
    }

    @Test fun `counterpart update notifies cached quest removal without invalidating all quest types`() {
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        val listener = mock<OsmQuestSource.Listener>()
        ctrl.addListener(listener)
        val changedWater = f.water.copy(tags = f.water.tags + ("tunnel" to "culvert"))
        val geometry = data.getGeometry(ElementType.WAY, f.water.id)
        data.put(changedWater, geometry)
        val changed = MutableMapDataWithGeometry().apply { put(changedWater, geometry) }
        mapListener.onUpdated(changed, emptyList())
        assertNull(dao.get(quest.key))
        verify { listener.onUpdated(any(), listOf(quest.key)) }
        data.put(f.water, geometry)
        mapListener.onUpdated(MutableMapDataWithGeometry().apply { put(f.water, geometry) }, emptyList())
        assertNotNull(dao.get(quest.key))
        verify { listener.onUpdated(listOf(quest), any()) }
    }

    @Test fun `a note at road center does not hide the actual crossing but a note at crossing does`() {
        data.putElement(node(4, p(0.0, 0.008)))
        data.put(f.road, ElementGeometryCreator().create(f.road, data))
        val roadCenter = data.getGeometry(ElementType.WAY, f.road.id)!!.center
        every { notes.getAllPositions(any()) } returns listOf(roadCenter)
        val quest = ctrl.getAllInBBox(area, listOf(type)).single()
        assertNotNull(ctrl.get(quest.key))
        every { notes.getAllPositions(any()) } returns listOf(p())
        assertNull(ctrl.get(quest.key))
        assertTrue(ctrl.getAllInBBox(area, listOf(type)).isEmpty())
    }
}
