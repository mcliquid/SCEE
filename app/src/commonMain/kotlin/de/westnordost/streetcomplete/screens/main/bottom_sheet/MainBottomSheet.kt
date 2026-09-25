package de.westnordost.streetcomplete.screens.main.bottom_sheet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import de.westnordost.osmfeatures.Feature
import de.westnordost.streetcomplete.data.osm.edits.ElementEditAction
import de.westnordost.streetcomplete.data.osm.edits.ElementEditType
import de.westnordost.streetcomplete.data.osm.edits.MapDataWithEditsSource
import de.westnordost.streetcomplete.data.osm.edits.addNodeEdit
import de.westnordost.streetcomplete.data.osm.edits.create.CreateNodeAction
import de.westnordost.streetcomplete.data.osm.edits.create.createNodeAction
import de.westnordost.streetcomplete.data.osm.edits.tagEdit
import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import de.westnordost.streetcomplete.data.osm.geometry.ElementPointGeometry
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.Note
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import de.westnordost.streetcomplete.data.quest.ExternalSourceQuestKey
import de.westnordost.streetcomplete.data.quest.OsmNoteQuestKey
import de.westnordost.streetcomplete.data.quest.OsmQuestKey
import de.westnordost.streetcomplete.data.quest.QuestKey
import de.westnordost.streetcomplete.quests.note_comments.AddNoteCommentForm
import de.westnordost.streetcomplete.resources.Res
import de.westnordost.streetcomplete.resources.quest_create_note
import de.westnordost.streetcomplete.screens.main.ShownBottomSheet
import de.westnordost.streetcomplete.screens.main.bottom_sheet.note.CreateNoteForm
import de.westnordost.streetcomplete.screens.main.bottom_sheet.overlay.OverlayFormContainer
import de.westnordost.streetcomplete.screens.main.bottom_sheet.quest.OsmQuestFormContainer
import de.westnordost.streetcomplete.ui.common.dialogs.SurveyConfirmationDialog
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMarkersCallback
import de.westnordost.streetcomplete.ui.common.quest.LocalMapMetersPerDp
import de.westnordost.streetcomplete.ui.common.quest.MapClick
import de.westnordost.streetcomplete.ui.common.quest.MapOverlayContent
import de.westnordost.streetcomplete.ui.common.quest.Marker
import de.westnordost.streetcomplete.util.math.PositionOnWay
import org.jetbrains.compose.resources.DrawableResource
import org.koin.compose.koinInject

/**
 * Everything that happens in the bottom sheet displayed in the main screen happens here.
 *
 * It actually ought to be displayed at full size, because bottom sheets may have elements that
 * should be displayed above the acutal bottom sheet form (such as a crosshairs, or the arrow when
 * moving a node). So, the actual sliding up/down of the bottom sheet(s) is handled by the forms
 * individually. */
