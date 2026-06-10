package com.potato.liftinsight.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.potato.liftinsight.R
import com.potato.liftinsight.common.logging.AndroidAppLogger
import java.util.concurrent.atomic.AtomicInteger

internal class VideoProcessingForegroundService : Service() {

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            AndroidAppLogger.warn(TAG, "Received null intent in onStartCommand; stopping self")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()
        startForegroundWithType(notification)

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AndroidAppLogger.debug(TAG, "Foreground service destroyed")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.video_processing_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.video_processing_notification_text)
                setShowBadge(false)
            }

            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.video_processing_notification_title))
            .setContentText(getString(R.string.video_processing_notification_text))
            .setSmallIcon(R.drawable.ic_app_icon)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val TAG = "VideoProcessingFgSvc"
        private const val CHANNEL_ID = "video_processing_channel"
        private const val NOTIFICATION_ID = 1001

        private val activeWorkCount = AtomicInteger(0)
        private val lock = Any()

        fun start(context: Context) {
            synchronized(lock) {
                val count = activeWorkCount.incrementAndGet()
                AndroidAppLogger.debug(TAG, "Work started; activeCount=$count")

                val intent = Intent(context, VideoProcessingForegroundService::class.java)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopWork(context: Context) {
            synchronized(lock) {
                val count = activeWorkCount.decrementAndGet()
                AndroidAppLogger.debug(TAG, "Work stopped; activeCount=$count")

                if (count <= 0) {
                    activeWorkCount.set(0)
                    context.stopService(Intent(context, VideoProcessingForegroundService::class.java))
                }
            }
        }
    }
}
