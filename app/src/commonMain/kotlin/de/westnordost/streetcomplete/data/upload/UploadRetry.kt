package de.westnordost.streetcomplete.data.upload

import de.westnordost.streetcomplete.data.ConnectionException

/** Maximum number of WorkManager automatic retries after the original upload run. */
const val MAX_AUTOMATIC_UPLOAD_RETRIES = 8

/** Initial WorkManager backoff before the first automatic upload retry. */
const val UPLOAD_RETRY_INITIAL_BACKOFF_SECONDS = 30L

/** Temporary transport/server errors that Android upload work should retry. */
fun Throwable.isRetryableUploadError(): Boolean = this is ConnectionException

enum class UploadWorkOutcome { Success, Retry, Failure }

/**
 * Decide the WorkManager outcome for one upload attempt.
 *
 * [runAttemptCount] is WorkManager's zero-based counter: the original run is 0.
 * A [ConnectionException] retries while [runAttemptCount] is `0` through
 * `[maxAutomaticRetries] - 1`. At [maxAutomaticRetries] the budget is exhausted
 * (`Result.failure()`). That is 1 initial attempt + [maxAutomaticRetries] automatic retries.
 *
 * A retry enqueues a new [Uploader.upload] starting from `getOldestUnsynced()`.
 * It does not resume the failed HTTP call. A transport failure after a successful
 * server-side create can theoretically duplicate that create on a later run; that
 * already exists for manual retry.
 */
fun uploadWorkOutcome(
    error: Throwable?,
    runAttemptCount: Int,
    maxAutomaticRetries: Int = MAX_AUTOMATIC_UPLOAD_RETRIES,
): UploadWorkOutcome = when {
    error == null -> UploadWorkOutcome.Success
    error.isRetryableUploadError() && runAttemptCount < maxAutomaticRetries -> UploadWorkOutcome.Retry
    else -> UploadWorkOutcome.Failure
}
