package de.westnordost.streetcomplete.screens.main

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import de.westnordost.osmfeatures.Feature
import de.westnordost.osmfeatures.FeatureDictionary
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuestController
import de.westnordost.streetcomplete.data.location.SurveyChecker
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.edits.ElementEditsController
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.BoundingBox
import de.westnordost.streetcomplete.data.osm.mapdata.Element
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osm.mapdata.LazyMapDataWithGeometry
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest
import de.westnordost.streetcomplete.data.osm.osmquests.OsmQuestSource
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditAction
import de.westnordost.streetcomplete.data.osmnotes.edits.NoteEditsController
import de.westnordost.streetcomplete.data.osmnotes.edits.NotesWithEditsSource
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest
import de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuestSource
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.overlays.OverlayRegistry
import de.westnordost.streetcomplete.data.preferences.Preferences
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.data.quest.QuestTypeRegistry
import de.westnordost.streetcomplete.data.quest.VisibleQuestsSource
import de.westnordost.streetcomplete.data.visiblequests.QuestTypeOrderSource
import de.westnordost.streetcomplete.data.visiblequests.QuestsHiddenController
import de.westnordost.streetcomplete.osm.level.levelsIntersect
import de.westnordost.streetcomplete.osm.level.parseLevelsOrNull
import de.westnordost.streetcomplete.screens.main.map.getIcon
import de.westnordost.streetcomplete.screens.main.map.getTitle
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.util.ktx.launch
import de.westnordost.streetcomplete.util.ktx.truncateTo6Decimals
import de.westnordost.streetcomplete.util.math.enclosingBoundingBox
import de.westnordost.streetcomplete.util.math.enlargedBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

@Stable
abstract class MainBottomSheetViewModel : ViewModel() {
    abstract suspend fun getBottomSheet(selection: MainSheetSelection): ShownBottomSheet?
    abstract suspend fun getHighlightedMarkers(sheet: ShownBottomSheet): List<Marker>

    abstract fun hideQuest(questKey: QuestKey, tempHide: Boolean = false)

    abstract fun isSurvey(geometry: ElementGeometry): Boolean

    abstract fun submitEdit(
        elementEditType: ElementEditType,
        geometry: ElementGeometry,
        elementEditAction: ElementEditAction,
        hasExtra: Boolean = false,
        key: QuestKey? = null,
    )

    /** When immediate next-quest chaining finds a successor, this emits its selection. */
    abstract val chainedQuestSelection: kotlinx.coroutines.flow.SharedFlow<MainSheetSelection.Quest>

    abstract fun commentNote(
        note: Note,
        text: String?,
        imagePaths: List<String> = emptyList(),
        close: Boolean = false,
    )
    abstract fun createNote(
        position: LatLon,
        text: String,
        imagePaths: List<String> = emptyList(),
        trackpoints: List<Trackpoint>? = null,
        isGpx: Boolean = false,
    )
}

