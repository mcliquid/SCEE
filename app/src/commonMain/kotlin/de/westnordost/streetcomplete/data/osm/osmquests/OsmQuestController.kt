package de.westnordost.streetcomplete.data.osm.osmquests

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.elementfilter.ElementsTypeFilter
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.key
import de.westnordost.streetcomplete.data.osm.mapdata.Way
import de.westnordost.streetcomplete.data.osm.edits.waterway_crossing.*
import de.westnordost.streetcomplete.util.ktx.equalsInOsm
import de.westnordost.streetcomplete.util.math.distanceToArcs
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.util.countryboundaries.AllCountries
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.quests.waterway_crossing.AddWaterwayCrossing
import de.westnordost.streetcomplete.util.Listeners
import de.westnordost.streetcomplete.util.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.util.ktx.format
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.ktx.truncateTo6Decimals
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.logs.Perf
import de.westnordost.streetcomplete.util.logs.currentPerfState
import de.westnordost.streetcomplete.util.logs.currentPerfTrace
import de.westnordost.streetcomplete.util.math.contains
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.math.enlargedBy
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Controller for managing OsmQuests. Takes care of persisting OsmQuest objects and notifying
 *  listeners about changes */
class OsmQuestController(
    private val db: OsmQuestDao,
    private val mapDataSource: MapDataWithEditsSource,
    private val notesSource: NotesWithEditsSource,
    private val questTypeRegistry: QuestTypeRegistry,
    private val countryBoundaries: Lazy<CountryBoundaries>,
    private val prefs: ObservableSettings,
) : OsmQuestSource {

    private val listeners = Listeners<OsmQuestSource.Listener>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("OsmQuestController"))

    private val allQuestTypes get() = questTypeRegistry.filterIsInstance<OsmElementQuestType<*>>()
        .sortedBy { getAnalyzePriority(it) }

    private val lock = ReentrantLock()
    private var lastCrossingQuery: Pair<BoundingBox, List<Element>>? = null

    private val wayOnlyFilterQuestTypes by lazy {
        // technically those could change if questTypeRegistry is reloaded, but that's unlikely enough to ignore it
        // the filter step is slow when called on init, probably because filters are loaded by lazy
        questTypeRegistry.filterIsInstance<OsmFilterQuestType<*>>()
            .filter { it.filter.elementsTypes.size == 1 && it.filter.elementsTypes.single() == ElementsTypeFilter.WAYS }
            .map { it.name }.toHashSet()
    }

    private val mapDataSourceListener = object : MapDataWithEditsSource.Listener {

        /** For the given elements, replace the current quests with the given ones. Called when
         *  OSM elements are updated, so the quests that reference that element need to be updated
         *  as well. */
        override fun onUpdated(updated: MapDataWithGeometry, deleted: Collection<ElementKey>) {
            val time = nowAsEpochMilliseconds()
            val start = Perf.mark()
            val crossingType = allQuestTypes.filterIsInstance<AddWaterwayCrossing>().singleOrNull()
            val questTypes = allQuestTypes.filterNot { it is AddWaterwayCrossing }
            val updatedElementKeys = updated.map { it.key }
            val beforeEvalMs = Perf.ms(start)
            val perfStats = QuestUpdatePerf()

            val deferredQuests = mutableListOf<Deferred<OsmQuest?>>()
            val evalStart = Perf.mark()

            for (element in updated) {
                val geometry = updated.getGeometry(element.type, element.id) ?: continue
                deferredQuests.addAll(createQuestsForElementDeferred(element, geometry, questTypes, perfStats))
            }
            val quests = runBlocking { deferredQuests.awaitAll().filterNotNull() }
            val questEvalMs = Perf.ms(evalStart)

            for (quest in quests) {
                Log.d(TAG, "Created ${quest.type.name} for ${quest.elementType.name}#${quest.elementId}")
            }

            var obsoleteQuestKeys: List<OsmQuestKey> = listOf()
            var visibleQuests: Collection<OsmQuest> = listOf()
            var persistMs = 0L
            lock.withLock {
                val previousQuests = db.getAllForElements(updatedElementKeys).filter { it.questTypeName != crossingType?.name }
                // quests that refer to elements that have been deleted shall be deleted
                val deleteQuestKeys = db.getAllForElements(deleted).map { it.key }

                val millis = nowAsEpochMilliseconds() - time
                Log.i(TAG, "Created ${quests.size} quests for ${updated.size} updated elements in ${millis}ms")

                obsoleteQuestKeys = getObsoleteQuestKeys(quests, previousQuests, deleteQuestKeys)
                val persistStart = Perf.mark()
                updateQuests(quests, obsoleteQuestKeys)
                persistMs = Perf.ms(persistStart)
                visibleQuests = quests.filterVisible()
            }

            val questKeysToDelete =
                if (prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false)) {
                    getDynamicallyObsoleteQuestKeys(
                        quests,
                        obsoleteQuestKeys,
                        updatedElementKeys,
                        deleted,
                        questTypes,
                    )
                } else {
                    obsoleteQuestKeys
                }
            val visibleHolder = longArrayOf(0L)
            currentPerfState().visibleQuestHolder = visibleHolder
            try {
                onUpdated(added = visibleQuests, deleted = questKeysToDelete)
            } finally {
                currentPerfState().visibleQuestHolder = null
            }
            // The crossing quest also depends on the counterpart way, which is not its anchor.
            // Refresh just this type around changed geometry, preserving the other quest caches.
            if (crossingType != null && hasCrossingChanges(updated, deleted)) {
                val affected = crossingType.cachedCandidatesForDeletedWays(deleted.filter { it.type == ElementType.WAY }.map { it.id }.toSet())
                val removedPins = lock.withLock {
                    val keys = affected.mapNotNull { candidate ->
                        db.getAllForElements(listOf(candidate.road.key)).singleOrNull {
                            it.questTypeName == crossingType.name && it.position.equalsInOsm(candidate.position)
                        }?.key
                    }
                    if (keys.isNotEmpty()) { db.deleteAll(keys); lastCrossingQuery = null }
                    keys
                }
                onUpdated(deleted = removedPins)
                val positions = updated.filter { element ->
                    element.type == ElementType.WAY || element.type == ElementType.NODE
                        && mapDataSource.getWaysForNode(element.id).any { "highway" in it.tags || "waterway" in it.tags }
                }.flatMap {
                    updated.getGeometry(it.type, it.id)?.bounds?.let { b -> listOf(b.min, b.max) }.orEmpty()
                }
                if (positions.isNotEmpty()) {
                    val bounds = positions.enclosingBoundingBox().enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)
                    val (added, removed) = refreshCrossingQuests(bounds, listOf(crossingType))
                    onUpdated(added.filterVisible(), removed)
                } else if (deleted.any { it.type == ElementType.WAY }) {
                    // Deleted counterpart geometry is unavailable; discard visible caches in this rare case.
                    onInvalidated()
                }
            }
            val totalMs = Perf.ms(start)
            currentPerfTrace()?.addMs("onUpdated", totalMs)
            val trace = currentPerfTrace()
            val id = trace?.id ?: Perf.nextId()
            val editSuffix = if (trace?.event == "upload" && trace.editIndex > 0) " edit=${trace.editIndex}" else ""
            Perf.log(
                "onUpdated#$id elements=${updated.size} questTypes=${questTypes.size} " +
                    "beforeEval=${beforeEvalMs}ms surrounding=${perfStats.surroundingMs}ms " +
                    "surroundingLoads=${perfStats.surroundingLoads} questEval=${questEvalMs}ms " +
                    "persist=${persistMs}ms updateVisibleQuests=${visibleHolder[0]}ms " +
                    "total=${totalMs}ms$editSuffix"
            )
            perfStats.slowLine()?.let { Perf.log("slowQuestType#$id $it") }
        }

        /** Replace all quests of the given types in the given bounding box with the given quests.
         *  Called on download of a quest type for a bounding box. */
        override fun onReplacedForBBox(bbox: BoundingBox, mapDataWithGeometry: MapDataWithGeometry) {
            val quests = createQuestsForBBox(bbox, mapDataWithGeometry, allQuestTypes)
            var obsoleteQuestKeys: List<OsmQuestKey> = listOf()
            var visibleQuests: Collection<OsmQuest> = listOf()
            lock.withLock {
                val previousQuests = db.getAllInBBox(bbox).filter { it.questTypeName != "AddWaterwayCrossing" || crossingResolved(it, mapDataWithGeometry) }
                obsoleteQuestKeys = getObsoleteQuestKeys(quests, previousQuests, emptyList())
                updateQuests(quests, obsoleteQuestKeys)
                visibleQuests = quests.filterVisible()
            }

            onUpdated(added = visibleQuests, deleted = obsoleteQuestKeys)
        }

        override fun onCleared() {
            lock.withLock {
                lastCrossingQuery = null
                db.clear()
            }
            listeners.forEach { it.onInvalidated() }
        }
    }

    private val notesSourceListener = object : NotesWithEditsSource.Listener {
        override fun onUpdated(added: Collection<Note>, updated: Collection<Note>, deleted: Collection<Long>) {
            onInvalidated()
        }

        override fun onCleared() {
            onInvalidated()
        }
    }

    init {
        mapDataSource.addListener(mapDataSourceListener)
        notesSource.addListener(notesSourceListener)
        instance = this
    }

    private fun createQuestsForBBox(
        bbox: BoundingBox,
        mapDataWithGeometry: MapDataWithGeometry,
        questTypes: Collection<OsmElementQuestType<*>>,
    ): Collection<OsmQuest> {
        val time = nowAsEpochMilliseconds()

        val countryBoundaries = countryBoundaries.value

        // Remove elements without tags, to be used for quests that are never applicable without
        // tags. These quests are usually OsmFilterQuestType, where questType.filter.mayEvaluateToTrueWithNoTags
        // guarantees we can skip elements without tags completely. Also those quests don't use geometry.
        // This shortcut reduces time for creating quests by ~15-30%.
        val onlyMapDataWithTags = MutableMapDataWithGeometry(mapDataWithGeometry.size).apply {
            mapDataWithGeometry.forEach { if (it.tags.isNotEmpty()) put(it, mapDataWithGeometry.getGeometry(it.type, it.id)) }
            boundingBox = mapDataWithGeometry.boundingBox
        }
        val onlyWaysWithTags = MutableMapDataWithGeometry(onlyMapDataWithTags.filter { it.type == ElementType.WAY }, emptyList())

        val deferredQuests: List<Deferred<List<OsmQuest>>> = questTypes.map { questType ->
            scope.async {
                val questsForType = ArrayList<OsmQuest>()
                val questTypeName = questType.name
                if (!countryBoundaries.intersects(bbox, questType.enabledInCountries)) {
                    Log.d(TAG, "$questTypeName: Skipped because it is disabled for this country")
                    emptyList()
                } else {
                    val questTime = nowAsEpochMilliseconds()
                    var questCount = 0
                    val mapDataToUse = if (questType is OsmFilterQuestType && !questType.filter.mayEvaluateToTrueWithNoTags) {
                        if (questType.name in wayOnlyFilterQuestTypes) onlyWaysWithTags
                        else onlyMapDataWithTags
                    } else {
                        mapDataWithGeometry
                    }
                    val candidates = if (questType is AddWaterwayCrossing) {
                        crossingCandidates(questType, mapDataToUse, bbox).asSequence().map { it.road to ElementPointGeometry(it.position) }
                    } else {
                        questType.getApplicableElements(mapDataToUse).asSequence().mapNotNull { element ->
                            mapDataWithGeometry.getGeometry(element.type, element.id)?.let { element to it }
                        }
                    }
                    for ((element, geometry) in candidates) {
                        if (!mayCreateQuest(questType, geometry, bbox)) continue
                        questsForType.add(OsmQuest(questType, element.type, element.id, geometry))
                        questCount++
                    }

                    val questSeconds = nowAsEpochMilliseconds() - questTime
                    Log.d(TAG, "$questTypeName: Found $questCount quests in ${questSeconds}ms")
                    questsForType
                }
            }
        }
        val quests = runBlocking { deferredQuests.awaitAll().flatten() }

        val seconds = (nowAsEpochMilliseconds() - time) / 1000.0
        Log.i(TAG, "Created ${quests.size} quests for bbox in ${seconds.format(1)}s")

        return quests
    }

    private fun createQuestsForElementDeferred(
        element: Element,
        geometry: ElementGeometry,
        questTypes: Collection<OsmElementQuestType<*>>,
        perfStats: QuestUpdatePerf? = null,
    ): List<Deferred<OsmQuest?>> {
        val paddedBounds = geometry.bounds.enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)
        val lazyMapData by lazy {
            val loadStart = Perf.mark()
            mapDataSource.getMapDataWithGeometry(paddedBounds).apply {
                (this as? MutableMapDataWithGeometry)?.put(element, geometry) // this is specifically for tag editor to show the current version of the element, otherwise it should not matter
            }.also {
                perfStats?.addSurrounding(Perf.ms(loadStart))
            }
        }

        return questTypes.filterNot { it is AddWaterwayCrossing }.map { questType ->
            scope.async {
                val typeStart = Perf.mark()
                var surroundingWaitMs = 0L
                val result = if (questType.enabledInCountries != AllCountries && !mayCreateQuest(questType, geometry, null)) {
                    null // check whether it's disabled before creating the quest
                } else {
                    var appliesToElement = questType.isApplicableTo(element)
                    if (appliesToElement == null) {
                        Log.d(TAG, "${questType.name} requires surrounding map data to determine applicability to ${element.type.name}#${element.id}")
                        val waitStart = Perf.mark()
                        val mapData = withContext(Dispatchers.IO) { lazyMapData }
                        surroundingWaitMs = Perf.ms(waitStart)
                        appliesToElement = questType.getApplicableElements(mapData)
                            .any { it.id == element.id && it.type == element.type }
                    }
                    if (appliesToElement) {
                        OsmQuest(questType, element.type, element.id, geometry)
                    } else null
                }
                val ownMs = (Perf.ms(typeStart) - surroundingWaitMs).coerceAtLeast(0)
                if (ownMs >= Perf.SLOW_QUEST_TYPE_MS) perfStats?.addSlow(questType.name, ownMs)
                result
            }
        }
    }

    suspend fun createNonPoiQuestsForElement(element: Element, geometry: ElementGeometry): List<OsmQuest> {
        val ordinary = createQuestsForElementDeferred(element, geometry, allQuestTypes.filter { it.dotColor == null }).awaitAll().filterNotNull()
        val crossings = if (element is Way) withContext(Dispatchers.IO) {
            val data = MutableMapDataWithGeometry(mapDataSource.getMapDataWithGeometry(geometry.bounds.enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING)))
            data.put(element, geometry)
            allQuestTypes.filterIsInstance<AddWaterwayCrossing>().flatMap { type ->
                crossingCandidates(type, data, null).filter { it.road.id == element.id }.map {
                    OsmQuest(type, element.type, element.id, ElementPointGeometry(it.position))
                }
            }
        } else emptyList()
        return ordinary + crossings
    }

    private fun getObsoleteQuestKeys(
        questsNow: Collection<OsmQuest>,
        questsPreviously: Collection<OsmQuestDaoEntry>,
        deletedQuestKeys: Collection<OsmQuestKey>
    ): List<OsmQuestKey> {
        val obsoleteQuestKeys = HashSet<OsmQuestKey>(questsPreviously.size, 0.9f)
        questsPreviously.forEach { obsoleteQuestKeys.add(it.key) }

        for (quest in questsNow) {
            obsoleteQuestKeys.remove(quest.key)
        }
        // quests that were created previously for an element but now not anymore shall be deleted
        return deletedQuestKeys + obsoleteQuestKeys
    }

    private fun getDynamicallyObsoleteQuestKeys(
        questsNow: Collection<OsmQuest>,
        persistedObsoleteQuestKeys: Collection<OsmQuestKey>,
        updatedElementKeys: Collection<ElementKey>,
        deletedElementKeys: Collection<ElementKey>,
        questTypes: Collection<OsmElementQuestType<*>>,
    ): Set<OsmQuestKey> {
        val currentlyApplicableQuestKeys = questsNow.mapTo(HashSet(questsNow.size)) { it.key }
        val questTypeNames = questTypes.map { it.name }
        val obsoleteQuestKeys = persistedObsoleteQuestKeys.toMutableSet()

        for (elementKey in updatedElementKeys) {
            for (questTypeName in questTypeNames) {
                val questKey = OsmQuestKey(elementKey.type, elementKey.id, questTypeName)
                if (questKey !in currentlyApplicableQuestKeys) obsoleteQuestKeys.add(questKey)
            }
        }
        for (elementKey in deletedElementKeys) {
            for (questTypeName in questTypeNames) {
                obsoleteQuestKeys.add(OsmQuestKey(elementKey.type, elementKey.id, questTypeName))
            }
        }
        return obsoleteQuestKeys
    }

    private fun updateQuests(questsNow: Collection<OsmQuest>, obsoleteQuestKeys: Collection<OsmQuestKey>) {
        val time = nowAsEpochMilliseconds()

        db.deleteAll(obsoleteQuestKeys)
        db.putAll(questsNow)

        val seconds = (nowAsEpochMilliseconds() - time) / 1000.0
        Log.i(TAG, "Persisted ${questsNow.size} new and removed ${obsoleteQuestKeys.size} already resolved quests in ${seconds.format(1)}s")
    }

    private fun mayCreateQuest(
        questType: OsmElementQuestType<*>,
        geometry: ElementGeometry,
        downloadedBoundingBox: BoundingBox?
    ): Boolean {
        val pos = geometry.center

        // outside downloaded area: skip
        if (downloadedBoundingBox != null && !downloadedBoundingBox.contains(pos)) return false

        // do not create quests in countries where the quest is not activated
        val countries = questType.enabledInCountries
        if (!countryBoundaries.value.isInAny(pos, countries)) return false

        return true
    }

    fun delete(key: OsmQuestKey) {
        lock.withLock { db.delete(key) }
        onUpdated(deleted = listOf(key))
    }

    override fun get(key: OsmQuestKey): OsmQuest? {
        val entry = db.get(key) ?: return null
        val geometry = mapDataSource.getGeometry(entry.elementType, entry.elementId) ?: return null
        val quest = createOsmQuest(entry, geometry) ?: return null
        if (isBlacklistedPosition(quest.position)) return null
        return if (quest.type is AddWaterwayCrossing) getValidated(quest) else quest
    }

    override fun getValidated(quest: OsmQuest): OsmQuest? {
        if (quest.type !is AddWaterwayCrossing) return get(quest.key)
        val data = MutableMapDataWithGeometry(mapDataSource.getMapDataWithGeometry(quest.position.enclosingBoundingBox(500.0)))
        mapDataSource.getWayComplete(quest.elementId)?.forEach { data.putElement(it) }
        val valid = findWaterwayCrossings(data, quest.position, 0.5).any {
            it.road.id == quest.elementId && it.position.equalsInOsm(quest.position)
        }
        if (valid) return quest
        Log.i(TAG, "Discard stale waterway crossing ${quest.key} at ${quest.position}")
        lock.withLock { lastCrossingQuery = null }
        delete(quest.key) // Removes persisted entry and notifies visible/pin caches, even for dynamic quests.
        return null
    }

    override fun getAllInBBox(bbox: BoundingBox, questTypes: Collection<QuestType>?): Collection<OsmQuest> {
        val hiddenPositions = getBlacklistedPositions(bbox)
        if (prefs.getBoolean(Prefs.DYNAMIC_QUEST_CREATION, false)) {
            val mapData = mapDataSource.getMapDataWithGeometry(bbox.enlargedBy(ApplicationConstants.QUEST_FILTER_PADDING))
            val quests = createQuestsForBBox(bbox, mapData, questTypes?.filterIsInstance<OsmElementQuestType<*>>() ?: allQuestTypes)
            return quests.filter { it.position.truncateTo6Decimals() !in hiddenPositions }
        }
        // Also generate this newly introduced quest for areas downloaded by an older build.
        // Re-evaluation handles edits to either way without depending on dynamic quests settings.
        val crossingTypes = (questTypes ?: allQuestTypes).filterIsInstance<AddWaterwayCrossing>()
        if (crossingTypes.isNotEmpty()) {
            val (_, removed) = refreshCrossingQuests(bbox, crossingTypes)
            onUpdated(deleted = removed)
        }
        val entries = db.getAllInBBox(bbox, questTypes?.map { it.name }).filter {
            it.position.truncateTo6Decimals() !in hiddenPositions
        }

        val elementKeys = HashSet<ElementKey>(entries.size)
        entries.mapTo(elementKeys) { ElementKey(it.elementType, it.elementId) }

        val geometriesByKey = mapDataSource.getGeometries(elementKeys).associateBy { it.key }

        return entries.mapNotNull { entry ->
            val geometry = geometriesByKey[ElementKey(entry.elementType, entry.elementId)]?.geometry
            createOsmQuest(entry, geometry)
        }
    }

    private fun createOsmQuest(entry: OsmQuestDaoEntry, geometry: ElementGeometry?): OsmQuest? {
        if (geometry == null) return null
        val questType = questTypeRegistry.getByName(entry.questTypeName) as? OsmElementQuestType<*> ?: return null
        // The DAO persists the crossing position. The anchor's complete line is not the quest pin.
        val questGeometry = if (questType is AddWaterwayCrossing) ElementPointGeometry(entry.position) else geometry
        return OsmQuest(questType, entry.elementType, entry.elementId, questGeometry, entry.discriminator)
    }

    private fun refreshCrossingQuests(bbox: BoundingBox, types: List<AddWaterwayCrossing>): Pair<Collection<OsmQuest>, List<OsmQuestKey>> {
        // The map-data cache finds ways through their nodes. Include a wider area so that a
        // crossing whose waterway vertices are outside the visible tile is still discovered.
        val data = mapDataSource.getMapDataWithGeometry(bbox.enlargedBy(500.0))
        val input = bbox to crossingDetectionInput(data)
        if (lock.withLock { lastCrossingQuery == input }) return emptyList<OsmQuest>() to emptyList()
        val crossings = createQuestsForBBox(bbox, data, types)
        val removed = lock.withLock {
            val previous = db.getAllInBBox(bbox, types.map { it.name })
            // Missing local counterpart geometry is not evidence that another crossing is the same quest.
            val obsolete = getObsoleteQuestKeys(crossings, previous.filter { crossingResolved(it, data) }, emptyList())
            updateQuests(crossings, obsolete)
            lastCrossingQuery = input
            obsolete
        }
        return crossings to removed
    }

    private fun hasCrossingChanges(updated: MapDataWithGeometry, deleted: Collection<ElementKey>): Boolean {
        val before = lock.withLock { lastCrossingQuery?.second?.associateBy { it.key }.orEmpty() }
        if (deleted.any { it.type == ElementType.WAY || it in before }) return true
        val ways = before.values.filterIsInstance<Way>()
        val sharedIds = ways.filter { it.isCrossingRoad() }.flatMap { it.nodeIds }.toSet()
            .intersect(ways.filter { it.isCrossingWaterway() }.flatMap { it.nodeIds }.toSet())
        return updated.any { element ->
            val old = before[element.key]
            val current = if (element is de.westnordost.streetcomplete.data.osm.mapdata.Node && element.id in sharedIds)
                element.copy(version = 0, timestampEdited = 0) else crossingDetectionElement(element)
            if (old == current) false
            else if (element.type == ElementType.NODE) old != null || mapDataSource.getWaysForNode(element.id)
                .any { "highway" in it.tags || "waterway" in it.tags || "railway" in it.tags }
            else old != null || current != null
        }
    }

    private fun crossingCandidates(type: AddWaterwayCrossing, original: MapDataWithGeometry,
        bbox: BoundingBox?): List<AddWaterwayCrossing.Candidate> {
        val roads = original.ways.filter { type.isApplicableTo(it) == null }
        val data = MutableMapDataWithGeometry(original)
        // Query the whole anchor, rather than letting the viewport choose between its crossings.
        for (road in roads) {
            val positions = road.nodeIds.mapNotNull { original.getNode(it)?.position }
            if (positions.size == road.nodeIds.size && positions.isNotEmpty())
                data.putAll(mapDataSource.getMapDataWithGeometry(positions.enclosingBoundingBox().enlargedBy(500.0)))
        }
        val persisted = lock.withLock { db.getAllForElements(roads.map { it.key }) }
            .filter { it.questTypeName == type.name }
        val preferred = persisted.filterNot { crossingResolved(it, data) }.groupBy { it.elementId }.mapValues { (_, entries) -> entries.map { it.position } }
        val roadIds = roads.map { it.id }.toSet()
        return type.getCrossings(data, bbox, preferred).filter { it.road.id in roadIds }
    }

    /** Retire a saved pin only on positive evidence, never just on an incomplete viewport query. */
    private fun crossingResolved(entry: OsmQuestDaoEntry, data: MapDataWithGeometry): Boolean {
        val road = data.getWay(entry.elementId) ?: return false
        // Retire legacy single-pin entries once their anchor is available; regenerate in the crossing table.
        if (entry.discriminator.isEmpty()) return true
        if (!road.isCrossingRoad() || road.hasCrossingStructure() || road.isClosed || road.tags["area"] == "yes") return true
        val rp = road.nodeIds.map { data.getNode(it)?.position ?: return false }
        if (entry.position.distanceToArcs(rp) > 0.02) return true
        val known = allQuestTypes.filterIsInstance<AddWaterwayCrossing>().singleOrNull()
            ?.cachedCandidateAt(road.id, entry.position)
        val currentWater = known?.let { data.getWay(it.waterway.id) }
        if (currentWater != null) {
            if (!currentWater.isCrossingWaterway() || currentWater.hasCrossingStructure()
                || currentWater.isClosed || currentWater.tags["area"] == "yes") return true
            val wp = currentWater.nodeIds.map { data.getNode(it)?.position ?: return false }
            if (entry.position.distanceToArcs(wp) > 0.02) return true
        }
        return data.ways.any { water ->
            if (!water.isCrossingWaterway()) return@any false
            val wp = water.nodeIds.map { data.getNode(it)?.position ?: return@any false }
            val point = uniqueCrossingPoints(rp, wp).singleOrNull { it.equalsInOsm(entry.position) } ?: return@any false
            try { checkEditableCrossing(data, road, water, rp, wp, point); false } catch (_: de.westnordost.streetcomplete.data.ConflictException) { true }
        }
    }

    /* -------------------------- OsmQuestsHiddenControllerController  -------------------------- */

    private fun getBlacklistedPositions(bbox: BoundingBox): Set<LatLon> =
        notesSource
            .getAllPositions(bbox.enlargedBy(0.2))
            .map { it.truncateTo6Decimals() }
            .toHashSet()

    private fun isBlacklistedPosition(pos: LatLon): Boolean =
        pos.truncateTo6Decimals() in getBlacklistedPositions(BoundingBox(pos, pos))

    /* ---------------------------------------- Listeners --------------------------------------- */

    override fun addListener(listener: OsmQuestSource.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: OsmQuestSource.Listener) {
        listeners.remove(listener)
    }

    private fun onUpdated(
        added: Collection<OsmQuest> = emptyList(),
        deleted: Collection<OsmQuestKey> = emptyList()
    ) {
        if (added.isEmpty() && deleted.isEmpty()) return

        listeners.forEach { it.onUpdated(added, deleted) }
    }

    private fun Collection<OsmQuest>.filterVisible(): Collection<OsmQuest> =
        if (isNotEmpty()) {
            val bbox = map { it.position }.enclosingBoundingBox()
            val hiddenPositions = getBlacklistedPositions(bbox)
            filter { it.position.truncateTo6Decimals() !in hiddenPositions }
        } else {
            this
        }

    private fun onInvalidated() {
        listeners.forEach { it.onInvalidated() }
    }

    private fun reloadQuestTypes() {
        questTypeRegistry.reload()
        onInvalidated()
    }

    companion object {
        private const val TAG = "OsmQuestController"
        private var instance: OsmQuestController? = null
        fun reloadQuestTypes() = instance?.reloadQuestTypes()
    }
}

/** Aggregated timings for one [OsmQuestController] element update. Not a log per quest type. */
private class QuestUpdatePerf {
    var surroundingMs = 0L
        private set
    var surroundingLoads = 0
        private set
    private val slowMs = LinkedHashMap<String, Long>()
    private val lock = ReentrantLock()

    fun addSurrounding(ms: Long) = lock.withLock {
        surroundingMs += ms
        surroundingLoads++
    }

    fun addSlow(name: String, ms: Long) = lock.withLock {
        val previous = slowMs[name]
        if (previous == null || ms > previous) slowMs[name] = ms
    }

    fun slowLine(): String? = lock.withLock {
        if (slowMs.isEmpty()) return@withLock null
        val shown = slowMs.entries.take(8).joinToString(" ") { "${it.key}=${it.value}ms" }
        val extra = slowMs.size - 8
        if (extra > 0) "$shown more=$extra" else shown
    }
}
