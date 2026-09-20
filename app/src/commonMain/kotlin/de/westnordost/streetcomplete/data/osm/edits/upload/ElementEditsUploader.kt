package de.westnordost.streetcomplete.data.osm.edits.upload

import com.russhwolf.settings.ObservableSettings
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.ConflictException
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.osm.edits.ElementEdit
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.edits.ElementIdProvider
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.ElementKey
import de.westnordost.streetcomplete.data.osm.mapdata.ElementType
import de.westnordost.streetcomplete.data.osm.mapdata.MapData
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataApiClient
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataController
import de.westnordost.streetcomplete.data.osm.mapdata.MapDataUpdates
import de.westnordost.streetcomplete.data.osm.mapdata.MutableMapData
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestType
import de.westnordost.streetcomplete.data.osm.edits.IsRevertAction
import de.westnordost.streetcomplete.data.upload.OnUploadedChangeListener
import de.westnordost.streetcomplete.data.user.UserLoginController
import de.westnordost.streetcomplete.data.user.statistics.StatisticsController
import de.westnordost.streetcomplete.util.logs.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ElementEditsUploader(
    private val elementEditsController: ElementEditsController,
    private val noteEditsController: NoteEditsController,
    private val mapDataController: MapDataController,
    private val singleUploader: ElementEditUploader,
    private val mapDataApi: MapDataApiClient,
    private val statisticsController: StatisticsController,
    private val externalSourceQuestController: ExternalSourceQuestController,
    private val prefs: ObservableSettings,
) {
    var uploadedChangeListener: OnUploadedChangeListener? = null

    private val mutex = Mutex()

    suspend fun upload() = mutex.withLock { withContext(Dispatchers.IO) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val edit = elementEditsController.getOldestUnsynced() ?: break
            val getIdProvider: () -> ElementIdProvider = { elementEditsController.getIdProvider(edit.id) }
            /* the sync of local change -> API and its response should not be cancellable because
             * otherwise an inconsistency in the data would occur. E.g. no "star" for an uploaded
             * change, a change could be uploaded twice etc */
            withContext(NonCancellable) { uploadEdit(edit, getIdProvider) }
            if (ApplicationConstants.DEBUG && !UserLoginController.loggedIn)
                break // slow uploading is much better to read in logs
        }
    } }

    private suspend fun uploadEdit(edit: ElementEdit, getIdProvider: () -> ElementIdProvider) {
        val editActionClassName = edit.action::class.simpleName!!

        try {
            if (edit.type is ExternalSourceQuestType && !externalSourceQuestController.onUpload(edit))
                throw(ConflictException())
            val updates = singleUploader.upload(edit, getIdProvider)

            Log.d(TAG, "Uploaded a $editActionClassName for ${edit.action.elementKeys}")
            uploadedChangeListener?.onUploaded(edit.type.name, edit.position)

            elementEditsController.markSynced(edit, updates)
            mapDataController.updateAll(updates)
            noteEditsController.updateElementIds(updates.idUpdates)

            if (prefs.getBoolean(Prefs.UPDATE_LOCAL_STATISTICS, true)) {
                if (edit.action is IsRevertAction) {
                    statisticsController.subtractOne(edit.type.name, edit.position)
                } else {
                    statisticsController.addOne(edit.type.name, edit.position)
                }
            }
        } catch (e: ConflictException) {
            Log.d(TAG, "Dropped a $editActionClassName for ${edit.action.elementKeys}: ${e.message}")
            uploadedChangeListener?.onDiscarded(edit.type.name, edit.position)

            externalSourceQuestController.onSyncEditFailed(edit)
            elementEditsController.markSyncFailed(edit)

            /* fetching the current version of the element(s) edited on conflict and persisting
               them is not really optional, as when the edit has been deleted due to the conflict,
               the quests etc. would otherwise just be displayed again as if the user didn't solve
               them */
            val updated = mutableListOf<Element>()
            val deleted = mutableListOf<ElementKey>()

            for (elementKey in edit.action.elementKeys) {
                val mapData = fetchElementComplete(elementKey.type, elementKey.id)
                if (mapData != null) {
                    updated.addAll(mapData)
                } else {
                    deleted.add(elementKey)
                }
            }
            if (updated.isNotEmpty() || deleted.isNotEmpty()) {
                mapDataController.updateAll(MapDataUpdates(updated = updated, deleted = deleted))
            }
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Dropped a $editActionClassName: ${e.message}")
            uploadedChangeListener?.onDiscarded(edit.type.name, edit.position)

            elementEditsController.markSyncFailed(edit)
        }
    }

    private suspend fun fetchElementComplete(elementType: ElementType, elementId: Long): MapData? =
        when (elementType) {
            ElementType.NODE -> mapDataApi.getNode(elementId)?.let { MutableMapData(listOf(it)) }
            ElementType.WAY -> mapDataApi.getWayComplete(elementId)
            ElementType.RELATION -> mapDataApi.getRelationComplete(elementId)
        }

    companion object {
        private const val TAG = "ElementEditsUploader"
    }
}
