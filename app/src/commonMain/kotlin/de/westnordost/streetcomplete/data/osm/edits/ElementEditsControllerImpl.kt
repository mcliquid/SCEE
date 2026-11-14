package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.Database
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceDao
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryDelete
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryModify
import de.westnordost.streetcomplete.data.osm.edits.update_tags.UpdateElementTagsAction
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.util.Listeners
import de.westnordost.streetcomplete.util.ktx.nowAsEpochMilliseconds
import de.westnordost.streetcomplete.util.logs.Log
import de.westnordost.streetcomplete.util.logs.Perf
import de.westnordost.streetcomplete.util.logs.currentPerfTrace
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

class ElementEditsControllerImpl(
    private val editsDB: ElementEditsDao,
    private val editElementsDB: EditElementsDao,
    private val elementIdProviderDB: ElementIdProviderDao,
    private val database: Database,
    private val externalSourceDB: ExternalSourceDao,
    private val cleanExternalSourceEdits: ((Collection<Long>) -> Unit)? = null,
) : ElementEditsController {

    private val reviewsDB = ElementEditReviewsDao(database)
    private val listeners = Listeners<ElementEditsSource.Listener>()

    private val lock = ReentrantLock()
    // In-memory only; uncertain replies and process-death recovery still require A1.
    private var uploadClaim: Long? = null

    private val editCache by lazy {
        val c = hashMapOf<Long, ElementEdit>()
        editsDB.getAll().associateByTo(c) { it.id }
    }

    // full elementIdProvider cache didn't work as expected, so only store empty idPoviders (resp. their ids)
    // this is still very useful, because
    //  most are actually empty (edit tags action)
    //  on rebuildLocalChanges idProviders of all edits are queried, so the cache saves many db queries
    //    each query is fast, but for many unsynced edits this is a clear improvement
    private val emptyIdProviderCache = HashSet<Long>()
    private val deletedExternalSourceKeys = hashMapOf<Long, ExternalSourceQuestKey>()
    private val deletedIdProviders = hashMapOf<Long, List<ElementKey>>()

    /* ----------------------- Unsynced edits and syncing them -------------------------------- */

    override fun add(
        type: ElementEditType,
        geometry: ElementGeometry,
        source: String,
        action: ElementEditAction,
        isNearUserLocation: Boolean,
        key: QuestKey?
    ) {
        Log.d(TAG, "Add ${type.name} for ${action.elementKeys.joinToString()}")
        // removes discardable tags if they were part of original element, but not if user added them
        val newAction = if (action is UpdateElementTagsAction && action.originalElement.tags.keys.any { it in DISCARDABLE_TAGS }) {
            val builder = StringMapChangesBuilder(action.originalElement.tags)
            action.changes.changes.forEach { when (it) {
                is StringMapEntryDelete -> builder.remove(it.key)
                is StringMapEntryAdd -> builder[it.key] = it.value
                is StringMapEntryModify -> builder[it.key] = it.value
            } }
            DISCARDABLE_TAGS.forEach { builder.remove(it) }
            UpdateElementTagsAction(action.originalElement, builder.create())
        } else
            action
        add(ElementEdit(0, type, geometry, source, nowAsEpochMilliseconds(), false, newAction, isNearUserLocation), key)
    }

    override fun get(id: Long): ElementEdit? = lock.withLock {
        val blocked = blockedByReview()
        editCache[id]?.let { it.copy(review = blocked[id]) }
    }

    override fun getAll(): List<ElementEdit> = lock.withLock {
        val blocked = blockedByReview()
        editCache.values.map { it.copy(review = blocked[it.id]) }
    }

    override fun getAllUnsynced(): List<ElementEdit> =
        getAll().filterNot { it.isSynced }

    override fun getOldestUnsynced(): ElementEdit? =
        getAllUnsynced().minByOrNull { it.createdTimestamp }

    override fun claimOldestUnsynced(excludedIds: Set<Long>): ElementEdit? = lock.withLock {
        if (uploadClaim != null) return null
        val blocked = blockedByReview()
        val edit = editCache.values.filter { !it.isSynced && it.id !in excludedIds && it.id !in blocked &&
            it.action.elementKeys.none { key -> key.id <= 0 } }
            .minWithOrNull(compareBy<ElementEdit> { it.createdTimestamp }.thenBy { it.id }) ?: return null
        uploadClaim = edit.id
        edit
    }

    private fun blockedByReview(): Map<Long, ElementEditReview> {
        val blocked = editCache.values.filterNot { it.isSynced }
            .mapNotNull { edit -> edit.review?.let { edit.id to it } }.toMap().toMutableMap()
        val queue = ArrayDeque(blocked.keys.sorted())
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val edit = editCache.getValue(id)
            val count = edit.action.newElementsCount
            val keys = edit.action.elementKeys + if (count.nodes + count.ways + count.relations > 0)
                elementIdProviderDB.get(id).getAll() else emptyList()
            for (key in keys) for (dependent in editElementsDB.getAllByElement(key.type, key.id)) {
                if (editCache[dependent]?.isSynced == false && dependent !in blocked) {
                    blocked[dependent] = blocked.getValue(id)
                    queue.addLast(dependent)
                }
            }
        }
        return blocked
    }

    override fun markRejected(editId: Long, rejection: ElementUploadRejectedException) {
        lock.withLock {
            check(uploadClaim == editId)
            val edit = editCache[editId] ?: error("Claimed edit disappeared")
            check(!edit.isSynced)
            val review = ElementEditReview(editId, rejection.httpStatus,
                sanitizeRejectionReason(rejection.message.orEmpty()), nowAsEpochMilliseconds())
            database.transaction { reviewsDB.put(review) }
            editCache[editId] = edit.copy(review = review)
        }
        listeners.forEach { it.onReviewChanged() }
    }

    override fun retryRejectedEdit(editId: Long): Boolean {
        lock.withLock {
            if (uploadClaim == editId) throw ElementEditUploadInProgressException()
            val edit = editCache[editId] ?: return false
            if (edit.review == null) return false
            database.transaction { reviewsDB.delete(listOf(editId)) }
            editCache[editId] = edit.copy(review = null)
        }
        listeners.forEach { it.onReviewChanged() }
        return true
    }

    override fun releaseUploadClaim(editId: Long) = lock.withLock {
        check(uploadClaim == editId)
        uploadClaim = null
    }

    override fun getIdProvider(id: Long): ElementIdProvider = lock.withLock {
        deletedIdProviders[id]?.let { return ElementIdProvider(it) }
        if (id in emptyIdProviderCache) return ElementIdProvider(emptyList())
        val provider = elementIdProviderDB.get(id)
        if (provider.isEmpty()) emptyIdProviderCache.add(id)
        provider
    }

    override fun getExternalSourceQuestKey(id: Long): ExternalSourceQuestKey? = lock.withLock {
        deletedExternalSourceKeys[id] ?: externalSourceDB.getKeyForElementEdit(id)
    }

    /** Delete old synced (aka uploaded) edits older than the given timestamp. Used to clear
     *  the undo history */
    override fun deleteSyncedOlderThan(timestamp: Long): Int {
        var deletedCount = 0
        var deleteEdits: List<ElementEdit> = listOf()
        lock.withLock {
            editCache // Initialize from committed state before writing.
            var providers = emptyMap<Long, ElementIdProvider>()
            var sourceKeys = emptyMap<Long, ExternalSourceQuestKey>()
            database.transaction {
                val allEdits = editsDB.getAll()
                deleteEdits = allEdits.filter { it.createdTimestamp < timestamp && it.isSynced && it.id != uploadClaim }
                val ids = deleteEdits.map { it.id }
                providers = ids.associateWith { elementIdProviderDB.get(it) }
                sourceKeys = externalSourceKeys(deleteEdits)
                if (ids.isNotEmpty()) {
                    deletedCount = editsDB.deleteAll(ids)
                    editElementsDB.deleteAll(ids)
                    elementIdProviderDB.deleteAll(ids)
                    reviewsDB.delete(ids)
                }
                val kept = elementEditIdsKeptAfterCleanup(allEdits, deleteEdits)
                cleanExternalSourceEdits?.invoke(kept)
                    ?: externalSourceDB.deleteAllExceptForElementEdits(kept)
            }
            editCache.keys.removeAll(providers.keys)
            emptyIdProviderCache.removeAll(providers.keys)
            deletedIdProviders.putAll(providers.mapValues { it.value.getAll() })
            deletedExternalSourceKeys.putAll(sourceKeys)
        }
        if (deleteEdits.isNotEmpty()) notifyDeleted(deleteEdits)
        return deletedCount
    }

    override fun getUnsyncedCount(): Int =
        getAllUnsynced().size

    override fun getPositiveUnsyncedCount(): Int {
        val unsynced = getAllUnsynced().map { it.action }
        return unsynced.filter { it !is IsRevertAction }.size - unsynced.filter { it is IsRevertAction }.size
    }

    override fun markSynced(edit: ElementEdit, elementUpdates: MapDataUpdates) {
        val start = Perf.mark()
        try {
            markSyncedMeasured(edit, elementUpdates)
        } finally {
            currentPerfTrace()?.addMs("markSynced", Perf.ms(start))
        }
    }

    private fun markSyncedMeasured(edit: ElementEdit, elementUpdates: MapDataUpdates) {
        val idUpdatesMap = elementUpdates.idUpdates.associate {
            ElementKey(it.elementType, it.oldElementId) to it.newElementId
        }
        var syncSuccess = false
        val editIdsToUpdate = HashSet<Long>()
        val syncedEdit by lazy { edit.copy(isSynced = true) }
        lock.withLock {
            editCache
            check(editCache[edit.id]?.review == null) { "Rejected edit must be explicitly retried before synchronization" }
            val updatedEdits = mutableListOf<ElementEdit>()
            database.transaction {
                elementUpdates.idUpdates.flatMapTo(editIdsToUpdate) {
                    editElementsDB.getAllByElement(it.elementType, it.oldElementId)
                }
                for (id in editIdsToUpdate) {
                    val oldEdit = editsDB.get(id) ?: continue
                    val updatedEdit = oldEdit.copy(action = oldEdit.action.idsUpdatesApplied(idUpdatesMap))
                    editsDB.put(updatedEdit)
                    editElementsDB.delete(id)
                    editElementsDB.put(id, updatedEdit.action.elementKeys)
                    updatedEdits.add(updatedEdit)
                }
                syncSuccess = editsDB.markSynced(edit.id)
                elementIdProviderDB.updateIds(elementUpdates.idUpdates)
            }
            updatedEdits.forEach { editCache[it.id] = it }
            emptyIdProviderCache.removeAll(editIdsToUpdate)
            emptyIdProviderCache.remove(edit.id)
            if (syncSuccess) editCache[edit.id] = syncedEdit
        }
        if (syncSuccess) onSyncedEdit(syncedEdit, editIdsToUpdate)
    }

    override fun markSyncFailed(edit: ElementEdit) {
        delete(edit)
    }

    /* ----------------------- Undoable edits and undoing them -------------------------------- */

    override fun undo(edit: ElementEdit): Boolean {
        var removed = emptyList<ElementEdit>()
        var revert: ElementEdit? = null
        lock.withLock {
            val currentEdit = editCache[edit.id] ?: return false
            removed = getEditsBasedOnElementsCreatedByEdit(currentEdit)
            if (removed.any { it.id == uploadClaim }) throw ElementEditUploadInProgressException()
            if (currentEdit.isSynced) {
                val action = currentEdit.action
                if (action !is IsActionRevertable) return false
                revert = ElementEdit(0, currentEdit.type, currentEdit.originalGeometry, currentEdit.source,
                    nowAsEpochMilliseconds(), false, action.createReverted(getIdProvider(currentEdit.id)), currentEdit.isNearUserLocation)
            }
            val providers = removed.associate { it.id to elementIdProviderDB.get(it.id) }
            val sourceKeys = externalSourceKeys(removed)
            database.transaction {
                persistDelete(removed)
                revert?.let { persistAdd(it) }
            }
            publishDeleted(removed, providers)
            deletedExternalSourceKeys.putAll(sourceKeys)
            revert?.let { editCache[it.id] = it }
        }
        notifyDeleted(removed)
        revert?.let { onAddedEdit(it, null) }
        return true
    }

    /* ------------------------------------ add/sync/delete ------------------------------------- */

    private fun persistAdd(edit: ElementEdit) {
        editsDB.put(edit)
        editElementsDB.put(edit.id, edit.action.elementKeys)
        val count = edit.action.newElementsCount
        elementIdProviderDB.assign(edit.id, count.nodes, count.ways, count.relations)
    }

    private fun add(edit: ElementEdit, key: QuestKey? = null) {
        val start = Perf.mark()
        lock.withLock {
            editCache
            database.transaction {
                persistAdd(edit)
                if (key is ExternalSourceQuestKey) externalSourceDB.addElementEdit(key, edit.id)
            }
            editCache[edit.id] = edit
        }
        if (currentPerfTrace()?.event == "answer") currentPerfTrace()?.addMs("persist", Perf.ms(start))
        onAddedEdit(edit, key)
    }

    private fun persistDelete(edits: List<ElementEdit>) {
        val ids = edits.map { it.id }
        editsDB.deleteAll(ids)
        editElementsDB.deleteAll(ids)
        elementIdProviderDB.deleteAll(ids)
        reviewsDB.delete(ids)
        ids.forEach { externalSourceDB.deleteElementEdit(it) }
    }

    private fun externalSourceKeys(edits: List<ElementEdit>): Map<Long, ExternalSourceQuestKey> =
        edits.mapNotNull { edit -> externalSourceDB.getKeyForElementEdit(edit.id)?.let { edit.id to it } }.toMap()

    private fun publishDeleted(edits: List<ElementEdit>, providers: Map<Long, ElementIdProvider>) {
        val ids = edits.map { it.id }
        editCache.keys.removeAll(ids)
        emptyIdProviderCache.removeAll(ids.toSet())
        deletedIdProviders.putAll(providers.mapValues { it.value.getAll() })
    }

    private fun delete(edit: ElementEdit) {
        val edits = lock.withLock {
            val removed = getEditsBasedOnElementsCreatedByEdit(edit)
            val providers = removed.associate { it.id to elementIdProviderDB.get(it.id) }
            val sourceKeys = externalSourceKeys(removed)
            database.transaction { persistDelete(removed) }
            publishDeleted(removed, providers)
            deletedExternalSourceKeys.putAll(sourceKeys)
            removed
        }
        notifyDeleted(edits)
    }

    private fun notifyDeleted(edits: List<ElementEdit>) {
        try {
            // Listeners still need the committed deletion's old IDs to remove local geometry.
            onDeletedEdits(edits)
        } finally {
            lock.withLock { edits.forEach {
                deletedIdProviders.remove(it.id)
                deletedExternalSourceKeys.remove(it.id)
            } }
        }
    }

    private fun getEditsBasedOnElementsCreatedByEdit(edit: ElementEdit): List<ElementEdit> {
        val result = mutableListOf<ElementEdit>()

        val createdElementKeys = elementIdProviderDB.get(edit.id).getAll()
        val editsBasedOnThese = createdElementKeys
            .flatMapTo(HashSet()) { editElementsDB.getAllByElement(it.type, it.id) }
            .mapNotNull { editCache[it] }
            .filter { it.id != edit.id }

        // deep first
        for (e in editsBasedOnThese) {
            result += getEditsBasedOnElementsCreatedByEdit(e)
        }
        result += edit

        return result
    }

    /* ------------------------------------ Listeners ------------------------------------------- */

    override fun addListener(listener: ElementEditsSource.Listener) {
        listeners.add(listener)
    }
    override fun removeListener(listener: ElementEditsSource.Listener) {
        listeners.remove(listener)
    }

    private fun onAddedEdit(edit: ElementEdit, key: QuestKey?) {
        listeners.forEach { it.onAddedEdit(edit, key) }
    }

    private fun onSyncedEdit(edit: ElementEdit, updatedEditIds: Collection<Long>) {
        listeners.forEach { it.onSyncedEdit(edit, updatedEditIds) }
    }

    private fun onDeletedEdits(edits: List<ElementEdit>) {
        listeners.forEach { it.onDeletedEdits(edits) }
    }

    companion object {
        private const val TAG = "ElementEditsController"
    }
}

