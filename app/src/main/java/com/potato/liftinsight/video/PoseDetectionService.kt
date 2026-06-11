package com.potato.liftinsight.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.potato.liftinsight.R

internal data class PoseOverlayLandmark(
    val x: Float,
    val y: Float,
    val visibility: Float
)

internal data class PoseOverlaySpinePoints(
    val midShoulder: Pair<Float, Float>,
    val midSpine: Pair<Float, Float>,
    val midHip: Pair<Float, Float>
)

internal data class PoseDetectionResult(
    val bitmap: Bitmap,
    val angles: PoseOverlayAngles,
    val landmarkPositions: Map<Int, PoseOverlayLandmark>
)

internal data class PoseOverlayAngles(
    val spineAngle: Double?,
    val leftLegSpineAngle: Double?,
    val rightLegSpineAngle: Double?,
    val leftKneeAngle: Double?,
    val rightKneeAngle: Double?
) {
    fun toDisplayLines(context: Context): List<String> {
        val lines = mutableListOf<String>()

        spineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_spine_angle, it)
        }
        leftLegSpineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_left_leg_spine_angle, it)
        }
        rightLegSpineAngle?.let {
            lines += context.getString(R.string.training_video_overlay_right_leg_spine_angle, it)
        }
        leftKneeAngle?.let {
            lines += context.getString(R.string.training_video_overlay_left_knee_angle, it)
        }
        rightKneeAngle?.let {
            lines += context.getString(R.string.training_video_overlay_right_knee_angle, it)
        }

        return lines
    }
}

internal class PoseDetectionService(
    private val context: Context
) {
    private val poseDetector: PoseDetector by lazy {
        val options = AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE)
            .build()

        PoseDetection.getClient(options)
    }

    fun detectAndDrawPose(bitmap: Bitmap, options: DrawingOptions = DrawingOptions()): PoseDetectionResult {
        val pose = runCatching {
            Tasks.await(
                poseDetector.process(InputImage.fromBitmap(bitmap, 0))
            )
        }.getOrNull()

        val positions = if (pose != null) extractLandmarkPositions(pose) else emptyMap()
        val spinePoints = calculateSpinePoints(positions)
        val angles = calculateOverlayAngles(
            landmarks = positions,
            spinePoints = spinePoints,
            frameWidth = bitmap.width.toFloat(),
            frameHeight = bitmap.height.toFloat()
        )

        if (!options.drawLandmarks && !options.drawAngles) {
            return PoseDetectionResult(bitmap, angles, positions)
        }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        if (pose != null) {
            if (options.drawLandmarks) {
                PoseOverlayRenderer.drawPoseLandmarks(
                    canvas = canvas,
                    positions = positions
                )
            }

            if (options.drawAngles) {
                PoseOverlayRenderer.drawAngleOverlay(
                    canvas = canvas,
                    textLines = angles.toDisplayLines(context)
                )
            }
        }

        return PoseDetectionResult(mutableBitmap, angles, positions)
    }

    private fun extractLandmarkPositions(pose: Pose): Map<Int, PoseOverlayLandmark> {
        val positions = mutableMapOf<Int, PoseOverlayLandmark>()

        for (type in OVERLAY_LANDMARK_TYPES) {
            val landmark = pose.getPoseLandmark(type) ?: continue

            if (landmark.inFrameLikelihood < MIN_LANDMARK_CONFIDENCE) {
                continue
            }

            positions[type] = PoseOverlayLandmark(
                x = landmark.position.x,
                y = landmark.position.y,
                visibility = landmark.inFrameLikelihood
            )
        }

        return positions
    }

    companion object {
        private const val MIN_LANDMARK_CONFIDENCE = 0.5f

        private val OVERLAY_LANDMARK_TYPES = intArrayOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE_INNER,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.LEFT_EYE_OUTER,
            PoseLandmark.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.RIGHT_EYE_OUTER,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH,
            PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_PINKY,
            PoseLandmark.RIGHT_PINKY,
            PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_THUMB,
            PoseLandmark.RIGHT_THUMB,
            PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_HEEL,
            PoseLandmark.RIGHT_HEEL,
            PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_FOOT_INDEX
        )

    }
}

