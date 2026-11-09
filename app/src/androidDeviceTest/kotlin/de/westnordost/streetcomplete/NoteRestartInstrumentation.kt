package de.westnordost.streetcomplete

import android.app.Instrumentation
import android.os.Bundle
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.westnordost.streetcomplete.data.*
import de.westnordost.streetcomplete.data.osm.mapdata.LatLon
import de.westnordost.streetcomplete.data.osmnotes.*
import de.westnordost.streetcomplete.data.osmnotes.edits.*
import de.westnordost.streetcomplete.data.osmtracks.TracksApiClient
import de.westnordost.streetcomplete.data.user.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import java.lang.reflect.Proxy

/** Standalone instrumentation application: synthetic DB, loopback HTTP only, no app startup. */
class NoteRestartInstrumentation : Instrumentation() {
    private lateinit var arguments: Bundle
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        this.arguments = arguments ?: Bundle()
        start()
    }
    override fun onStart() {
        try {
            runBlocking { exercise(arguments.getString("phase") ?: error("phase required")) }
            finish(-1, Bundle().apply { putString("result", "PASS pid=${android.os.Process.myPid()}") })
        } catch (e: Throwable) {
            finish(0, Bundle().apply { putString("result", "FAIL ${e.stackTraceToString()}") })
        }
    }
    private suspend fun exercise(phase: String) {
        check(context.packageName != "de.westnordost.streetcomplete.expert.mc.debug")
        val comment = phase.startsWith("comment-")
        val file = java.io.File(context.filesDir, if (comment) "note-comment-restart-synthetic.db" else "note-restart-synthetic.db")
        val photo = java.io.File(context.filesDir, "note-restart-photo.jpg")
        if (phase == "prepare" || phase == "comment-prepare") {
            check(!file.exists()) { "Use a fresh isolated test installation" }
            val old = DatabaseImpl(BundledSQLiteDriver().open(file.path))
            old.initialize(StreetCompleteDatabaseConfigurator)
            NoteEditsDao(old).add(NoteEdit(0, if (comment) 50 else 0, LatLon(1.0, 1.0),
                if (comment) NoteEditAction.COMMENT else NoteEditAction.CREATE,
                "restart-safe", if (comment) emptyList() else listOf(photo.path), 1L, false, !comment, null))
            old.exec("DROP TABLE osm_note_creation_attempts")
            old.exec("PRAGMA user_version = 23")
            old.close()
            photo.writeBytes(byteArrayOf(1, 2, 3))
        }
        val db = DatabaseImpl(BundledSQLiteDriver().open(file.path))
        db.initialize(StreetCompleteDatabaseConfigurator)
        check(db.rawQuery("PRAGMA user_version") { it.getInt("user_version") }.single() == 24)
        val dao = NoteEditsDao(db)
        val controller = NoteEditsControllerImpl(dao, SystemFileSystem)
        val client = HttpClient(CIO) {
            followRedirects = false
        }
        try {
            val api = NotesApiClientImpl(client, "http://127.0.0.1:18765/notes-api/", object : UserAccessTokenSource {
                override val accessToken: String? = null
            }, NotesApiParser())
            val users = object : UserDataSource {
                override val userId = 7L
                override val userName = "synthetic"
                override val unreadMessagesCount = 0
                override fun addListener(listener: UserDataSource.Listener) = Unit
                override fun removeListener(listener: UserDataSource.Listener) = Unit
            }
            val photos = PhotoServiceApiClientImpl(SystemFileSystem, client, "http://127.0.0.1:18765/")
            val uploader = NoteEditsUploader(controller, unused(NoteController::class.java), users, api,
                unused(TracksApiClient::class.java), photos)
            if (comment) {
                if (phase == "comment-prepare") {
                    try { uploader.upload(); error("uncertainty required") }
                    catch (_: NoteCreationUncertainException) { }
                    check(dao.getCreationAttempt(1)?.noteId == null)
                    sendStatus(1, Bundle().apply { putString("result", "READY_TO_KILL pid=${android.os.Process.myPid()}") })
                    while (true) Thread.sleep(1000)
                }
                check(phase == "comment-verify")
                repeat(3) {
                    try { uploader.upload(); error("must remain uncertain") }
                    catch (_: NoteCreationUncertainException) { }
                }
                controller.add(50, NoteEditAction.CLOSE, LatLon(1.0, 1.0), "later close")
                controller.add(51, NoteEditAction.COMMENT, LatLon(1.0, 1.0), "independent")
                try { uploader.upload(); error("first comment remains uncertain") }
                catch (_: NoteCreationUncertainException) { }
                check(!dao.get(1)!!.isSynced && !dao.get(2)!!.isSynced && dao.get(3)!!.isSynced)
                // Simulate explicit user confirmation after checking the mock's history.
                uploader.discardUncertainCreation(1)
                uploader.upload()
                check(dao.getUnsyncedCount() == 0 && dao.get(2)!!.isSynced)
            } else if (phase == "prepare") {
                check(dao.get(1)!!.text == "restart-safe" && dao.get(1)!!.imagePaths == listOf(photo.path))
                try { uploader.upload(); error("uncertainty required") }
                catch (_: NoteCreationUncertainException) { }
                check(dao.getCreationAttempt(1)?.noteId == null)
                check(dao.getCreationAttempt(1)!!.text.contains("/photo.jpg"))
                check(photo.exists())
                sendStatus(1, Bundle().apply { putString("result", "READY_TO_KILL pid=${android.os.Process.myPid()}") })
                while (true) Thread.sleep(1000)
            } else {
                check(phase == "verify")
                check(dao.getUnsyncedCount() == 1 && photo.exists())
                repeat(3) {
                    try { uploader.upload(); error("must remain uncertain") }
                    catch (_: NoteCreationUncertainException) { }
                }
                controller.add(0, NoteEditAction.CREATE, LatLon(2.0, 2.0), "independent")
                try { uploader.upload(); error("original remains uncertain") }
                catch (_: NoteCreationUncertainException) { }
                check(dao.get(2)!!.isSynced && !dao.get(1)!!.isSynced)
                uploader.resolveCreation(1, 1)
                uploader.upload()
                check(dao.getUnsyncedCount() == 0 && dao.getCreationAttempt(1)!!.noteId == 1L)
                check(!photo.exists())
            }
        } finally { client.close(); db.close() }
    }
    private fun <T> unused(type: Class<T>): T = type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        check(method.name == "put") { "Unexpected dependency call: ${method.name}" }
        null
    })
}