/** Ids of edits that still exist after [deletedEdits] are removed. Orphan links are everything else. */
internal fun elementEditIdsKeptAfterCleanup(
    allEdits: List<ElementEdit>,
    deletedEdits: Collection<ElementEdit>,
): List<Long> {
    val deletedIds = deletedEdits.map { it.id }.toSet()
    return allEdits.map { it.id }.filter { it !in deletedIds }
}

// list from josm
private val DISCARDABLE_TAGS = hashSetOf(
    "created_by",
    "converted_by",
    "current_id",
    "geobase:datasetName",
    "geobase:uuid",
    "KSJ2:ADS",
    "KSJ2:ARE",
    "KSJ2:AdminArea",
    "KSJ2:COP_label",
    "KSJ2:DFD",
    "KSJ2:INT",
    "KSJ2:INT_label",
    "KSJ2:LOC",
    "KSJ2:LPN",
    "KSJ2:OPC",
    "KSJ2:PubFacAdmin",
    "KSJ2:RAC",
    "KSJ2:RAC_label",
    "KSJ2:RIC",
    "KSJ2:RIN",
    "KSJ2:WSC",
    "KSJ2:coordinate",
    "KSJ2:curve_id",
    "KSJ2:curve_type",
    "KSJ2:filename",
    "KSJ2:lake_id",
    "KSJ2:lat",
    "KSJ2:long",
    "KSJ2:river_id",
    "odbl",
    "odbl:note",
    "osmarender:nameDirection",
    "osmarender:renderName",
    "osmarender:renderRef",
    "osmarender:rendernames",
    "SK53_bulk:load",
    "sub_sea:type",
    "tiger:source",
    "tiger:separated",
    "tiger:tlid",
    "tiger:upload_uuid",
    "import_uuid",
    "gnis:import_uuid",
    "yh:LINE_NAME",
    "yh:LINE_NUM",
    "yh:STRUCTURE",
    "yh:TOTYUMONO",
    "yh:TYPE",
    "yh:WIDTH",
    "yh:WIDTH_RANK"
)