internal fun calculateSpinePoints(
    landmarks: Map<Int, PoseOverlayLandmark>
): PoseOverlaySpinePoints? {
    val leftShoulder = landmarks[PoseLandmark.LEFT_SHOULDER] ?: return null
    val rightShoulder = landmarks[PoseLandmark.RIGHT_SHOULDER] ?: return null
    val leftHip = landmarks[PoseLandmark.LEFT_HIP] ?: return null
    val rightHip = landmarks[PoseLandmark.RIGHT_HIP] ?: return null

    val minVisibility = minOf(
        leftShoulder.visibility,
        rightShoulder.visibility,
        leftHip.visibility,
        rightHip.visibility
    )
    if (minVisibility < 0.5f) {
        return null
    }

    val midShoulder = Pair(
        (leftShoulder.x + rightShoulder.x) / 2f,
        (leftShoulder.y + rightShoulder.y) / 2f
    )
    val midHip = Pair(
        (leftHip.x + rightHip.x) / 2f,
        (leftHip.y + rightHip.y) / 2f
    )
    val midSpine = Pair(
        (midShoulder.first + midHip.first) / 2f,
        (midShoulder.second + midHip.second) / 2f
    )

    return PoseOverlaySpinePoints(
        midShoulder = midShoulder,
        midSpine = midSpine,
        midHip = midHip
    )
}

internal fun calculateOverlayAngles(
    landmarks: Map<Int, PoseOverlayLandmark>,
    spinePoints: PoseOverlaySpinePoints?,
    frameWidth: Float = 1f,
    frameHeight: Float = 1f
): PoseOverlayAngles {
    val spineAngle = spinePoints?.let {
        Math.toDegrees(
            kotlin.math.atan2(
                ((it.midShoulder.first - it.midHip.first) * frameWidth).toDouble(),
                -(((it.midShoulder.second - it.midHip.second) * frameHeight).toDouble())
            )
        )
    }

    val leftLegSpineAngle = calculateLegSpineAngle(
        knee = landmarks[PoseLandmark.LEFT_KNEE],
        spinePoints = spinePoints,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )
    val rightLegSpineAngle = calculateLegSpineAngle(
        knee = landmarks[PoseLandmark.RIGHT_KNEE],
        spinePoints = spinePoints,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    val leftKneeAngle = calculateJointAngle(
        first = landmarks[PoseLandmark.LEFT_HIP],
        center = landmarks[PoseLandmark.LEFT_KNEE],
        third = landmarks[PoseLandmark.LEFT_ANKLE],
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )
    val rightKneeAngle = calculateJointAngle(
        first = landmarks[PoseLandmark.RIGHT_HIP],
        center = landmarks[PoseLandmark.RIGHT_KNEE],
        third = landmarks[PoseLandmark.RIGHT_ANKLE],
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    return PoseOverlayAngles(
        spineAngle = spineAngle,
        leftLegSpineAngle = leftLegSpineAngle,
        rightLegSpineAngle = rightLegSpineAngle,
        leftKneeAngle = leftKneeAngle,
        rightKneeAngle = rightKneeAngle
    )
}

private fun calculateLegSpineAngle(
    knee: PoseOverlayLandmark?,
    spinePoints: PoseOverlaySpinePoints?,
    frameWidth: Float,
    frameHeight: Float
): Double? {
    if (knee == null || spinePoints == null || knee.visibility < 0.5f) {
        return null
    }

    val spineDx = ((spinePoints.midShoulder.first - spinePoints.midHip.first) * frameWidth).toDouble()
    val spineDy = ((spinePoints.midShoulder.second - spinePoints.midHip.second) * frameHeight).toDouble()
    val legDx = ((knee.x - spinePoints.midHip.first) * frameWidth).toDouble()
    val legDy = ((knee.y - spinePoints.midHip.second) * frameHeight).toDouble()

    return angleBetweenVectors(spineDx, spineDy, legDx, legDy)
}

private fun calculateJointAngle(
    first: PoseOverlayLandmark?,
    center: PoseOverlayLandmark?,
    third: PoseOverlayLandmark?,
    frameWidth: Float,
    frameHeight: Float
): Double? {
    if (first == null || center == null || third == null) {
        return null
    }

    if (first.visibility < 0.5f || center.visibility < 0.5f || third.visibility < 0.5f) {
        return null
    }

    val firstDx = ((first.x - center.x) * frameWidth).toDouble()
    val firstDy = ((first.y - center.y) * frameHeight).toDouble()
    val thirdDx = ((third.x - center.x) * frameWidth).toDouble()
    val thirdDy = ((third.y - center.y) * frameHeight).toDouble()

    return angleBetweenVectors(firstDx, firstDy, thirdDx, thirdDy)
}

private fun angleBetweenVectors(
    firstDx: Double,
    firstDy: Double,
    secondDx: Double,
    secondDy: Double
): Double? {
    val firstLength = kotlin.math.sqrt(firstDx * firstDx + firstDy * firstDy)
    val secondLength = kotlin.math.sqrt(secondDx * secondDx + secondDy * secondDy)
    if (firstLength == 0.0 || secondLength == 0.0) {
        return null
    }

    val cosine = ((firstDx * secondDx) + (firstDy * secondDy)) / (firstLength * secondLength)
    return Math.toDegrees(kotlin.math.acos(cosine.coerceIn(-1.0, 1.0)))
}
