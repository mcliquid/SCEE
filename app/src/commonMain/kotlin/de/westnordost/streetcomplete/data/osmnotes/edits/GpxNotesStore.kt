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
    private val failAtomicReplace: () -> Boolean = { false },
    private val failBackupInstall: () -> Boolean = { false },
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
            recoverInterrupted(NOTES_FILE)
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
        while (true) {
            val name = "track_$i.gpx"
            recoverInterrupted(name)
            if (!fileSystem.exists(Path(directory, name))) break
            i += 1
        }
        val name = "track_$i.gpx"
        replace(name, serializer.serializeTrack(name.substringBefore(".gpx"), recordedTrack))
        return name
    }

    private fun replace(fileName: String, text: String) {
        recoverInterrupted(fileName)
        val temp = Path(directory, "$fileName.tmp")
        val target = Path(directory, fileName)
        val backup = Path(directory, "$fileName.bak")
        fileSystem.sink(temp).buffered().use { it.writeString(text) }
        if (replaceAtomically(temp, target)) {
            fileSystem.delete(backup, mustExist = false)
            return
        }
        // Never truncate the live file. Park it, then move the finished temp into place.
        val hadTarget = fileSystem.exists(target)
        if (hadTarget) {
            if (fileSystem.exists(backup)) fileSystem.delete(backup, mustExist = false)
            try {
                fileSystem.atomicMove(target, backup)
            } catch (e: IOException) {
                fileSystem.delete(temp, mustExist = false)
                throw e
            }
        }
        try {
            if (failBackupInstall()) throw IOException("backup install")
            fileSystem.atomicMove(temp, target)
        } catch (e: IOException) {
            if (hadTarget && !fileSystem.exists(target) && fileSystem.exists(backup)) {
                fileSystem.atomicMove(backup, target)
            }
            fileSystem.delete(temp, mustExist = false)
            throw e
        }
        fileSystem.delete(backup, mustExist = false)
    }

    private fun replaceAtomically(temp: Path, target: Path): Boolean {
        if (failAtomicReplace()) return false
        return try {
            fileSystem.atomicMove(temp, target)
            true
        } catch (_: IOException) {
            false
        }
    }

    /** A crash between parking the old file and installing the new one leaves only the `.bak`. */
    private fun recoverInterrupted(fileName: String) {
        val target = Path(directory, fileName)
        val backup = Path(directory, "$fileName.bak")
        val temp = Path(directory, "$fileName.tmp")
        if (!fileSystem.exists(target) && fileSystem.exists(backup)) {
            fileSystem.atomicMove(backup, target)
        } else if (fileSystem.exists(backup)) {
            fileSystem.delete(backup, mustExist = false)
        }
        if (fileSystem.exists(temp)) fileSystem.delete(temp, mustExist = false)
    }

    private companion object {
        const val NOTES_FILE = "notex.gpx"
    }
}
