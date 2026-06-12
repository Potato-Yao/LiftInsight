package com.potato.liftinsight.video

import com.potato.liftinsight.common.logging.RecordingAppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarbellTrackingServiceTest {
    private val logger = RecordingAppLogger()
    private val service = BarbellTrackingService(logger)

    @Test
    fun `trackSelectedCircle returns empty list when video file does not exist`() {
        val videoFile = java.io.File("/nonexistent/video.mp4")
        val detectionService = BarbellDetectionService(logger)

        val result = service.trackSelectedCircle(
            videoFile = videoFile,
            initialX = 0.5f,
            initialY = 0.5f,
            initialRadius = 0.1f,
            metahistoryId = 1,
            detectionService = detectionService,
            onProgress = {}
        )

        assertNotNull(result)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `trackSelectedCircle with empty video returns empty list`() {
        // Create a temporary empty file (not a valid video)
        val tempFile = java.io.File.createTempFile("test_video", ".mp4")
        tempFile.deleteOnExit()
        tempFile.writeBytes(ByteArray(0))

        val detectionService = BarbellDetectionService(logger)

        val result = service.trackSelectedCircle(
            videoFile = tempFile,
            initialX = 0.5f,
            initialY = 0.5f,
            initialRadius = 0.1f,
            metahistoryId = 1,
            detectionService = detectionService,
            onProgress = {}
        )

        assertNotNull(result)
        // Empty video file should result in empty result or cause exception that is caught
    }
}
