package com.potato.liftinsight.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.potato.liftinsight.R

/**
 * Represents a selectable camera lens option in the in‑app camera.
 */
enum class CameraLens(val labelRes: Int) {
    BACK(R.string.camera_lens_back),
    FRONT(R.string.camera_lens_front),
    WIDE(R.string.camera_lens_wide)
}

/** Data class used for focal-length classification. */
data class CameraFocalInfo(
    val cameraId: String,
    val shortestFocalLength: Float
)

/**
 * Helpers for discovering camera lenses and creating appropriate
 * [CameraSelector] instances for CameraX binding.
 */
object CameraLensHelper {

    /** Ratio threshold: a lens is "wide" when its shortest focal length
     *  is less than this fraction of the average across back cameras. */
    const val WIDE_FOCAL_RATIO_THRESHOLD = 0.75f

    /**
     * Classifies back-camera focal-length data and returns the camera ID
     * of the ultra‑wide lens, or null when no wide lens can be identified.
     *
     * This pure function is separated for unit testing.
     */
    fun classifyWideCamera(
        backCameras: List<CameraFocalInfo>,
        threshold: Float = WIDE_FOCAL_RATIO_THRESHOLD
    ): String? {
        if (backCameras.size < 2) return null

        val widest = backCameras.minByOrNull { it.shortestFocalLength } ?: return null
        val avgFocal = backCameras.map { it.shortestFocalLength }.average().toFloat()

        return if (widest.shortestFocalLength < avgFocal * threshold) widest.cameraId else null
    }

    /**
     * Returns the set of [CameraLens] values that are available on this device.
     */
    fun availableLenses(context: Context): Set<CameraLens> {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return emptySet()

        val lenses = mutableSetOf<CameraLens>()

        for (id in cameraManager.cameraIdList) {
            val chars = try {
                cameraManager.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }

            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            when (facing) {
                CameraCharacteristics.LENS_FACING_BACK -> lenses.add(CameraLens.BACK)
                CameraCharacteristics.LENS_FACING_FRONT -> lenses.add(CameraLens.FRONT)
            }
        }

        if (lenses.contains(CameraLens.BACK) && findWideCameraId(cameraManager) != null) {
            lenses.add(CameraLens.WIDE)
        }

        return lenses.toSet()
    }

    /**
     * Finds the camera ID of a back‑facing ultra‑wide lens if one is present.
     * Ultra‑wide is identified by having a meaningfully shorter focal length
     * than the average across all back cameras.
     */
    fun findWideCameraId(cameraManager: CameraManager): String? {
        val backCameras = mutableListOf<CameraFocalInfo>()

        for (id in cameraManager.cameraIdList) {
            val chars = try {
                cameraManager.getCameraCharacteristics(id)
            } catch (_: Exception) {
                continue
            }

            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue
            if (facing != CameraCharacteristics.LENS_FACING_BACK) continue

            val focalLengths = chars.get(
                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
            ) ?: continue
            if (focalLengths.isEmpty()) continue

            backCameras.add(CameraFocalInfo(id, focalLengths.min()))
        }

        return classifyWideCamera(backCameras)
    }

    /**
     * Returns true when the device has both a regular back camera and a
     * distinguishable ultra‑wide camera.
     */
    fun hasWideCamera(context: Context): Boolean {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return false
        return findWideCameraId(cameraManager) != null
    }

    /**
     * Creates a [CameraSelector] targeting the requested [lens].
     */
    fun createCameraSelector(
        lens: CameraLens,
        cameraProvider: ProcessCameraProvider
    ): CameraSelector {
        return when (lens) {
            CameraLens.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
            CameraLens.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
            CameraLens.WIDE -> buildWideSelector(cameraProvider)
        }
    }

    /**
     * Builds a [CameraSelector] that picks the back camera with the
     * shortest focal length (ultra‑wide).
     *
     * Falls back to [CameraSelector.DEFAULT_BACK_CAMERA] when no wide
     * camera can be identified through CameraX.
     */
    private fun buildWideSelector(
        cameraProvider: ProcessCameraProvider
    ): CameraSelector {
        return try {
            val backInfos = cameraProvider.availableCameraInfos.filter { info ->
                try {
                    Camera2CameraInfo.from(info)
                        .getCameraCharacteristic(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK
                } catch (_: Exception) {
                    false
                }
            }

            val wideId = backInfos
                .minByOrNull { info ->
                    try {
                        val c2 = Camera2CameraInfo.from(info)
                        val focalLengths = c2.getCameraCharacteristic(
                            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                        ) ?: floatArrayOf()
                        focalLengths.minOrNull() ?: Float.MAX_VALUE
                    } catch (_: Exception) {
                        Float.MAX_VALUE
                    }
                }
                ?.let { Camera2CameraInfo.from(it).cameraId }

            if (wideId != null) {
                CameraSelector.Builder()
                    .addCameraFilter { infos ->
                        infos.filter { info ->
                            try {
                                Camera2CameraInfo.from(info).cameraId == wideId
                            } catch (_: Exception) {
                                false
                            }
                        }
                    }
                    .build()
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        } catch (_: Exception) {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
}
