package de.westnordost.streetcomplete.data.osmnotes.edits

import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmtracks.Trackpoint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.readString
import kotlinx.io.writeString
import java.io.File

/**
 * Serialised read-modify-write of `notex.gpx` and `track_N.gpx`.
 * A failed write leaves the previous file in place and does not stick.
 */
internal class GpxNotesStore(
    private val fileSystem: FileSystem,
    private val directory: Path,
    private val beforeCommit: () -> Unit = {},
) {
    private val mutex = Mutex()
    private val serializer = GpxNoteSerializer(ApplicationConstants.USER_AGENT)

    suspend fun append(
        note: String,
        imagePaths: List<String>,
        position: LatLon,
        recordedTrack: List<Trackpoint>?,
    ) {
        mutex.withLock {
            fileSystem.createDirectories(directory, mustCreate = false)
            val imageText = if (imagePaths.isEmpty()) {
                ""
            } else {
                "\n images used: ${imagePaths.joinToString(", ") { it.substringAfterLast(File.separator) }}"
            }
            val trackFileName = writeTrack(recordedTrack)
            val trackText = if (trackFileName == null) "" else "\n attached track: $trackFileName"
            val notesPath = Path(directory, NOTES_FILE)
            val oldGpx = if (fileSystem.exists(notesPath)) {
                fileSystem.source(notesPath).buffered().use { it.readString() }
            } else {
                null
            }
            val updated = serializer.appendWaypoint(oldGpx, position, note + trackText + imageText)
            beforeCommit()
            replace(NOTES_FILE, updated)
        }
    }

    private fun writeTrack(recordedTrack: List<Trackpoint>?): String? {
        if (recordedTrack.isNullOrEmpty()) return null
        var i = 1
        while (fileSystem.exists(Path(directory, "track_$i.gpx"))) i += 1
        val name = "track_$i.gpx"
        replace(name, serializer.serializeTrack(name.substringBefore(".gpx"), recordedTrack))
        return name
    }

    private fun replace(fileName: String, text: String) {
        val tempName = "$fileName.tmp"
        val temp = Path(directory, tempName)
        val target = Path(directory, fileName)
        fileSystem.sink(temp).buffered().use { it.writeString(text) }
        try {
            fileSystem.atomicMove(temp, target)
        } catch (_: IOException) {
            fileSystem.sink(target).buffered().use { it.writeString(text) }
            fileSystem.delete(temp, mustExist = false)
        }
    }

    private companion object {
        const val NOTES_FILE = "notex.gpx"
    }
}
