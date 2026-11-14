package de.westnordost.streetcomplete.data.osm.edits

import de.westnordost.streetcomplete.data.Database

/** Only a confirmed rejection, never an uncertain remote outcome. */
data class ElementEditReview(val editId: Long, val httpStatus: Int, val reason: String, val rejectedAt: Long)

class ElementUploadRejectedException(val httpStatus: Int, reason: String) : RuntimeException(reason) {
    init { require(httpStatus == 400) }
}
class ElementEditsNeedReviewException(val editIds: List<Long>) :
    RuntimeException("Element edits require review: ${editIds.joinToString()}")
class UnresolvedElementReferenceException : RuntimeException("Unresolved temporary element reference; upload was not sent")

internal object ElementEditReviewsTable {
    const val NAME = "osm_element_edit_reviews"
    const val CREATE = """CREATE TABLE osm_element_edit_reviews (
        edit_id INTEGER PRIMARY KEY,
        state TEXT NOT NULL CHECK(state = 'needs_review'),
        http_status INTEGER NOT NULL CHECK(http_status = 400),
        reason TEXT NOT NULL,
        rejected_at INTEGER NOT NULL
    )"""
}

internal class ElementEditReviewsDao(private val db: Database) {
    fun put(review: ElementEditReview) {
        db.replace(ElementEditReviewsTable.NAME, listOf("edit_id" to review.editId, "state" to "needs_review",
            "http_status" to review.httpStatus, "reason" to sanitizeRejectionReason(review.reason), "rejected_at" to review.rejectedAt))
    }
    fun delete(ids: List<Long>) {
        if (ids.isNotEmpty()) db.delete(ElementEditReviewsTable.NAME, "edit_id IN (${ids.joinToString()})")
    }
}

/** Persist only a bounded server diagnostic, never request headers, URLs or an XML payload. */
internal fun sanitizeRejectionReason(reason: String): String = reason.take(4096)
    .replace(Regex("""<[^>]*>"""), " ")
    .replace(Regex("""https?://\S+""", RegexOption.IGNORE_CASE), "[URL]")
    .replace(Regex("""(?:Bearer|Basic)\s+\S+""", RegexOption.IGNORE_CASE), "[credential]")
    .replace(Regex("""(?:access_token|token|password|authorization|(?:set-)?cookie)["']?\s*[:=][^\r\n]*""",
        RegexOption.IGNORE_CASE), "[credential]")
    .replace(Regex("""[^\s@]+@[^\s@]+"""), "[email]")
    .map { if (it.isISOControl() || it in '\u202a'..'\u202e' || it in '\u2066'..'\u2069') ' ' else it }.joinToString("")
    .replace(Regex("""\s+"""), " ").trim().take(512).ifEmpty { "HTTP 400: edit rejected by server" }
