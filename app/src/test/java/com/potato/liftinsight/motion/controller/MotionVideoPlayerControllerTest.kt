package com.potato.liftinsight.motion.controller

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.potato.liftinsight.common.logging.RecordingAppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MotionVideoPlayerControllerTest {
    @Test
    fun registerTap_requiresDoubleTapToTogglePlayback() {
        val logger = RecordingAppLogger()
        val controller = createController(logger)

        assertFalse(controller.registerTap(100L))
        assertEquals(100L, controller.state.lastTapUptimeMs)
        assertTrue(controller.state.isPlaying)

        assertTrue(controller.registerTap(350L))
        assertEquals(0L, controller.state.lastTapUptimeMs)
        assertFalse(controller.state.isPlaying)
        assertTrue(controller.state.gestureState.showPlayPauseIcon)
        assertTrue(
            logger.entries().any { entry ->
                entry.level == "trace" &&
                    entry.tag == "MotionVideoPlayer" &&
                    entry.message == "Registered first tap: uptimeMs=100"
            }
        )
        assertTrue(
            logger.entries().any { entry ->
                entry.level == "debug" &&
                    entry.tag == "MotionVideoPlayer" &&
                    entry.message == "Double tap toggled playback: isPlaying=false"
            }
        )
    }

    @Test
    fun setBasePlaybackSpeed_updatesVisibleState() {
        val logger = RecordingAppLogger()
        val controller = createController(logger)

        controller.hideControls()
        controller.setBasePlaybackSpeed(1.5f)

        assertEquals(1.5f, controller.state.basePlaybackSpeed)
        assertEquals(1.5f, controller.state.effectiveSpeed)
        assertTrue(controller.state.gestureState.showControls)
        assertTrue(
            logger.entries().any { entry ->
                entry.level == "debug" &&
                    entry.tag == "MotionVideoPlayer" &&
                    entry.message == "Updating playback speed: speed=1.5"
            }
        )
    }

    private fun createController(logger: RecordingAppLogger): MotionVideoPlayerController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MotionVideoPlayerController(
            context = context,
            videoUri = Uri.parse("file:///tmp/test.mp4"),
            logger = logger
        )
    }
}
