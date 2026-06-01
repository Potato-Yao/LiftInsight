package com.potato.liftinsight.camera.controller

import android.Manifest
import android.content.Context
import android.media.CamcorderProfile
import android.os.Environment
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.potato.liftinsight.common.logging.AndroidAppLogger
import com.potato.liftinsight.common.logging.AppLogger
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService

data class CameraCaptureDetails(
    val resolutionText: String,
    val frameRateText: String
)

data class CameraSession(
    val recorder: Recorder,
    val camera: Camera
)

class CameraController(
    private val logger: AppLogger = AndroidAppLogger
) {
    fun missingPermissions(
        hasCameraPermission: Boolean,
        hasAudioPermission: Boolean
    ): Array<String> {
        val permissions = mutableListOf<String>()

        if (!hasCameraPermission) {
            permissions += Manifest.permission.CAMERA
        }

        if (!hasAudioPermission) {
            permissions += Manifest.permission.RECORD_AUDIO
        }

        return permissions.toTypedArray()
    }

    fun bindCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        defaultResolution: String,
        defaultFrameRate: String,
        onSessionReady: (CameraSession) -> Unit,
        onCaptureDetailsChanged: (CameraCaptureDetails) -> Unit
    ) {
        logger.debug(TAG, "Binding camera preview and recorder")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val qualitySelector = QualitySelector.fromOrderedList(
                listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            )
            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()
            val videoCapture = VideoCapture.Builder(recorder).build()

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                videoCapture
            )

            onSessionReady(CameraSession(recorder = recorder, camera = camera))
            val captureDetails = captureDetails(
                camera = camera,
                preview = preview,
                defaultResolution = defaultResolution,
                defaultFrameRate = defaultFrameRate
            )

            logger.info(
                TAG,
                "Camera session ready: resolution=${captureDetails.resolutionText}, frameRate=${captureDetails.frameRateText}"
            )

            onCaptureDetailsChanged(captureDetails)
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(
        context: Context,
        recorder: Recorder,
        cameraExecutor: ExecutorService,
        motionId: Int,
        setIndex: Int,
        onFileReady: (File) -> Unit,
        onRecording: (Recording) -> Unit,
        onRecordingFinished: () -> Unit
    ): Boolean {
        val outputDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val videoFile = File(outputDir, videoFileName(motionId, setIndex))
        logger.info(
            TAG,
            "Starting camera recording: motionId=$motionId, setIndex=$setIndex, output=${videoFile.name}"
        )
        onFileReady(videoFile)

        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        val pendingRecording = recorder.prepareRecording(context, outputOptions)

        var recording: Recording? = null
        recording = pendingRecording.start(cameraExecutor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                if (event.hasError()) {
                    logger.error(
                        TAG,
                        "Camera recording finalized with error: motionId=$motionId, setIndex=$setIndex, output=${videoFile.name}, errorCode=${event.error}",
                        event.cause
                    )
                } else {
                    logger.info(
                        TAG,
                        "Camera recording finalized: motionId=$motionId, setIndex=$setIndex, output=${videoFile.name}"
                    )
                }

                try {
                    recording?.close()
                } catch (_: Exception) {
                }

                onRecordingFinished()
            }
        }

        val activeRecording = recording ?: run {
            logger.warn(
                TAG,
                "Camera recording did not start: motionId=$motionId, setIndex=$setIndex, output=${videoFile.name}"
            )
            return false
        }

        onRecording(activeRecording)
        return true
    }

    private fun captureDetails(
        camera: Camera,
        preview: Preview,
        defaultResolution: String,
        defaultFrameRate: String
    ): CameraCaptureDetails {
        val resolution = preview.resolutionInfo?.resolution
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val supportedQualities = QualitySelector.getSupportedQualities(camera.cameraInfo)
        val selectedQuality = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            .firstOrNull(supportedQualities::contains)

        val resolutionText = if (resolution != null) {
            "${resolution.width}x${resolution.height}"
        } else {
            defaultResolution
        }

        val frameRateText = selectedQuality
            ?.let { quality -> profileFrameRate(cameraId, quality) }
            ?.let { frameRate -> "${frameRate}fps" }
            ?: defaultFrameRate

        return CameraCaptureDetails(
            resolutionText = resolutionText,
            frameRateText = frameRateText
        )
    }

    private fun profileFrameRate(cameraId: String, quality: Quality): Int? {
        val numericCameraId = cameraId.toIntOrNull() ?: return null
        val profileQuality = when (quality) {
            Quality.UHD -> CamcorderProfile.QUALITY_2160P
            Quality.FHD -> CamcorderProfile.QUALITY_1080P
            Quality.HD -> CamcorderProfile.QUALITY_720P
            Quality.SD -> CamcorderProfile.QUALITY_480P
            else -> return null
        }

        if (!CamcorderProfile.hasProfile(numericCameraId, profileQuality)) {
            return null
        }

        return CamcorderProfile.get(numericCameraId, profileQuality).videoFrameRate
    }

    companion object {
        private const val TAG = "CameraController"
    }
}

private fun videoFileName(motionId: Int, setIndex: Int): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    return "${formatter.format(Instant.now())}-$motionId-$setIndex.mp4"
}