@Stable
class MainBottomSheetViewModelImpl(
    private val mapDataSource: MapDataWithEditsSource,
    private val notesSource: NotesWithEditsSource,
    private val osmQuestSource: OsmQuestSource,
    private val osmNoteQuestSource: OsmNoteQuestSource,
    private val elementEditsController: ElementEditsController,
    private val noteEditsController: NoteEditsController,
    private val hiddenQuestsController: QuestsHiddenController,
    private val surveyChecker: SurveyChecker,
    private val visibleQuestsSource: VisibleQuestsSource,
    private val overlayRegistry: OverlayRegistry,
    private val featureDictionary: Lazy<FeatureDictionary>,
    private val externalSource: ExternalSourceQuestController,
    private val questTypeRegistry: QuestTypeRegistry,
    private val questTypeOrderSource: QuestTypeOrderSource,
    private val prefs: Preferences,
) : MainBottomSheetViewModel() {
    override suspend fun getBottomSheet(selection: MainSheetSelection): ShownBottomSheet? =
        withContext(Dispatchers.IO) { load(selection) }

    private fun load(selection: MainSheetSelection): ShownBottomSheet? = when (selection) {
        is MainSheetSelection.Quest -> getQuestBottomSheet(selection)
        is MainSheetSelection.Overlay -> getOverlayBottomSheet(selection)
        is MainSheetSelection.CreateNote -> ShownBottomSheet.CreateOsmNote(selection.trackpoints)
        is MainSheetSelection.EditHistory -> null
        is MainSheetSelection.InsertNode -> ShownBottomSheet.InsertNode(selection.position)
    }

    private val _chainedQuestSelection = MutableSharedFlow<MainSheetSelection.Quest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val chainedQuestSelection: SharedFlow<MainSheetSelection.Quest> =
        _chainedQuestSelection.asSharedFlow()

    private fun getQuestBottomSheet(selection: MainSheetSelection.Quest): ShownBottomSheet? {
        val key = selection.key
        return when (key) {
            is OsmQuestKey -> {
                // VisibleQuestsSource serves dynamic quests from cache and falls back to the DB.
                if (visibleQuestsSource.get(key) == null) return null
                val quest = visibleQuestsSource.get(key) as? OsmQuest
                    ?: osmQuestSource.get(key)
                    ?: return null
                val element = mapDataSource.get(key.elementType, key.elementId) ?: return null
                ShownBottomSheet.OsmQuest(quest, element)
            }
            is OsmNoteQuestKey -> {
                if (visibleQuestsSource.get(key) == null) return null
                val quest = osmNoteQuestSource.get(key.noteId) ?: return null
                val note = notesSource.get(key.noteId) ?: return null
                ShownBottomSheet.OsmNoteQuest(quest, note)
            }
            is ExternalSourceQuestKey -> {
                if (visibleQuestsSource.get(key) == null) return null
                val quest = externalSource.get(key) ?: return null
                ShownBottomSheet.ExternalSourceQuest(quest)
            }
        }
    }

    private fun getOverlayBottomSheet(selection: MainSheetSelection.Overlay): ShownBottomSheet? {
        val overlay = overlayRegistry.getByName(selection.name) ?: return null
        val key = selection.elementKey
        return if (key == null) {
            ShownBottomSheet.Overlay(overlay, null, null)
        } else {
            val geometry = mapDataSource.getGeometry(key.type, key.id) ?: return null
            val note = getNoteForElementAt(geometry.center)
            if (note != null) {
                val quest = OsmNoteQuest(note.id, geometry.center)
                ShownBottomSheet.OsmNoteQuest(quest, note)
            } else {
                val element = mapDataSource.get(key.type, key.id) ?: return null
                ShownBottomSheet.Overlay(overlay, element, geometry)
            }
        }
    }

    override suspend fun getHighlightedMarkers(sheet: ShownBottomSheet): List<Marker> = withContext(Dispatchers.IO) {
        if (sheet !is ShownBottomSheet.OsmQuest) return@withContext emptyList()
        val quest = sheet.quest
        val element = sheet.element
        val bbox = quest.geometry.bounds.enlargedBy(quest.type.highlightedElementsRadius)
        val mapData = LazyMapDataWithGeometry(bbox, mapDataSource)
        val levels = parseLevelsOrNull(element.tags)
        quest.type.getHighlightedElements(element, mapData).mapNotNull { other ->
            if (element == other) return@mapNotNull null
            if (!levels.levelsIntersect(parseLevelsOrNull(other.tags))) return@mapNotNull null
            if (element.tags["layer"] != other.tags["layer"]) return@mapNotNull null
            val geometry = mapData.getGeometry(other.type, other.id) ?: return@mapNotNull null
            Marker(geometry, getIcon(featureDictionary.value, other), getTitle(other.tags))
        }.toList()
    }

    override fun hideQuest(questKey: QuestKey, tempHide: Boolean) {
        launch(Dispatchers.IO) {
            if (tempHide) hiddenQuestsController.tempHide(questKey)
            else hiddenQuestsController.hide(questKey)
        }
    }

    override fun isSurvey(geometry: ElementGeometry): Boolean =
        surveyChecker.checkIsSurvey(geometry)

    /**
     * Submits an edit. When immediate same-element chaining is enabled, emits the successor
     * via [chainedQuestSelection] for MainScreen to open.
     */
    override fun submitEdit(
        elementEditType: ElementEditType,
        geometry: ElementGeometry,
        elementEditAction: ElementEditAction,
        hasExtra: Boolean,
        key: QuestKey?,
    ) {
        launch(Dispatchers.IO) {
            val isNearUserLocation = surveyChecker.checkIsSurvey(geometry)
            val source = if (hasExtra) "survey,extra" else "survey"
            elementEditsController.add(elementEditType, geometry, source, elementEditAction, isNearUserLocation, key)

            val elementKey = elementKeyForImmediateSameElementQuest(
                elementEditType,
                elementEditAction,
                prefs.getBoolean(Prefs.SHOW_NEXT_QUEST_IMMEDIATELY, false),
            ) ?: return@launch

            val successor = immediateSameElementQuestSheet(
                visibleQuestsSource.getAll(geometry.center.enclosingBoundingBox(0.5)),
                elementKey,
                questTypesInChainingOrder(questTypeRegistry, questTypeOrderSource),
            ) { type, id -> mapDataSource.get(type, id) } ?: return@launch

            _chainedQuestSelection.tryEmit(MainSheetSelection.Quest(successor.quest.key))
        }
    }

    override fun commentNote(
        note: Note,
        text: String?,
        imagePaths: List<String>,
        close: Boolean,
    ) {
        launch(Dispatchers.IO) {
            val action = if (close) NoteEditAction.CLOSE else NoteEditAction.COMMENT
            noteEditsController.add(note.id, action, note.position, text, imagePaths)
        }
    }

    override fun createNote(
        position: LatLon,
        text: String,
        imagePaths: List<String>,
        trackpoints: List<Trackpoint>?,
        isGpx: Boolean,
    ) {
        launch(Dispatchers.IO) {
            noteEditsController.add(0, NoteEditAction.CREATE, position, text, imagePaths, trackpoints, isGpx)
        }
    }

    private fun getNoteForElementAt(position: LatLon): Note? =
        notesSource
            .getAll(BoundingBox(position, position).enlargedBy(0.2))
            .filter { note ->
                note.position.truncateTo6Decimals() == position.truncateTo6Decimals() &&
                hiddenQuestsController.get(OsmNoteQuestKey(note.id)) == null
            }.firstOrNull()
}

