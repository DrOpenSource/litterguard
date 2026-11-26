package app.litterguard.core.alerts.delivery

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.litterguard.core.domain.ports.AlertSink
import app.litterguard.core.model.IncidentId
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val KEY_INCIDENT_ID = "incidentId"

@HiltWorker
internal class DeliveryWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted parameters: WorkerParameters,
        private val delivery: IncidentDelivery,
    ) : CoroutineWorker(context, parameters) {
        override suspend fun doWork(): Result {
            val id = inputData.getString(KEY_INCIDENT_ID) ?: return Result.failure()
            return when (delivery.deliver(IncidentId(id))) {
                DeliveryOutcome.DONE -> Result.success()
                DeliveryOutcome.RETRY -> Result.retry()
                DeliveryOutcome.FAILED -> Result.failure()
            }
        }
    }

/** One unique work item per incident, so alerts survive restarts and offline periods without duplicating. */
internal class WorkManagerAlertSink
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AlertSink {
        override fun enqueue(id: IncidentId) {
            val request =
                OneTimeWorkRequestBuilder<DeliveryWorker>()
                    .setInputData(workDataOf(KEY_INCIDENT_ID to id.value))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                    .build()
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork("deliver-${id.value}", ExistingWorkPolicy.KEEP, request)
        }

        private companion object {
            const val BACKOFF_SECONDS = 15L
        }
    }
