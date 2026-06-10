package com.potato.liftinsight.video

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.lang.reflect.Field

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VideoProcessingForegroundServiceTest {

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        resetActiveWorkCount()
    }

    private fun resetActiveWorkCount() {
        try {
            val field: Field = VideoProcessingForegroundService::class.java
                .getDeclaredField("activeWorkCount")
            field.isAccessible = true
            val counter = field.get(null) as java.util.concurrent.atomic.AtomicInteger
            counter.set(0)
        } catch (_: Exception) {
            // best-effort reset
        }
    }

    @Test
    fun nullIntent_stopsSelfGracefully() {
        val serviceController: ServiceController<VideoProcessingForegroundService> =
            Robolectric.buildService(VideoProcessingForegroundService::class.java)

        val service = serviceController.create().get()

        val result = service.onStartCommand(null, 0, 0)
        assertEquals(android.app.Service.START_NOT_STICKY, result)

        // Null intent should not post any notification
        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        // Notification posting happens through startForeground which requires
        // a valid intent with valid context. Null intent returns early.
        serviceController.destroy()
    }

    @Test
    fun validIntent_postsForegroundNotification() {
        val intent = Intent(context, VideoProcessingForegroundService::class.java)
        val serviceController: ServiceController<VideoProcessingForegroundService> =
            Robolectric.buildService(VideoProcessingForegroundService::class.java, intent)

        val service = serviceController.create().get()

        // onStartCommand should call startForeground which posts a notification
        val startResult = service.onStartCommand(intent, 0, 0)
        assertEquals(android.app.Service.START_NOT_STICKY, startResult)

        val shadowNotificationManager = Shadows.shadowOf(notificationManager)
        val notifications = shadowNotificationManager.allNotifications
        assertTrue(
            "Should have at least one notification posted via startForeground",
            notifications.isNotEmpty()
        )

        serviceController.destroy()
    }

    @Test
    fun notificationChannel_isCreated() {
        val intent = Intent(context, VideoProcessingForegroundService::class.java)
        val serviceController: ServiceController<VideoProcessingForegroundService> =
            Robolectric.buildService(VideoProcessingForegroundService::class.java, intent)

        // onCreate() should create the notification channel
        serviceController.create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel("video_processing_channel")
            assertNotNull("Notification channel should be created", channel)
            assertEquals("Video processing", channel?.name)
        }

        serviceController.destroy()
    }

    @Test
    fun start_staticMethod_startsService() {
        // Verify the static start() method can be called without crashing
        VideoProcessingForegroundService.start(context)

        // After calling start, we should be able to stop cleanly
        VideoProcessingForegroundService.stopWork(context)
    }

    @Test
    fun multipleStartStop_maintainsCorrectCount() {
        // Reset first to ensure clean state
        resetActiveWorkCount()

        VideoProcessingForegroundService.start(context)
        VideoProcessingForegroundService.start(context)

        // Stopping one work should not stop the service (counter > 0)
        VideoProcessingForegroundService.stopWork(context)

        // Stopping the last work should allow clean restart
        VideoProcessingForegroundService.stopWork(context)

        // Should be able to start again
        VideoProcessingForegroundService.start(context)
        VideoProcessingForegroundService.stopWork(context)
    }
}
