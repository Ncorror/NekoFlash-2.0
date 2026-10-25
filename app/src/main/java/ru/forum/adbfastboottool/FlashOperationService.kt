package ru.forum.adbfastboottool

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer

/** Owns admitted USB execution. A successful foreground promotion is the start acknowledgement. */
class FlashOperationService : Service() {
    private var foregroundStarted = false
    private var activeOperation: ServiceOwnedOperation? = null
    private var latestStartId = 0
    private var lastNotificationAtMs = 0L
    private lateinit var session: DeviceViewModel
    private val progressObserver = Observer<DeviceViewModel.OperationProgress?> { progress ->
        val task = activeOperation
        if (task != null && task.tracksProgress && progress != null && !task.job.isCompleted &&
            SystemClock.elapsedRealtime() - lastNotificationAtMs >= 500L) {
            lastNotificationAtMs = SystemClock.elapsedRealtime()
            notifySafely(buildNotification(task.title, progress.detail, progress.percent, ongoing = true))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannelIfNeeded()
        session = DeviceSessionStore.get(application)
        session.operationProgress.observeForever(progressObserver)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        val task = registry.find(intent?.getStringExtra(EXTRA_OPERATION_ID))
        try {
            // Even a late/cancelled start must satisfy the platform foreground
            // contract. Its token is never allowed to start another operation.
            val shown = task ?: activeOperation
            ServiceCompat.startForeground(this, NOTIFICATION_ID,
                buildNotification(shown?.title ?: getString(R.string.app_name),
                    shown?.description ?: getString(R.string.rev20_service_preparing), -1, ongoing = true),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            foregroundStarted = true
        } catch (error: Exception) {
            task?.rejectStartup(getString(R.string.rev20_foreground_failed, error.message ?: error.javaClass.simpleName))
            activeOperation?.cancel("Foreground service promotion failed", serviceDestroyed = true)
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (task != null && !task.job.isCompleted) {
            activeOperation = task
            task.acknowledgeForeground()
        } else if (registry.isEmpty() && activeOperation?.job?.isCompleted != false) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelfResult(startId)
        }
        // The OS must never redeliver/replay a destructive command after process death.
        return START_NOT_STICKY
    }

    private fun operationFinished(id: String) {
        val task = activeOperation
        if (task?.id != id || !registry.isEmpty()) return
        val state = session.lastPersistedOperation(id)
        if (state != null) {
            val status = when (state.phase) {
                PersistedOperationPhase.SUCCESS -> R.string.layout_operation_center_completed
                PersistedOperationPhase.CANCELLED -> R.string.layout_operation_center_cancelled
                PersistedOperationPhase.VERIFY_PENDING -> R.string.layout_operation_center_verify_pending
                PersistedOperationPhase.INTERRUPTED -> R.string.rev20_operation_interrupted
                else -> R.string.layout_operation_center_failed
            }
            notifySafely(buildNotification("${task.title} · ${getString(status)}", state.detail, state.percent, ongoing = false))
        }
        if (foregroundStarted) stopForeground(if (state != null) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
        activeOperation = null
        stopSelfResult(latestStartId)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        if (::session.isInitialized) session.operationProgress.removeObserver(progressObserver)
        val task = activeOperation
        if (task != null && !task.job.isCompleted) {
            task.cancel("Foreground execution service was destroyed", serviceDestroyed = true)
            // Existing two-phase shutdown waits for Java/native completion before closing USB.
            session.disconnectCurrent()
        }
        activeOperation = null
        foregroundStarted = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, text: String, percent: Int, ongoing: Boolean): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .addAction(R.drawable.ic_notification, getString(R.string.rev20_notification_open), open)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(if (ongoing) NotificationCompat.CATEGORY_PROGRESS else NotificationCompat.CATEGORY_STATUS)
            .apply { if (ongoing) setProgress(100, percent.coerceIn(0, 100), percent < 0) }
            .build()
    }

    private fun notifySafely(notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
            .onFailure { android.util.Log.w(TAG, "Notification update failed", it) }
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.service_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "FlashOpService"
        private const val CHANNEL_ID = "adb_fastboot_operations"
        private const val NOTIFICATION_ID = 1401
        private const val EXTRA_OPERATION_ID = "operation_id"
        private const val START_ACK_TIMEOUT_MS = 10_000L
        private val registry = ServiceOperationRegistry()
        private val mainHandler = Handler(Looper.getMainLooper())
        @Volatile private var instance: FlashOperationService? = null

        internal fun submit(context: Context, task: ServiceOwnedOperation) {
            if (!registry.register(task)) {
                task.rejectStartup(context.getString(R.string.rev20_service_busy))
                return
            }
            val timeout = Runnable { task.rejectStartup(context.getString(R.string.rev20_service_start_timeout)) }
            task.job.invokeOnCompletion { mainHandler.removeCallbacks(timeout) }
            mainHandler.postDelayed(timeout, START_ACK_TIMEOUT_MS)
            try {
                ContextCompat.startForegroundService(context, Intent(context, FlashOperationService::class.java)
                    .putExtra(EXTRA_OPERATION_ID, task.id))
            } catch (error: Exception) {
                task.rejectStartup(context.getString(R.string.rev20_foreground_failed, error.message ?: error.javaClass.simpleName))
            }
        }

        internal fun complete(id: String) {
            registry.remove(id)
            // Stopping never starts a new service. Late callbacks carry their own token.
            mainHandler.post { instance?.operationFinished(id) }
        }
    }
}
