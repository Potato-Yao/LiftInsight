package com.potato.liftinsight.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private val linePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.GREEN
    }

    private val pointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val spinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.CYAN
    }

    private val spinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.YELLOW
    }

    private val midSpinePointPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.MAGENTA
    }

    private val textBackgroundPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.argb(160, 0, 0, 0)
    }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
        textSize = 34f
    }

    fun detectAndDrawPose(bitmap: Bitmap, options: DrawingOptions = DrawingOptions()): Bitmap {
        if (!options.drawLandmarks && !options.drawAngles) {
            return bitmap
        }

        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val pose = runCatching {
            Tasks.await(
                poseDetector.process(InputImage.fromBitmap(mutableBitmap, 0))
            )
        }.getOrNull()

        if (pose != null) {
            val positions = extractLandmarkPositions(pose)

            if (options.drawLandmarks) {
                drawPoseLandmarks(
                    canvas = canvas,
                    positions = positions
                )
            }

            if (options.drawAngles) {
                val spinePoints = calculateSpinePoints(positions)
                drawAngleOverlay(
                    canvas = canvas,
                    angles = calculateOverlayAngles(
                        landmarks = positions,
                        spinePoints = spinePoints,
                        frameWidth = canvas.width.toFloat(),
                        frameHeight = canvas.height.toFloat()
                    )
                )
            }
        }

        return mutableBitmap
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

    private fun drawPoseLandmarks(
        canvas: Canvas,
        positions: Map<Int, PoseOverlayLandmark>
    ) {
        val pointRadius = 6f

        positions.forEach { (_, landmark) ->
            canvas.drawCircle(
                landmark.x,
                landmark.y,
                pointRadius,
                pointPaint
            )
        }

        OVERLAY_CONNECTIONS.forEach { (startType, endType) ->
            val start = positions[startType] ?: return@forEach
            val end = positions[endType] ?: return@forEach

            canvas.drawLine(
                start.x,
                start.y,
                end.x,
                end.y,
                linePaint
            )
        }

        val spinePoints = calculateSpinePoints(positions)
        if (spinePoints != null) {
            canvas.drawLine(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                spinePaint
            )
            canvas.drawCircle(
                spinePoints.midShoulder.first,
                spinePoints.midShoulder.second,
                pointRadius + 2f,
                spinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midSpine.first,
                spinePoints.midSpine.second,
                pointRadius + 2f,
                midSpinePointPaint
            )
            canvas.drawCircle(
                spinePoints.midHip.first,
                spinePoints.midHip.second,
                pointRadius + 2f,
                spinePointPaint
            )
        }
    }

    private fun drawAngleOverlay(
        canvas: Canvas,
        angles: PoseOverlayAngles
    ) {
        val lines = angles.toDisplayLines(context)
        if (lines.isEmpty()) {
            return
        }

        val padding = 20f
        val lineSpacing = 12f
        val fontMetrics = textPaint.fontMetrics
        val lineHeight = fontMetrics.bottom - fontMetrics.top
        val backgroundHeight = padding * 2 + lineHeight * lines.size + lineSpacing * (lines.size - 1)

        canvas.drawRect(
            0f,
            0f,
            canvas.width.toFloat(),
            backgroundHeight,
            textBackgroundPaint
        )

        var y = padding - fontMetrics.top
        lines.forEach { line ->
            canvas.drawText(line, padding, y, textPaint)
            y += lineHeight + lineSpacing
        }
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

        private val OVERLAY_CONNECTIONS = listOf(
            PoseLandmark.NOSE to PoseLandmark.LEFT_EYE_INNER,
            PoseLandmark.LEFT_EYE_INNER to PoseLandmark.LEFT_EYE,
            PoseLandmark.LEFT_EYE to PoseLandmark.LEFT_EYE_OUTER,
            PoseLandmark.LEFT_EYE_OUTER to PoseLandmark.LEFT_EAR,
            PoseLandmark.NOSE to PoseLandmark.RIGHT_EYE_INNER,
            PoseLandmark.RIGHT_EYE_INNER to PoseLandmark.RIGHT_EYE,
            PoseLandmark.RIGHT_EYE to PoseLandmark.RIGHT_EYE_OUTER,
            PoseLandmark.RIGHT_EYE_OUTER to PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_MOUTH to PoseLandmark.RIGHT_MOUTH,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_PINKY,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_INDEX,
            PoseLandmark.LEFT_WRIST to PoseLandmark.LEFT_THUMB,
            PoseLandmark.LEFT_PINKY to PoseLandmark.LEFT_INDEX,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_PINKY,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_INDEX,
            PoseLandmark.RIGHT_WRIST to PoseLandmark.RIGHT_THUMB,
            PoseLandmark.RIGHT_PINKY to PoseLandmark.RIGHT_INDEX,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
            PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
            PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
            PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
            PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_FOOT_INDEX,
            PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
            PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX,
            PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_FOOT_INDEX
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
