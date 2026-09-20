package de.westnordost.streetcomplete.data.upload

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class UploadWorkerRequestTest {

    @Test fun `automatic upload keeps existing unique work`() {
        assertEquals(ExistingWorkPolicy.KEEP, uploadExistingWorkPolicy(isUserInitiated = false))
    }

    @Test fun `user initiated upload replaces existing unique work`() {
        assertEquals(ExistingWorkPolicy.REPLACE, uploadExistingWorkPolicy(isUserInitiated = true))
    }

    @Test fun `upload work requires a connected network`() {
        assertEquals(NetworkType.CONNECTED, uploadWorkConstraints().requiredNetworkType)
    }

    @Test fun `upload work uses exponential backoff of 30 seconds`() {
        val automatic = UploadWorker.createWorkRequest(isUserInitiated = false)
        val manual = UploadWorker.createWorkRequest(isUserInitiated = true)
        for (request in listOf(automatic, manual)) {
            assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
            assertEquals(
                TimeUnit.SECONDS.toMillis(UPLOAD_RETRY_INITIAL_BACKOFF_SECONDS),
                request.workSpec.backoffDelayDuration
            )
            assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        }
    }
}