/** The data necessary to show an element from the map clicked on in the bottom sheet */
sealed interface ShownBottomSheet {
    data class OsmQuest(
        val quest: de.westnordost.streetcomplete.data.osm.osmquests.OsmQuest,
        val element: Element,
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
    }

    data class ExternalSourceQuest(
        val quest: de.westnordost.streetcomplete.data.externalsource.ExternalSourceQuest,
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
    }

    data class OsmNoteQuest(
        val quest: de.westnordost.streetcomplete.data.osmnotes.notequests.OsmNoteQuest,
        val note: Note
    ) : ShownBottomSheet {
        override val position get() = quest.position
        override val geometry get() = quest.geometry
    }

    data class Overlay(
        val overlay: de.westnordost.streetcomplete.data.overlays.Overlay,
        val element: Element?,
        override val geometry: ElementGeometry?,
    ) : ShownBottomSheet {
        override val position get() = geometry?.center
    }

    data class CreateOsmNote(
        val trackpoints: List<Trackpoint>?
    ) : ShownBottomSheet {
        override val position get() = null
        override val geometry get() = null
    }

    data class AddPoi(
        override val position: LatLon,
        val feature: Feature,
    ) : ShownBottomSheet {
        override val geometry get() = null
    }

    data class InsertNode(
        override val position: LatLon,
    ) : ShownBottomSheet {
        override val geometry get() = null
    }

    val position: LatLon?
    val geometry: ElementGeometry?
}
