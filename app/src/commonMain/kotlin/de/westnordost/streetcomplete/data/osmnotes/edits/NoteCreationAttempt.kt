package de.westnordost.streetcomplete.data.osmnotes.edits

/** A durable reservation for CREATE, COMMENT or CLOSE made before POST; null noteId means the server outcome is unknown. */
data class NoteCreationAttempt(val text: String, val noteId: Long?)

class NoteCreationUncertainException(val editId: Long, cause: Throwable? = null) :
    RuntimeException("Note write outcome is uncertain for local edit $editId; automatic retry blocked", cause)

class NoteWriteRejectedException(val editId: Long, cause: Throwable) :
    RuntimeException("The note write was rejected; local edit $editId is retained", cause)

internal object NoteCreationAttemptsTable {
    const val NAME = "osm_note_creation_attempts"
    const val CREATE = """
        CREATE TABLE $NAME (
            edit_id INTEGER PRIMARY KEY,
            text TEXT NOT NULL,
            note_id INTEGER
        );
    """
}

/** OSM rejects these requests before a write, or rolls back their transaction.
 *  406 can be produced during rendering after commit; 408/429/5xx remain ambiguous. */
internal fun Throwable.isDefinitiveNoteWriteRejection(): Boolean {
    if (this is de.westnordost.streetcomplete.data.AuthorizationException ||
        this is de.westnordost.streetcomplete.data.ConflictException) return true
    return generateSequence(this) { it.cause }
        .filterIsInstance<io.ktor.client.plugins.ClientRequestException>()
        .any { it.response.status.value in setOf(400, 401, 403, 404, 405, 409, 410, 413, 414) }
}
