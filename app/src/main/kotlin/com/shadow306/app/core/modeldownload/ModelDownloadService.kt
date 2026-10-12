package com.shadow306.app.core.modeldownload

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shadow306.app.R
import com.shadow306.app.core.data.prefs.AppPreferences
import com.shadow306.app.core.util.Format
import com.shadow306.app.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that drives [ModelDownloader]. It survives process death
 * because stopWithTask=false and is restarted by WorkManager on boot.
 *
 * Notification shows live progress (bytes, speed, ETA).
 */
class ModelDownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "model_download"
        private const val NOTIF_ID = 1001

        const val ACTION_START = "com.shadow306.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.shadow306.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.shadow306.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.shadow306.action.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            val i = Intent(context, ModelDownloadService::class.java).apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).apply { action = ACTION_PAUSE })
        }

        fun resume(context: Context) {
            context.startService(Intent(context, ModelDownloadService::class.java).apply { action = ACTION_RESUME })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downloadJob: Job? = null

    private val prefs: AppPreferences by lazy { ServiceLocator.providePrefs(this) }
    private val repository: ModelRepository by lazy { ServiceLocator.provideModelRepository(this) }
    private val downloader: ModelDownloader by lazy { ServiceLocator.provideModelDownloader(this) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        scope.launch {
            repository.state.collectLatest { state ->
                updateNotification(state)
                if (state.isComplete) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                startForeground(NOTIF_ID, buildNotification(repository.state.value))
                startDownload()
            }
            ACTION_PAUSE -> {
                downloadJob?.cancel()
                repository.setPaused(true)
            }
            ACTION_CANCEL -> {
                downloadJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                val manifest = repository.loadBundledManifest()
                repository.setPaused(false)
                repository.setError(null)
                downloader.downloadAll(manifest)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                repository.setError(com.shadow306.app.core.modeldownload.DownloadError.Io(e.message ?: "download failed"))
            }
        }
    }

    private fun updateNotification(state: PackDownloadState) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(state))
    }

    private fun buildNotification(state: PackDownloadState): Notification {
        val title = getString(R.string.setup_notification_title)
        val text = if (state.isComplete) {
            getString(R.string.setup_done)
        } else {
            getString(
                R.string.setup_notification_text,
                state.percent,
                Format.bytes(state.downloadedBytes),
                Format.bytes(state.totalBytes)
            )
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(!state.isComplete)
            .setOnlyAlertOnce(true)
            .setProgress(100, state.percent, false)

        if (state.error != null) {
            builder.setContentText(state.error.toString())
        }

        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Downloads AI models for offline use"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        downloadJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }
}
