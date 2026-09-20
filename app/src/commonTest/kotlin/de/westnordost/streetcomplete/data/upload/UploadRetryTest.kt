package de.westnordost.streetcomplete.data.upload

import de.westnordost.streetcomplete.data.ApiClientException
import de.westnordost.streetcomplete.data.AuthorizationException
import de.westnordost.streetcomplete.data.ConnectionException
import de.westnordost.streetcomplete.data.ConflictException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadRetryTest {

    @Test fun `ConnectionException is retryable`() {
        assertTrue(ConnectionException("temporary").isRetryableUploadError())
    }

    @Test fun `ApiClientException is not retryable`() {
        assertFalse(ApiClientException("client error").isRetryableUploadError())
    }

    @Test fun `ConflictException is not retryable`() {
        assertFalse(ConflictException("conflict").isRetryableUploadError())
    }

    @Test fun `AuthorizationException is not retryable`() {
        assertFalse(AuthorizationException("unauthorized").isRetryableUploadError())
    }

    @Test fun `arbitrary runtime exception is not retryable`() {
        assertFalse(IllegalStateException("bug").isRetryableUploadError())
        assertFalse(RuntimeException("bug").isRetryableUploadError())
    }

    @Test fun `success has no error`() {
        assertEquals(UploadWorkOutcome.Success, uploadWorkOutcome(error = null, runAttemptCount = 0))
    }

    @Test fun `permanent failure fails on the original run`() {
        assertEquals(
            UploadWorkOutcome.Failure,
            uploadWorkOutcome(AuthorizationException(), runAttemptCount = 0)
        )
        assertEquals(
            UploadWorkOutcome.Failure,
            uploadWorkOutcome(ApiClientException(), runAttemptCount = 0)
        )
    }

    @Test fun `ConnectionException retries on the original run`() {
        assertEquals(
            UploadWorkOutcome.Retry,
            uploadWorkOutcome(ConnectionException(), runAttemptCount = 0)
        )
    }

    @Test fun `ConnectionException retries on automatic attempts 1 through 7`() {
        assertEquals(8, MAX_AUTOMATIC_UPLOAD_RETRIES)
        for (count in 1..7) {
            assertEquals(
                UploadWorkOutcome.Retry,
                uploadWorkOutcome(ConnectionException(), runAttemptCount = count),
                "expected retry at runAttemptCount=$count"
            )
        }
    }

    @Test fun `ConnectionException fails when runAttemptCount is 8`() {
        assertEquals(
            UploadWorkOutcome.Failure,
            uploadWorkOutcome(ConnectionException(), runAttemptCount = 8)
        )
        assertEquals(
            UploadWorkOutcome.Failure,
            uploadWorkOutcome(ConnectionException(), runAttemptCount = MAX_AUTOMATIC_UPLOAD_RETRIES)
        )
    }
}
