package de.westnordost.streetcomplete.data.upload

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.westnordost.streetcomplete.ApplicationConstants
import de.westnordost.streetcomplete.data.sync.createSyncNotification
import java.util.concurrent.TimeUnit

class AndroidUploadController(private val context: Context) : UploadController {
    override fun upload(isUserInitiated: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            Uploader.TAG,
            uploadExistingWorkPolicy(isUserInitiated),
            UploadWorker.createWorkRequest(isUserInitiated)
        )
    }
}

class UploadWorker(
    private val uploader: Uploader,
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notificationId = ApplicationConstants.NOTIFICATIONS_ID_SYNC
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        val notification = createSyncNotification(context, cancelIntent)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    override suspend fun doWork(): Result =
        try {
            uploader.upload()
            Result.success()
        } catch (e: Exception) {
            when (uploadWorkOutcome(e, runAttemptCount)) {
                UploadWorkOutcome.Success -> Result.success()
                UploadWorkOutcome.Retry -> Result.retry()
                UploadWorkOutcome.Failure -> Result.failure()
            }
        }

    companion object {
        fun createWorkRequest(isUserInitiated: Boolean): OneTimeWorkRequest {
            val builder = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(uploadWorkConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    UPLOAD_RETRY_INITIAL_BACKOFF_SECONDS,
                    TimeUnit.SECONDS
                )
            if (isUserInitiated) builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            return builder.build()
        }
    }
}

/** KEEP so AutoSyncer cannot clobber a backed-off retry. REPLACE so a manual Upload can. */
internal fun uploadExistingWorkPolicy(isUserInitiated: Boolean): ExistingWorkPolicy =
    if (isUserInitiated) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP

internal fun uploadWorkConstraints(): Constraints =
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
