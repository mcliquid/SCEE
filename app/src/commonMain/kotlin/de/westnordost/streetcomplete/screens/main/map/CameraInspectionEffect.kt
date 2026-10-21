package de.westnordost.streetcomplete.screens.main.map

import de.westnordost.streetcomplete.data.osm.geometry.ElementGeometry
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import de.westnordost.streetcomplete.screens.main.MainSheetSelection
import de.westnordost.streetcomplete.screens.main.MainSheetState
import de.westnordost.streetcomplete.screens.main.ShownBottomSheet
import de.westnordost.streetcomplete.util.ktx.toLatLon
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.maplibre.compose.util.DpPadding
import org.maplibre.spatialk.geojson.Position

/** Moves the camera to what the user inspects: the object of the open bottom sheet or the edit
 *  selected in the edit history sidebar. Returns the camera when the sheet is closed.
 *
 *  The sheet is opened on the camera before its object has loaded so that location updates stop
 *  moving the camera right away. */
@Composable
internal fun CameraInspectionEffect(
    cameraState: MainMapCameraState,
    sheet: MainSheetState,
    position: Position?,
    tracks: MainMapTrackState,
    sheetPadding: DpPadding,
) {
    LaunchedEffect(cameraState, sheet.selection, sheet.id, sheetPadding) {
        when (val selection = sheet.selection) {
            null -> {
                cameraState.closeSheet(position?.toLatLon(), getTrackBearing(tracks.currentTrack))
            }
            is MainSheetSelection.EditHistory -> {
                cameraState.openSheet()
                val shown = snapshotFlow { sheet.shownEdit }
                    .filterNotNull().first { it.edit.key == selection.editKey }
                cameraState.focus(shown.geometry, DpPadding.Zero)
            }
            is MainSheetSelection.CreateNote -> {
                cameraState.openSheet()
                cameraState.focus(selection.position, sheetPadding)
            }
            else -> {
                cameraState.openSheet()
                val shown = snapshotFlow { sheet.shownBottomSheet }.filterNotNull().first()

                // only focus quest forms. Overlay forms leave the map in place
                questGeometryToFocus(shown)?.let { cameraState.focus(it, sheetPadding) }
            }
        }
    }
}

/** The geometry the camera zooms to when a bottom sheet is opened, null if the map should stay as it is */
internal fun questGeometryToFocus(shown: ShownBottomSheet): ElementGeometry? = when (shown) {
    is ShownBottomSheet.OsmQuest -> shown.geometry
    is ShownBottomSheet.OsmNoteQuest -> shown.geometry
    is ShownBottomSheet.ExternalSourceQuest -> shown.geometry // SCEE: Osmose, custom quests
    else -> null
}
