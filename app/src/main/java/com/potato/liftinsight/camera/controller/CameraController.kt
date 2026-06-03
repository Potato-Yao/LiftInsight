package com.potato.liftinsight.camera.controller

import android.Manifest
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.CamcorderProfile
import android.os.Environment
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
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
    val frameRateText: String,
    val zoomRatio: Float,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val lensFacingLabel: String,
    val isWideAngleZoomRange: Boolean,
    val supportsWideAngleSwitch: Boolean
)

data class CameraZoomDetails(
    val zoomRatio: Float,
    val minZoomRatio: Float,
    val maxZoomRatio: Float,
    val appZoomRatio: Float,
    val appMinZoomRatio: Float,
    val appMaxZoomRatio: Float,
    val lensFacingLabel: String,
    val isWideAngleZoomRange: Boolean,
    val supportsWideAngleSwitch: Boolean
)

data class CameraSession(
    val recorder: Recorder,
    val camera: Camera,
    val activeLens: CameraLensType
)

enum class CameraLensType {
    MAIN,
    WIDE
}

data class RearCameraSelection(
    val cameraSelector: CameraSelector,
    val activeLens: CameraLensType,
    val supportsWideAngleSwitch: Boolean,
    val wideToMainZoomRatio: Float
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
        lensType: CameraLensType,
        defaultResolution: String,
        defaultFrameRate: String,
        onSessionReady: (CameraSession) -> Unit,
        onCaptureDetailsChanged: (CameraCaptureDetails) -> Unit
    ) {
        logger.debug(TAG, "Binding camera preview and recorder")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val rearCameraSelection = selectRearCamera(
                context = context,
                cameraProvider = cameraProvider,
                requestedLensType = lensType
            )
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
                rearCameraSelection.cameraSelector,
                preview,
                videoCapture
            )

            onSessionReady(
                CameraSession(
                    recorder = recorder,
                    camera = camera,
                    activeLens = rearCameraSelection.activeLens
                )
            )
            val captureDetails = captureDetails(
                context = context,
                camera = camera,
                preview = preview,
                activeLens = rearCameraSelection.activeLens,
                supportsWideAngleSwitchOverride = rearCameraSelection.supportsWideAngleSwitch,
                wideToMainZoomRatio = rearCameraSelection.wideToMainZoomRatio,
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
        val videoFile = createVideoOutputFile(context, motionId, setIndex)
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

    fun createVideoOutputFile(
        context: Context,
        motionId: Int,
        setIndex: Int
    ): File {
        val outputDir = videoOutputDirectory(context)

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        return File(outputDir, videoFileName(motionId, setIndex))
    }

    fun fileProviderAuthority(context: Context): String {
        return "${context.packageName}.fileprovider"
    }

    fun videoOutputDirectory(context: Context): File {
        return context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: context.filesDir
    }

    private fun captureDetails(
        context: Context,
        camera: Camera,
        preview: Preview,
        activeLens: CameraLensType,
        supportsWideAngleSwitchOverride: Boolean,
        wideToMainZoomRatio: Float,
        defaultResolution: String,
        defaultFrameRate: String
    ): CameraCaptureDetails {
        val resolution = preview.resolutionInfo?.resolution
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val supportedQualities = QualitySelector.getSupportedQualities(camera.cameraInfo)
        val selectedQuality = listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD)
            .firstOrNull(supportedQualities::contains)
        val zoomDetails = zoomDetails(
            context = context,
            camera = camera,
            activeLens = activeLens,
            supportsWideAngleSwitchOverride = supportsWideAngleSwitchOverride,
            wideToMainZoomRatio = wideToMainZoomRatio
        )

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
            frameRateText = frameRateText,
            zoomRatio = zoomDetails.appZoomRatio,
            minZoomRatio = zoomDetails.appMinZoomRatio,
            maxZoomRatio = zoomDetails.appMaxZoomRatio,
            lensFacingLabel = zoomDetails.lensFacingLabel,
            isWideAngleZoomRange = zoomDetails.isWideAngleZoomRange,
            supportsWideAngleSwitch = zoomDetails.supportsWideAngleSwitch
        )
    }

    fun zoomDetails(
        context: Context,
        camera: Camera,
        activeLens: CameraLensType,
        supportsWideAngleSwitchOverride: Boolean? = null,
        wideToMainZoomRatio: Float = 1f
    ): CameraZoomDetails {
        val cameraId = Camera2CameraInfo.from(camera.cameraInfo).cameraId
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val zoomState = camera.cameraInfo.zoomState.value
        val cameraCharacteristics = runCatching {
            cameraManager.getCameraCharacteristics(cameraId)
        }.getOrNull()
        val lensFacingLabel = lensFacingLabel(cameraCharacteristics)
        val supportsWideAngleSwitch = supportsWideAngleSwitchOverride
            ?: supportsWideAngleSwitch(cameraManager, cameraCharacteristics)
        val physicalZoomRatio = zoomState?.zoomRatio ?: 1f
        val physicalMinZoomRatio = zoomState?.minZoomRatio ?: 1f
        val physicalMaxZoomRatio = zoomState?.maxZoomRatio ?: 1f
        val appZoomRatio = when (activeLens) {
            CameraLensType.WIDE -> physicalZoomRatio * wideToMainZoomRatio
            CameraLensType.MAIN -> physicalZoomRatio
        }
        val appMinZoomRatio = when (activeLens) {
            CameraLensType.WIDE -> physicalMinZoomRatio * wideToMainZoomRatio
            CameraLensType.MAIN -> physicalMinZoomRatio
        }
        val appMaxZoomRatio = when (activeLens) {
            CameraLensType.WIDE -> minOf(physicalMaxZoomRatio * wideToMainZoomRatio, 1f)
            CameraLensType.MAIN -> physicalMaxZoomRatio
        }

        return CameraZoomDetails(
            zoomRatio = physicalZoomRatio,
            minZoomRatio = physicalMinZoomRatio,
            maxZoomRatio = physicalMaxZoomRatio,
            appZoomRatio = appZoomRatio,
            appMinZoomRatio = appMinZoomRatio,
            appMaxZoomRatio = appMaxZoomRatio,
            lensFacingLabel = lensFacingLabel,
            isWideAngleZoomRange = supportsWideAngleSwitch && activeLens == CameraLensType.WIDE,
            supportsWideAngleSwitch = supportsWideAngleSwitch
        )
    }

    fun pinchZoom(camera: Camera, currentZoomRatio: Float, scaleFactor: Float): Float {
        val zoomState = camera.cameraInfo.zoomState.value ?: return currentZoomRatio
        val updatedZoomRatio = (currentZoomRatio * scaleFactor)
            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)

        camera.cameraControl.setZoomRatio(updatedZoomRatio)
        return updatedZoomRatio
    }

    fun shouldSwitchToWideLens(
        activeLens: CameraLensType,
        requestedZoomRatio: Float,
        supportsWideAngleSwitch: Boolean
    ): Boolean {
        return supportsWideAngleSwitch && activeLens == CameraLensType.MAIN && requestedZoomRatio < 1f
    }

    fun shouldSwitchToMainLens(
        activeLens: CameraLensType,
        requestedZoomRatio: Float,
        supportsWideAngleSwitch: Boolean
    ): Boolean {
        return supportsWideAngleSwitch && activeLens == CameraLensType.WIDE && requestedZoomRatio >= 1f
    }

    fun wideLensZoomRatio(requestedZoomRatio: Float, wideToMainZoomRatio: Float): Float {
        val normalizedZoomRatio = if (wideToMainZoomRatio > 0f) {
            requestedZoomRatio / wideToMainZoomRatio
        } else {
            requestedZoomRatio
        }

        return normalizedZoomRatio.coerceAtLeast(1f)
    }

    fun mainLensZoomRatio(requestedZoomRatio: Float): Float {
        return requestedZoomRatio.coerceAtLeast(1f)
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

    private fun lensFacingLabel(cameraCharacteristics: CameraCharacteristics?): String {
        return when (cameraCharacteristics?.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Rear"
        }
    }

    private fun supportsWideAngleSwitch(
        cameraManager: CameraManager,
        activeCharacteristics: CameraCharacteristics?
    ): Boolean {
        val activeFacing = activeCharacteristics?.get(CameraCharacteristics.LENS_FACING) ?: return false
        val activeFocalLengths = activeCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            ?: return false
        val activeMinFocalLength = activeFocalLengths.minOrNull() ?: return false

        return cameraManager.cameraIdList.any { candidateId ->
            val candidate = runCatching {
                cameraManager.getCameraCharacteristics(candidateId)
            }.getOrNull() ?: return@any false

            if (candidate.get(CameraCharacteristics.LENS_FACING) != activeFacing) {
                return@any false
            }

            val focalLengths = candidate.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?: return@any false
            val candidateMinFocalLength = focalLengths.minOrNull() ?: return@any false

            candidateMinFocalLength < activeMinFocalLength
        }
    }

    private fun isWideAngleZoomRange(
        zoomState: ZoomState?,
        cameraCharacteristics: CameraCharacteristics?
    ): Boolean {
        val minZoomRatio = zoomState?.minZoomRatio ?: return false
        val zoomRatio = zoomState.zoomRatio

        if (minZoomRatio >= 1f) {
            return false
        }

        val thresholds = cameraCharacteristics
            ?.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        val lowerBound = thresholds?.lower ?: minZoomRatio

        return zoomRatio <= maxOf(1f, lowerBound + (1f - lowerBound) * WIDE_ANGLE_THRESHOLD_RATIO)
    }

    private fun selectRearCamera(
        context: Context,
        cameraProvider: ProcessCameraProvider,
        requestedLensType: CameraLensType
    ): RearCameraSelection {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val rearCameraIds = cameraManager.cameraIdList.mapNotNull { cameraId ->
            val characteristics = runCatching {
                cameraManager.getCameraCharacteristics(cameraId)
            }.getOrNull() ?: return@mapNotNull null

            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing != CameraCharacteristics.LENS_FACING_BACK) {
                return@mapNotNull null
            }

            val focalLength = characteristics
                .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.minOrNull()
                ?: return@mapNotNull null

            RearLensCandidate(cameraId = cameraId, focalLength = focalLength)
        }.sortedBy { it.focalLength }

        val wideCandidate = rearCameraIds.firstOrNull()
        val mainCandidate = rearCameraIds.lastOrNull()
        val supportsWideAngleSwitch = wideCandidate != null && mainCandidate != null &&
            wideCandidate.cameraId != mainCandidate.cameraId
        val wideToMainZoomRatio = if (supportsWideAngleSwitch) {
            (wideCandidate?.focalLength ?: 1f) / (mainCandidate?.focalLength ?: 1f)
        } else {
            1f
        }

        val selectedCameraId = when {
            requestedLensType == CameraLensType.WIDE && supportsWideAngleSwitch -> wideCandidate?.cameraId
            else -> mainCandidate?.cameraId ?: wideCandidate?.cameraId
        } ?: return RearCameraSelection(
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            activeLens = CameraLensType.MAIN,
            supportsWideAngleSwitch = false,
            wideToMainZoomRatio = 1f
        )

        val cameraSelector = CameraSelector.Builder()
            .addCameraFilter { cameraInfos ->
                cameraInfos.filter { cameraInfo ->
                    Camera2CameraInfo.from(cameraInfo).cameraId == selectedCameraId
                }
            }
            .build()

        val activeLens = if (supportsWideAngleSwitch && selectedCameraId == wideCandidate?.cameraId) {
            CameraLensType.WIDE
        } else {
            CameraLensType.MAIN
        }

        val validatedSelector = if (cameraProvider.hasCamera(cameraSelector)) {
            cameraSelector
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        return RearCameraSelection(
            cameraSelector = validatedSelector,
            activeLens = if (validatedSelector == cameraSelector) activeLens else CameraLensType.MAIN,
            supportsWideAngleSwitch = supportsWideAngleSwitch,
            wideToMainZoomRatio = wideToMainZoomRatio
        )
    }

    companion object {
        private const val TAG = "CameraController"
        private const val WIDE_ANGLE_THRESHOLD_RATIO = 0.35f
    }
}

private data class RearLensCandidate(
    val cameraId: String,
    val focalLength: Float
)

internal fun videoFileName(motionId: Int, setIndex: Int): String {
    val formatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd-HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    return "${formatter.format(Instant.now())}-$motionId-$setIndex.mp4"
}