@Composable
fun MainBottomSheet(
    onDismiss: () -> Unit,
    onSolved: (icon: DrawableResource, position: LatLon) -> Unit,
    onHideQuest: (QuestKey, tempHide: Boolean) -> Unit,
    isSurvey: (ElementGeometry) -> Boolean,
    onSubmitEdit: (
        ElementEditType,
        ElementGeometry,
        ElementEditAction,
        hasExtra: Boolean,
        key: QuestKey?,
    ) -> Unit,
    onCommentNote: (Note, String?, List<String>, close: Boolean) -> Unit,
    onCreateNote: (LatLon, String, List<String>, List<Trackpoint>?, isGpx: Boolean) -> Unit,
    shownBottomSheet: ShownBottomSheet,
    mapRotation: Float,
    mapTilt: Float,
    mapPosition: LatLon,
    mapMetersPerDp: Double,
    onSetMapMarkers: (Iterable<Marker>?) -> Unit,
    onSetMapOverlay: (MapOverlayContent?) -> Unit,
    lastMapClick: MapClick?,
    modifier: Modifier = Modifier
) {
    var confirmEdit by remember { mutableStateOf<PendingEdit?>(null) }

    when (shownBottomSheet) {
        is ShownBottomSheet.CreateOsmNote -> {
            CreateNoteForm(
                onLeaveNote = { noteText, noteImagePaths, trackpoints, isGpx ->
                    onCreateNote(
                        mapPosition,
                        noteText,
                        noteImagePaths,
                        trackpoints,
                        isGpx,
                    )
                    onSolved(Res.drawable.quest_create_note, mapPosition)
                    onDismiss()
                },
                onDismiss = onDismiss,
                trackpoints = shownBottomSheet.trackpoints,
                position = mapPosition,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.OsmNoteQuest -> {
            AddNoteCommentForm(
                onDismiss = onDismiss,
                onCommentNote = { noteText, noteImagePaths, close ->
                    onCommentNote(
                        shownBottomSheet.note,
                        noteText,
                        noteImagePaths,
                        close,
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = { tempHide ->
                    val key = OsmNoteQuestKey(shownBottomSheet.note.id)
                    onHideQuest(key, tempHide)
                    onDismiss()
                },
                quest = shownBottomSheet.quest,
                note = shownBottomSheet.note,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.OsmQuest -> {
            OsmQuestFormContainer(
                onDismiss = onDismiss,
                onEdit = { action, isTagEdit, isEditInContextOf ->
                    val hasExtra = isEditInContextOf && !isTagEdit
                    if (SuppressSurveyConfirmation || isSurvey(shownBottomSheet.quest.geometry)) {
                        onSubmitEdit(
                            if (isTagEdit) tagEdit else shownBottomSheet.quest.type,
                            shownBottomSheet.quest.geometry,
                            action,
                            hasExtra,
                            shownBottomSheet.quest.key,
                        )
                        onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                        onDismiss()
                    } else {
                        confirmEdit = PendingEdit(
                            if (isTagEdit) tagEdit else shownBottomSheet.quest.type,
                            shownBottomSheet.quest.geometry,
                            action,
                            hasExtra,
                            shownBottomSheet.quest.key,
                        )
                    }
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx ->
                    onCreateNote(
                        shownBottomSheet.quest.geometry.center,
                        noteText,
                        noteImagePaths,
                        null,
                        isGpx,
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = { tempHide ->
                    val key = OsmQuestKey(
                        shownBottomSheet.element.type,
                        shownBottomSheet.element.id, shownBottomSheet.quest.type.name)
                    onHideQuest(key, tempHide)
                    onDismiss()
                },
                questType = shownBottomSheet.quest.type,
                element = shownBottomSheet.element,
                geometry = shownBottomSheet.quest.geometry,
                mapPosition = mapPosition,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = onSetMapMarkers,
                onSetMapOverlay = onSetMapOverlay,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.ExternalSourceQuest -> {
            ExternalSourceQuestFormContainer(
                onDismiss = onDismiss,
                onEdit = { action ->
                    if (SuppressSurveyConfirmation || isSurvey(shownBottomSheet.quest.geometry)) {
                        onSubmitEdit(
                            shownBottomSheet.quest.type,
                            shownBottomSheet.quest.geometry,
                            action,
                            false,
                            shownBottomSheet.quest.key,
                        )
                        onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                        onDismiss()
                    } else {
                        confirmEdit = PendingEdit(
                            shownBottomSheet.quest.type,
                            shownBottomSheet.quest.geometry,
                            action,
                            false,
                            shownBottomSheet.quest.key,
                        )
                    }
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx ->
                    onCreateNote(
                        shownBottomSheet.quest.geometry.center,
                        noteText,
                        noteImagePaths,
                        null,
                        isGpx,
                    )
                    onSolved(shownBottomSheet.quest.type.icon, shownBottomSheet.quest.position)
                    onDismiss()
                },
                onHideQuest = { tempHide ->
                    val key = ExternalSourceQuestKey(shownBottomSheet.quest.id, shownBottomSheet.quest.source)
                    onHideQuest(key, tempHide)
                    onDismiss()
                },
                quest = shownBottomSheet.quest,
                mapPosition = mapPosition,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = { onSetMapMarkers(it) },
                modifier = modifier,
            )
        }
        is ShownBottomSheet.Overlay -> {
            OverlayFormContainer(
                onDismiss = onDismiss,
                onEdit = { action, isTagEdit, isEditInContextOf ->
                    val geometry = shownBottomSheet.geometry ?: ElementPointGeometry(mapPosition)
                    val hasExtra = isEditInContextOf && !isTagEdit

                    if (SuppressSurveyConfirmation || isSurvey(geometry)) {
                        onSubmitEdit(
                            if (isTagEdit) tagEdit else shownBottomSheet.overlay,
                            geometry,
                            action,
                            hasExtra,
                            null,
                        )
                        onSolved(shownBottomSheet.overlay.icon, geometry.center)
                        onDismiss()
                    } else {
                        confirmEdit = PendingEdit(
                            if (isTagEdit) tagEdit else shownBottomSheet.overlay,
                            geometry,
                            action,
                            hasExtra,
                            null,
                        )
                    }
                },
                onLeaveNote = { noteText, noteImagePaths, isGpx ->
                    val center = shownBottomSheet.geometry?.center ?: mapPosition
                    onCreateNote(
                        center,
                        noteText,
                        noteImagePaths,
                        null,
                        isGpx,
                    )
                    onSolved(shownBottomSheet.overlay.icon, center)
                    onDismiss()
                },
                overlay = shownBottomSheet.overlay,
                element = shownBottomSheet.element,
                geometry = shownBottomSheet.geometry,
                mapRotation = mapRotation,
                mapTilt = mapTilt,
                mapPosition = mapPosition,
                mapMetersPerDp = mapMetersPerDp,
                onSetMapMarkers = onSetMapMarkers,
                onSetMapOverlay = onSetMapOverlay,
                lastMapClick = lastMapClick,
                modifier = modifier,
            )
        }
        is ShownBottomSheet.AddPoi -> {
            AddPoiForm(
                feature = shownBottomSheet.feature,
                position = mapPosition,
                onAdd = {
                    onSubmitEdit(
                        addNodeEdit,
                        ElementPointGeometry(it.position),
                        CreateNodeAction(it.position, it.tags),
                        false,
                        null,
                    )
                    onSolved(addNodeEdit.icon, it.position)
                    onDismiss()
                },
                onDismiss = onDismiss,
                onSetMapMarkers = { onSetMapMarkers(it) },
            )
        }
        is ShownBottomSheet.InsertNode -> {
            var selected by remember { mutableStateOf<Pair<Feature, PositionOnWay>?>(null) }
            if (selected == null) {
                CompositionLocalProvider(
                    LocalMapMetersPerDp provides mapMetersPerDp,
                    LocalMapMarkersCallback provides { onSetMapMarkers(it) },
                ) {
                    InsertNodeForm(
                        shownBottomSheet.position,
                        onDismiss,
                        highlightGeometries = { geometries ->
                            onSetMapMarkers(geometries.map { Marker(it) })
                        },
                        onSelectFeature = { f, p -> selected = f to p }
                    )
                }
            } else {
                val mapDataSource: MapDataWithEditsSource = koinInject()
                AddPoiForm(
                    feature = selected!!.first,
                    position = selected!!.second.position,
                    onAdd = { element ->
                        val action = createNodeAction(selected!!.second, mapDataSource) { changeBuilder ->
                            changeBuilder.keys.forEach { if (it !in element.tags) changeBuilder.remove(it) }
                            element.tags.forEach { changeBuilder[it.key] = it.value }
                        }
                        if (action != null) {
                            onSubmitEdit(
                                addNodeEdit,
                                ElementPointGeometry(element.position),
                                action,
                                false,
                                null,
                            )
                            onSolved(addNodeEdit.icon, element.position)
                        }
                        onDismiss()
                    },
                    onDismiss = onDismiss,
                    onSetMapMarkers = { onSetMapMarkers(it) },
                    showPin = false
                )
            }
        }
    }

    confirmEdit?.let { pendingEdit ->
        SurveyConfirmationDialog(
            onDismissRequest = { confirmEdit = null },
            onConfirmed = {
                onSubmitEdit(
                    pendingEdit.elementEditType,
                    pendingEdit.geometry,
                    pendingEdit.elementEditAction,
                    pendingEdit.hasExtra,
                    pendingEdit.key,
                )
                onDismiss()
            },
            onToggleDontShowAgain = { SuppressSurveyConfirmation = it }
        )
    }
}

private data class PendingEdit(
    val elementEditType: ElementEditType,
    val geometry: ElementGeometry,
    val elementEditAction: ElementEditAction,
    val hasExtra: Boolean = false,
    val key: QuestKey? = null,
)

private var SuppressSurveyConfirmation = false
