package app.litterguard.core.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import app.litterguard.core.domain.ports.MonitoringStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the camera working while the app is in the background or the screen shows something else (FR-8.1). A camera
 * foreground service may only be started from the visible app.
 */
@AndroidEntryPoint
class MonitoringService : LifecycleService() {
    @Inject
    internal lateinit var session: MonitoringSession

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            session.stop()
            stopSelf()
        } else {
            // Must happen promptly, and before the camera is opened.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
            if (!session.isRunning) {
                session.start(lifecycleScope)
                lifecycleScope.launch {
                    session.status.filterIsInstance<MonitoringStatus.Failed>().first()
                    stopSelf()
                }
            }
        }
        // Android doesn't allow a camera service to be restarted from the background.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        session.stop()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.monitoring_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val builder =
            NotificationCompat
                .Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_monitoring)
                .setContentTitle(getString(R.string.monitoring_notification_title))
                .setContentText(getString(R.string.monitoring_notification_text))
                .setOngoing(true)
                .addAction(0, getString(R.string.monitoring_notification_stop), stopIntent())
        packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
            builder.setContentIntent(PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_IMMUTABLE))
        }
        return builder.build()
    }

    private fun stopIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            0,
            Intent(this, MonitoringService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

    internal companion object {
        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "app.litterguard.action.STOP_MONITORING"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitoringService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
