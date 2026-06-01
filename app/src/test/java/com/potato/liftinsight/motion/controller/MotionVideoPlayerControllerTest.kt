package com.potato.liftinsight.motion.controller

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
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
        val controller = createController()

        assertFalse(controller.registerTap(100L))
        assertEquals(100L, controller.state.lastTapUptimeMs)
        assertTrue(controller.state.isPlaying)

        assertTrue(controller.registerTap(350L))
        assertEquals(0L, controller.state.lastTapUptimeMs)
        assertFalse(controller.state.isPlaying)
        assertTrue(controller.state.gestureState.showPlayPauseIcon)
    }

    @Test
    fun setBasePlaybackSpeed_updatesVisibleState() {
        val controller = createController()

        controller.hideControls()
        controller.setBasePlaybackSpeed(1.5f)

        assertEquals(1.5f, controller.state.basePlaybackSpeed)
        assertEquals(1.5f, controller.state.effectiveSpeed)
        assertTrue(controller.state.gestureState.showControls)
    }

    private fun createController(): MotionVideoPlayerController {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MotionVideoPlayerController(context, Uri.parse("file:///tmp/test.mp4"))
    }
}
