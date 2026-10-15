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
import de.westnordost.streetcomplete.util.ktx.equalsInOsm
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.enlargedBy
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
    private fun controller(mapSource: MapDataWithEditsSource = source) = OsmQuestController(dao, mapSource, notes,
        QuestTypeRegistry({ listOf(202 to type) }), lazyOf(boundaries), MapSettings())

    @Test fun `bbox refresh loads surroundings once and still sees updates after invalidation`() {
        repeat(100) { data.putElement(f.road.copy(id = 100L + it)) }
        val cache = crossingTestCache(data, warm = true)
        var queries = 0
        val bulk = object : MapDataWithEditsSource by source, MapDataAreasSource {
            override fun getMapDataForAreas(areas: List<BoundingBox>): MutableMapDataWithGeometry {
                error("bbox refresh must not use per-road area groups")
            }
            override fun getMapDataWithGeometry(bbox: BoundingBox): MutableMapDataWithGeometry {
                queries++
                return cache.getMapDataWithGeometry(bbox)
            }
        }
        val controller = controller(bulk)
        assertTrue(controller.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty())
        assertEquals(1, queries)
        val removed = (100L until 200L).map { ElementKey(ElementType.WAY, it) }
        cache.update(deletedKeys = removed)
        data.ways.filter { it.id >= 100 }.toList().forEach { data.remove(ElementType.WAY, it.id) }
        queries = 0
        assertEquals(1, controller.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).size)
        assertEquals(1, queries)
    }

    @Test fun `bbox refresh and cache-backed source create exactly the same quest keys and positions`() {
        data.putElement(node(8, p(-0.002, 0.003)))
        data.putElement(node(9, p(0.002, 0.003)))
        data.putElement(way(40, listOf(8, 9), mapOf("waterway" to "ditch")))
        data.putElement(node(10, p(0.001, -0.002)))
        data.putElement(node(11, p(0.001, 0.004)))
        data.putElement(way(30, listOf(10, 11), mapOf("highway" to "service")))
        data.ways.toList().forEach { w -> data.put(w, ElementGeometryCreator().create(w, data)) }
        val oldCache = crossingTestCache(data, warm = true)
        every { source.getMapDataWithGeometry(any()) } calls { (bounds: BoundingBox) -> oldCache.getMapDataWithGeometry(bounds) }
        val expected = controller().getAllInBBox(area, listOf(type), refreshCrossingQuests = true).map { it.key to it.position }.toSet()
        val newCache = crossingTestCache(data, warm = true)
        var areaQueries = 0
        val bulk = object : MapDataWithEditsSource by source, MapDataAreasSource {
            override fun getMapDataForAreas(areas: List<BoundingBox>): MutableMapDataWithGeometry {
                areaQueries++
                return newCache.getMapDataForAreas(areas)
            }
            override fun getMapDataWithGeometry(bbox: BoundingBox) = newCache.getMapDataWithGeometry(bbox)
        }
        val actual = controller(bulk).getAllInBBox(area, listOf(type), refreshCrossingQuests = true).map { it.key to it.position }.toSet()
        assertEquals(3, expected.size)
        assertEquals(expected, actual)
        assertEquals(0, areaQueries)
    }

    @Test fun `identical viewport refresh loads enlarged surroundings only once per run`() {
        // Identical bounds, but both ways and their references must remain in the data.
        data.putElement(f.road.copy(id = 30))
        val requests = mutableListOf<BoundingBox>()
        every { source.getMapDataWithGeometry(any()) } calls { (bounds: BoundingBox) ->
            requests.add(bounds)
            data
        }
        assertTrue(ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty()) // third way prevents editing
        assertEquals(1, requests.size)
        assertEquals(area.enlargedBy(500.0), requests.single())
        requests.clear()
        data.boundingBox = null
        data.remove(ElementType.WAY, 30)
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertEquals(1, requests.size)
        assertEquals(f.road.id, quest.elementId)
    }

    @Test fun `overlapping roads still yield outside tile candidates and relation checks`() {
        val shared = f.sharedCrossing()
        data.putElement(node(8, p(-0.002, 0.003)))
        data.putElement(node(9, p(0.002, 0.003)))
        val secondWater = way(40, listOf(8, 9), mapOf("waterway" to "ditch"))
        data.putElement(secondWater)
        val secondRoad = way(30, listOf(1, 4), mapOf("highway" to "service"))
        // Do not introduce a third way at the shared ford.
        data.putElement(node(10, p(0.001, -0.002)))
        data.putElement(node(11, p(0.001, 0.004)))
        data.putElement(secondRoad.copy(nodeIds = listOf(10, 11)))
        data.putElement(rel(100, listOf(member(ElementType.NODE, shared.road.nodeIds[2]))))
        data.ways.toList().forEach { w -> data.put(w, ElementGeometryCreator().create(w, data)) }
        val requests = mutableListOf<BoundingBox>()
        every { source.getMapDataWithGeometry(any()) } calls { (bounds: BoundingBox) -> requests.add(bounds); data }
        val quests = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true)
        assertTrue(quests.none { it.elementId == f.road.id }) // relation forbids the shared ford
        assertEquals(2, quests.count { it.elementId == secondRoad.id })
        assertEquals(1, requests.size)
        assertEquals(area.enlargedBy(500.0), requests.single())
    }

    @Test fun `cleared map data during refresh does not leave stale crossing pins`() {
        data.putElement(f.road.copy(id = 30))
        var requests = 0
        every { source.getMapDataWithGeometry(any()) } calls {
            requests++
            mapListener.onCleared()
            data
        }
        assertTrue(ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty())
        assertEquals(1, requests)
        assertTrue(dao.getAllInBBox(area, listOf(type.name)).isEmpty())
    }

    @Test fun `combined OSM bridge removes both persisted pins and undo restores the same keys`() {
        data.toList().forEach { data.remove(it.type, it.id) }
        data.putAll(osmCrossingPracticeData(982009820, 1385223359, 1385223360))
        data.ways.toList().forEach { w -> data.put(w, ElementGeometryCreator().create(w, data)) }
        every { source.getWaysForNode(any()) } calls { (id: Long) -> data.ways.filter { id in it.nodeIds } }
        val bounds = BoundingBox(47.75, 9.10, 47.77, 9.13)
        val original = MutableMapDataWithGeometry(data)
        val quests = ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true)
        assertEquals(2, quests.size)
        val keys = quests.map { it.key }.toSet()
        assertEquals(2, keys.size)
        val originalListener = mapListener
        assertEquals(keys, controller().getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).map { it.key }.toSet())
        mapListener = originalListener
        val crossing = findWaterwayCrossings(data, quests.first().position, 1.0).single()
        val rp = crossing.roadPositions
        val action = SetWaterwayStructureAction(crossing, WaterwayStructure.BRIDGE, listOf(
            de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtPoint(rp[1]),
            de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtPoint(rp[rp.lastIndex - 1])))
        val changes = action.createUpdates(f.repo, f.idProvider())
        val updates = MutableMapDataWithGeometry()
        (changes.creations + changes.modifications).forEach { data.putElement(it) }
        (changes.creations + changes.modifications).forEach { e ->
            val g = ElementGeometryCreator().create(e, data); data.put(e, g); updates.put(e, g)
        }
        val deletedPins = mutableSetOf<de.westnordost.streetcomplete.data.quest.OsmQuestKey>()
        ctrl.addListener(object : OsmQuestSource.Listener {
            override fun onUpdated(added: Collection<OsmQuest>, deleted: Collection<de.westnordost.streetcomplete.data.quest.OsmQuestKey>) { deletedPins.addAll(deleted) }
            override fun onInvalidated() {}
        })
        mapListener.onUpdated(updates, emptyList())
        assertTrue(ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).isEmpty())
        assertTrue(deletedPins.containsAll(keys))
        keys.forEach { assertNull(dao.get(it)) }
        changes.creations.forEach { data.remove(it.type, it.id) }
        original.forEach { e -> data.put(e, original.getGeometry(e.type, e.id)) }
        mapListener.onUpdated(data, changes.creations.map { it.key })
        assertEquals(keys, ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).map { it.key }.toSet())
    }

    @Test fun `partial bridge and upload ID replacement leave exactly the other crossing pin`() {
        data.putElement(node(1, p(0.0, -0.004)))
        data.put(f.road, ElementGeometryCreator().create(f.road, data))
        data.putElement(node(7, p(-0.002, 0.001))); data.putElement(node(8, p(0.002, 0.001)))
        val water = way(30, listOf(7, 8), mapOf("waterway" to "ditch"))
        data.put(water, ElementGeometryCreator().create(water, data))
        every { source.getWaysForNode(any()) } calls { (id: Long) -> data.ways.filter { id in it.nodeIds } }
        val initial = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true)
        assertEquals(2, initial.size)
        val edits = f.bridge.copy(crossing = findWaterwayCrossings(data, p(), 1.0).single()).createUpdates(f.repo, f.idProvider())
        fun notify(elements: List<Element>, deleted: List<ElementKey>) {
            elements.forEach { data.putElement(it) }
            val updates = MutableMapDataWithGeometry()
            elements.forEach { e -> val g = ElementGeometryCreator().create(e, data); data.put(e, g); updates.put(e, g) }
            mapListener.onUpdated(updates, deleted)
        }
        notify(edits.creations + edits.modifications, emptyList())
        val remaining = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertTrue(remaining.position.equalsInOsm(p(0.0, 0.001)))
        assertTrue(remaining.elementId < 0)
        initial.forEach { assertNull(dao.get(it.key)) }
        val temporary = data.filter { it.id < 0 }.toList()
        val ids = temporary.associate { it.key to (100000L - it.id) }
        val replacements = data.mapNotNull { element -> when (element) {
            is Node -> if (element.id < 0) element.copy(id = ids.getValue(element.key)) else null
            is Way -> if (element.id < 0 || element.nodeIds.any { it < 0 }) element.copy(
                id = ids[element.key] ?: element.id,
                nodeIds = element.nodeIds.map { ids[ElementKey(ElementType.NODE, it)] ?: it }) else null
            is Relation -> null
        } }
        temporary.forEach { data.remove(it.type, it.id) }
        notify(replacements, temporary.map { it.key })
        val uploaded = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertEquals(remaining.position, uploaded.position)
        assertEquals(remaining.key.discriminator, uploaded.key.discriminator)
        assertTrue(uploaded.elementId > 0)
        assertNull(dao.get(remaining.key))
        assertEquals(uploaded.position, dao.get(uploaded.key)!!.position)
    }

    @Test fun `legacy single pin is replaced with independent crossing keys`() {
        val legacy = BasicOsmQuestDaoEntry(ElementType.WAY, f.road.id, type.name, p())
        dao.put(legacy)
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertTrue(quest.key.discriminator.isNotEmpty())
        assertNull(dao.get(legacy.key))
        assertEquals(quest.position, dao.get(quest.key)!!.position)
    }

    @Test fun `ford retirement is independent of geographic position and segment direction`() {
        for (i in 0 until 100) {
            dao.clear()
            val fixture = WaterwayCrossingTestData()
            data = fixture.data
            val origin = p(20.0 + i * 0.5123, 10.0 + i * 0.314)
            val angle = kotlin.math.PI * i / 101
            data.nodes.toList().forEach { n ->
                val lat = n.position.latitude; val lon = n.position.longitude
                data.putElement(n.copy(position = p(origin.latitude + lat * kotlin.math.cos(angle) - lon * kotlin.math.sin(angle),
                    origin.longitude + lat * kotlin.math.sin(angle) + lon * kotlin.math.cos(angle))))
            }
            data.ways.toList().forEach { w -> data.put(w, ElementGeometryCreator().create(w, data)) }
            val bounds = BoundingBox(origin.latitude - 0.01, origin.longitude - 0.01, origin.latitude + 0.01, origin.longitude + 0.01)
            val c = findWaterwayCrossings(data, origin, 10.0).single()
            val quest = ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).single()
            val changes = CreateWaterwayFordAction(c).createUpdates(fixture.repo, fixture.idProvider())
            (changes.creations + changes.modifications).forEach { data.putElement(it) }
            (changes.creations + changes.modifications).forEach { e -> data.put(e, ElementGeometryCreator().create(e, data)) }
            assertTrue(ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).isEmpty(), "case $i points=" + uniqueCrossingPoints(
                data.getWay(c.road.id)!!.nodeIds.map { data.getNode(it)!!.position },
                data.getWay(c.waterway.id)!!.nodeIds.map { data.getNode(it)!!.position }) + " expected=" + c.position)
            assertNull(dao.get(quest.key), "case $i")
        }
    }

    @Test fun `bridge undo culvert undo ford removes persisted quest and notifies visible cache`() {
        // Nonzero coordinates expose spherical endpoint rounding hidden by equatorial fixtures.
        val origin = p(22.0492, 11.256)
        val angle = kotlin.math.PI * 4 / 101
        data.nodes.toList().forEach { n ->
            val lat = n.position.latitude; val lon = n.position.longitude
            data.putElement(n.copy(position = p(origin.latitude + lat * kotlin.math.cos(angle) - lon * kotlin.math.sin(angle),
                origin.longitude + lat * kotlin.math.sin(angle) + lon * kotlin.math.cos(angle))))
        }
        data.ways.toList().forEach { w -> data.put(w, ElementGeometryCreator().create(w, data)) }
        val bounds = BoundingBox(origin.latitude - 0.01, origin.longitude - 0.01, origin.latitude + 0.01, origin.longitude + 0.01)
        val original = MutableMapDataWithGeometry(data)
        val crossing = findWaterwayCrossings(data, origin, 10.0).single()
        val quest = ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).single()
        val deletedPins = mutableListOf<de.westnordost.streetcomplete.data.quest.OsmQuestKey>()
        ctrl.addListener(object : OsmQuestSource.Listener {
            override fun onUpdated(added: Collection<OsmQuest>, deleted: Collection<de.westnordost.streetcomplete.data.quest.OsmQuestKey>) { deletedPins.addAll(deleted) }
            override fun onInvalidated() {}
        })
        every { source.getWaysForNode(any()) } calls { (id: Long) -> data.ways.filter { id in it.nodeIds } }
        for (structure in listOf(WaterwayStructure.BRIDGE, WaterwayStructure.CULVERT)) {
            val positions = if (structure == WaterwayStructure.BRIDGE) crossing.roadPositions else crossing.waterwayPositions
            val i = if (structure == WaterwayStructure.BRIDGE) 1 else 0
            val cuts = (if (structure == WaterwayStructure.BRIDGE) listOf(0.25, 0.75) else listOf(0.4, 0.6)).map { de.westnordost.streetcomplete.data.osm.edits.split_way.SplitAtLinePosition(positions[i], positions[i + 1], it) }
            val changes = SetWaterwayStructureAction(crossing, structure, cuts).createUpdates(f.repo, f.idProvider())
            val updates = MutableMapDataWithGeometry()
            (changes.creations + changes.modifications).forEach { data.putElement(it) }
            (changes.creations + changes.modifications).forEach { e -> val g = ElementGeometryCreator().create(e, data); data.put(e, g); updates.put(e, g) }
            mapListener.onUpdated(updates, emptyList())
            assertTrue(ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).isEmpty())
            assertNull(dao.get(quest.key))
            assertTrue(quest.key in deletedPins)
            changes.creations.forEach { data.remove(it.type, it.id) }
            original.forEach { e -> data.put(e, original.getGeometry(e.type, e.id)) }
            mapListener.onUpdated(data, changes.creations.map { it.key })
            assertEquals(quest.position, ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).single().position)
        }
        val changes = CreateWaterwayFordAction(crossing).createUpdates(f.repo, f.idProvider())
        val ford = changes.creations.filterIsInstance<Node>().single()
        assertEquals(mapOf("ford" to "yes"), ford.tags)
        assertEquals(crossing.position, ford.position)
        assertEquals(setOf(crossing.road.id, crossing.waterway.id), changes.modifications.map { it.id }.toSet())
        changes.modifications.filterIsInstance<Way>().forEach { way ->
            val before = original.getWay(way.id)!!
            assertEquals(before.tags, way.tags)
            assertEquals(before.nodeIds, way.nodeIds.filter { it != ford.id })
            assertEquals(1, way.nodeIds.count { it == ford.id })
        }
        val updates = MutableMapDataWithGeometry()
        (changes.creations + changes.modifications).forEach { data.putElement(it) }
        (changes.creations + changes.modifications).forEach { e -> val g = ElementGeometryCreator().create(e, data); data.put(e, g); updates.put(e, g) }
        deletedPins.clear()
        mapListener.onUpdated(updates, emptyList())
        assertTrue(ctrl.getAllInBBox(bounds, listOf(type), refreshCrossingQuests = true).isEmpty())
        assertNull(dao.get(quest.key))
        assertTrue(quest.key in deletedPins)
        assertNull(ctrl.get(quest.key))
    }

    @Test fun `stale quest is removed on direct and dynamic cached lookup`() {
        for (dynamic in listOf(false, true)) {
            dao.clear()
            data = MutableMapDataWithGeometry(f.data)
            val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
            data.putElement(f.road.copy(tags = f.road.tags + ("bridge" to "yes")))
            if (dynamic) dao.clear()
            // No map update notification: simulate a change between cache lookup and opening.
            assertNull(ctrl.getValidated(quest))
            assertNull(dao.get(quest.key))
            assertNull(ctrl.get(quest.key))
        }
    }

    @Test fun `ford only shared crossing is never created and stale pins are retired`() {
        f.sharedCrossing()
        assertTrue(ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty())
        val stale = OsmQuest(type, ElementType.WAY, f.road.id, ElementPointGeometry(p()))
        dao.put(stale)
        assertNull(ctrl.getValidated(stale))
        assertNull(dao.get(stale.key))
    }

    @Test fun `irrelevant way and node tag updates do not rerun crossing queries`() {
        var queries = 0
        every { source.getMapDataWithGeometry(any()) } calls { queries++; data }
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
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
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
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
        val otherQuest = ctrl.getAllInBBox(otherTile, listOf(type), refreshCrossingQuests = true).single()
        val quest = ctrl.getAllInBBox(firstTile, listOf(type), refreshCrossingQuests = true).single()
        assertEquals(p(), quest.position)
        assertEquals(otherQuest, ctrl.getAllInBBox(otherTile, listOf(type), refreshCrossingQuests = true).single())
        assertEquals(quest.position, dao.get(quest.key)!!.position)
        assertEquals(otherQuest, controller().getAllInBBox(otherTile, listOf(type), refreshCrossingQuests = true).single())
        assertNotEquals(quest.key, otherQuest.key)
        assertEquals(quest, controller().getAllInBBox(firstTile, listOf(type), refreshCrossingQuests = true).single())
    }

    @Test fun `incomplete counterpart data never retargets or deletes a saved pin`() {
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        data.remove(ElementType.WAY, f.water.id)
        data.putElement(node(7, p(-0.002, 0.001)))
        data.putElement(node(8, p(0.002, 0.001)))
        val other = way(30, listOf(7, 8), mapOf("waterway" to "ditch"))
        data.put(other, ElementGeometryCreator().create(other, data))
        ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true)
        assertEquals(quest.position, dao.get(quest.key)!!.position)
    }

    @Test fun `unchanged viewport skips expanded crossing queries and persistence`() {
        var queries = 0
        every { source.getMapDataWithGeometry(any()) } calls { queries++; data }
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        val before = queries
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single())
        assertEquals(before + 1, queries)
        data.putElement(f.road.copy(tags = f.road.tags + ("name" to "New name")))
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single())
        assertEquals(before + 2, queries)
    }

    @Test fun `cached area from older build generates and persists new quest without dynamic quests`() {
        val quests = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true)
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
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertNotEquals(quest.position, data.getGeometry(ElementType.WAY, f.road.id)!!.center)
        assertEquals(quest, controller().get(quest.key))
        assertEquals(quest, controller().getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single())
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
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        val changes = f.culvert.createUpdates(f.repo, f.idProvider())
        val creator = ElementGeometryCreator()
        (changes.creations + changes.modifications).forEach { data.putElement(it) }
        (changes.creations + changes.modifications).forEach { data.put(it, creator.create(it, data)) }
        assertTrue(ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty())
        assertNull(dao.get(quest.key))
        data = WaterwayCrossingTestData().data
        assertEquals(quest, ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single())
    }

    @Test fun `counterpart update notifies cached quest removal without invalidating all quest types`() {
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
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
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        assertNotNull(ctrl.get(quest.key))
        every { notes.getAllPositions(any()) } returns listOf(p())
        assertNull(ctrl.get(quest.key))
        assertTrue(ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).isEmpty())
    }

    @Test fun `get returns persisted crossing without running detection`() {
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        var mapLoads = 0
        every { source.getMapDataWithGeometry(any()) } calls {
            mapLoads++
            data
        }
        assertEquals(quest, ctrl.get(quest.key))
        assertEquals(0, mapLoads)
        ctrl.getValidated(quest)
        assertTrue(mapLoads > 0)
    }

    @Test fun `resolveWaterwayCrossing shares one map load between validate and form`() {
        val quest = ctrl.getAllInBBox(area, listOf(type), refreshCrossingQuests = true).single()
        var mapLoads = 0
        every { source.getMapDataWithGeometry(any()) } calls {
            mapLoads++
            data
        }
        every { source.getWayComplete(any()) } calls { (id: Long) ->
            data.getWay(id)?.let { way ->
                MutableMapData(listOf(way) + way.nodeIds.mapNotNull { data.getNode(it) })
            }
        }
        val first = ctrl.resolveWaterwayCrossing(quest.elementId, quest.position)
        assertNotNull(first)
        val loadsAfterFirst = mapLoads
        assertTrue(loadsAfterFirst > 0)
        assertEquals(quest, ctrl.getValidated(quest))
        assertEquals(loadsAfterFirst, mapLoads)
        assertEquals(first, ctrl.resolveWaterwayCrossing(quest.elementId, quest.position))
        assertEquals(loadsAfterFirst, mapLoads)
    }

    @Test fun `getAllInBBox without refreshCrossingQuests does not re-query crossing areas`() {
        var areaQueries = 0
        val cache = crossingTestCache(data, warm = true)
        val bulk = object : MapDataWithEditsSource by source, MapDataAreasSource {
            override fun getMapDataForAreas(areas: List<BoundingBox>): MutableMapDataWithGeometry {
                areaQueries++
                return cache.getMapDataForAreas(areas)
            }
            override fun getMapDataWithGeometry(bbox: BoundingBox) = cache.getMapDataWithGeometry(bbox)
        }
        val c = controller(bulk)
        c.getAllInBBox(area, listOf(type), refreshCrossingQuests = true)
        val afterSeed = areaQueries
        assertTrue(c.getAllInBBox(area, listOf(type), refreshCrossingQuests = false).isNotEmpty())
        assertEquals(afterSeed, areaQueries)
    }
}
